"""
Fine-tune DistilBERT Multilingual for intent classification.

Improvements over basic version:
- Data augmentation (synonym swap, random insertion, word shuffle)
- Train/validation split (80/20, stratified)
- Early stopping with patience
- Warmup + cosine annealing LR scheduler
- Label smoothing for better generalisation
- Gradient accumulation for larger effective batch size
- Per-class accuracy reporting
- Reproducible training with seed
"""

import json
import os
import random
import re
import copy
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from transformers import DistilBertTokenizer
from collections import Counter

from model import IntentClassifier

# ============================================================
# 0. Reproducibility
# ============================================================
SEED = 42
random.seed(SEED)
np.random.seed(SEED)
torch.manual_seed(SEED)
if torch.cuda.is_available():
    torch.cuda.manual_seed_all(SEED)

# ============================================================
# 1. Load intents data
# ============================================================

with open('data/intents.json', 'r', encoding='utf-8') as f:
    intents = json.load(f)

# Collect all patterns and their tags
raw_patterns = []
raw_labels = []
tags = sorted(set(intent['tag'] for intent in intents['intents']))
tag_to_idx = {tag: idx for idx, tag in enumerate(tags)}

for intent in intents['intents']:
    tag = intent['tag']
    for pattern in intent['patterns']:
        raw_patterns.append(pattern)
        raw_labels.append(tag_to_idx[tag])

print(f"📊 Dataset Statistics (before augmentation):")
print(f"   {len(raw_patterns)} training patterns")
print(f"   {len(tags)} intent categories")
print(f"   Tags: {tags}")
print()

# ============================================================
# 2. Data Augmentation
# ============================================================

# Simple synonym dictionaries for augmentation (EN + RO)
SYNONYMS = {
    # English
    "good": ["great", "nice", "excellent", "awesome", "fantastic"],
    "best": ["top", "greatest", "finest", "ultimate"],
    "recommend": ["suggest", "propose", "advise"],
    "want": ["need", "would like", "desire", "wish for"],
    "where": ["what place", "which spot"],
    "eat": ["dine", "grab a bite", "have food"],
    "like": ["enjoy", "love", "prefer", "fancy"],
    "tell": ["show", "give", "share"],
    "help": ["assist", "aid", "support"],
    "beautiful": ["pretty", "gorgeous", "stunning", "lovely"],
    "interesting": ["fascinating", "cool", "exciting", "amazing"],
    "fun": ["entertaining", "enjoyable", "amusing"],
    "cheap": ["affordable", "budget", "inexpensive", "budget-friendly"],
    "expensive": ["pricey", "costly", "premium"],
    "big": ["large", "huge", "massive"],
    "small": ["tiny", "little", "compact"],
    "happy": ["glad", "joyful", "pleased", "cheerful"],
    "sad": ["unhappy", "down", "miserable", "gloomy"],
    "food": ["cuisine", "meal", "dish", "grub"],
    "place": ["spot", "location", "venue"],
    "restaurant": ["eatery", "dining place", "bistro"],
    "park": ["garden", "green space", "outdoor area"],
    "museum": ["gallery", "exhibition", "cultural center"],
    "hotel": ["accommodation", "lodging", "stay"],
    "night": ["evening", "nighttime", "after dark"],
    "morning": ["dawn", "sunrise", "AM"],
    "walk": ["stroll", "wander", "hike"],
    "visit": ["explore", "check out", "go to", "see"],
    "coffee": ["espresso", "latte", "brew", "cup of joe"],
    "bar": ["pub", "lounge", "tavern"],
    # Romanian
    "bun": ["fain", "super", "grozav", "excelent"],
    "frumos": ["superb", "minunat", "deosebit"],
    "mancare": ["bucate", "fel de mancare"],
    "locuri": ["zone", "locatii"],
    "mai": ["cel mai"],
    "vreau": ["as vrea", "doresc", "imi doresc"],
    "unde": ["in ce loc", "ce zona"],
}

FILLER_WORDS_EN = ["please", "actually", "maybe", "possibly", "really", "just", "I think"]
FILLER_WORDS_RO = ["te rog", "oare", "cumva", "cam", "poate", "chiar"]


def augment_synonym_swap(text, prob=0.3):
    """Replace random words with synonyms."""
    words = text.split()
    new_words = []
    for word in words:
        w_lower = word.lower()
        if w_lower in SYNONYMS and random.random() < prob:
            synonym = random.choice(SYNONYMS[w_lower])
            # Try to preserve original casing
            if word[0].isupper():
                synonym = synonym.capitalize()
            new_words.append(synonym)
        else:
            new_words.append(word)
    result = " ".join(new_words)
    return result if result != text else None  # Only return if actually changed


def augment_random_insertion(text, prob=0.15):
    """Insert filler words at random positions."""
    words = text.split()
    if len(words) < 2:
        return None
    # Detect language heuristic
    ro_chars = set("ăâîșț")
    is_romanian = any(c in ro_chars for c in text.lower()) or any(
        w in text.lower() for w in ["vreau", "unde", "bun", "sunt", "imi", "ce"]
    )
    fillers = FILLER_WORDS_RO if is_romanian else FILLER_WORDS_EN
    if random.random() < prob:
        insert_pos = random.randint(0, len(words))
        filler = random.choice(fillers)
        words.insert(insert_pos, filler)
        return " ".join(words)
    return None


def augment_word_shuffle(text, prob=0.2):
    """Lightly shuffle word order while keeping it somewhat coherent."""
    words = text.split()
    if len(words) < 3 or random.random() > prob:
        return None
    # Swap two adjacent words
    idx = random.randint(0, len(words) - 2)
    words[idx], words[idx + 1] = words[idx + 1], words[idx]
    result = " ".join(words)
    return result if result != text else None


def augment_dataset(patterns, labels, num_augmented_per_sample=2):
    """
    Apply augmentation to increase dataset size.
    Focuses on classes with fewer samples (balancing).
    """
    aug_patterns = list(patterns)
    aug_labels = list(labels)

    # Count samples per class
    class_counts = Counter(labels)
    max_count = max(class_counts.values())

    augmentors = [augment_synonym_swap, augment_random_insertion, augment_word_shuffle]

    for pattern, label in zip(patterns, labels):
        # Generate more augmented samples for underrepresented classes
        count = class_counts[label]
        num_aug = num_augmented_per_sample
        if count < max_count * 0.5:
            num_aug += 2  # Extra augmentation for minority classes
        elif count < max_count * 0.75:
            num_aug += 1

        generated = 0
        attempts = 0
        while generated < num_aug and attempts < num_aug * 3:
            attempts += 1
            augmentor = random.choice(augmentors)
            augmented = augmentor(pattern)
            if augmented and augmented not in aug_patterns:
                aug_patterns.append(augmented)
                aug_labels.append(label)
                generated += 1

    return aug_patterns, aug_labels


print("🔄 Augmenting dataset...")
patterns, labels = augment_dataset(raw_patterns, raw_labels, num_augmented_per_sample=2)
print(f"   {len(raw_patterns)} → {len(patterns)} patterns after augmentation")
print(f"   Augmentation ratio: {len(patterns)/len(raw_patterns):.1f}x")
print()

# ============================================================
# 3. Train/Validation Split (stratified)
# ============================================================

def stratified_split(patterns, labels, val_ratio=0.2):
    """Split data maintaining class distribution in both sets."""
    # Group indices by label
    label_indices = {}
    for idx, label in enumerate(labels):
        if label not in label_indices:
            label_indices[label] = []
        label_indices[label].append(idx)

    train_indices = []
    val_indices = []

    for label, indices in label_indices.items():
        random.shuffle(indices)
        n_val = max(1, int(len(indices) * val_ratio))
        # Ensure at least 1 sample in training
        if len(indices) - n_val < 1:
            n_val = max(0, len(indices) - 1)
        val_indices.extend(indices[:n_val])
        train_indices.extend(indices[n_val:])

    return train_indices, val_indices


train_idx, val_idx = stratified_split(patterns, labels, val_ratio=0.2)

train_patterns = [patterns[i] for i in train_idx]
train_labels = [labels[i] for i in train_idx]
val_patterns = [patterns[i] for i in val_idx]
val_labels = [labels[i] for i in val_idx]

print(f"📂 Split:")
print(f"   Train: {len(train_patterns)} samples")
print(f"   Val:   {len(val_patterns)} samples")
print()

# ============================================================
# 4. Tokenize with DistilBERT tokenizer
# ============================================================

MODEL_NAME = "distilbert-base-multilingual-cased"
tokenizer = DistilBertTokenizer.from_pretrained(MODEL_NAME)

MAX_LENGTH = 64  # Max tokens per sentence

print(f"🔤 Tokenizing with DistilBERT tokenizer...")
train_encodings = tokenizer(
    train_patterns, padding=True, truncation=True,
    max_length=MAX_LENGTH, return_tensors='pt'
)
val_encodings = tokenizer(
    val_patterns, padding=True, truncation=True,
    max_length=MAX_LENGTH, return_tensors='pt'
)
print(f"   Train tokens shape: {train_encodings['input_ids'].shape}")
print(f"   Val tokens shape:   {val_encodings['input_ids'].shape}")
print()

# ============================================================
# 5. Dataset
# ============================================================

class IntentDataset(Dataset):
    def __init__(self, encodings, labels):
        self.encodings = encodings
        self.labels = torch.tensor(labels, dtype=torch.long)

    def __getitem__(self, idx):
        return {
            'input_ids': self.encodings['input_ids'][idx],
            'attention_mask': self.encodings['attention_mask'][idx],
            'labels': self.labels[idx]
        }

    def __len__(self):
        return len(self.labels)

BATCH_SIZE = 16
ACCUMULATION_STEPS = 2  # Effective batch size = 16 * 2 = 32

train_dataset = IntentDataset(train_encodings, train_labels)
val_dataset = IntentDataset(val_encodings, val_labels)
train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True)
val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False)

# ============================================================
# 6. Model setup
# ============================================================

device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f"🖥️  Training on: {device}")

num_classes = len(tags)
model = IntentClassifier(num_classes=num_classes, dropout_rate=0.3).to(device)

# Count parameters
total_params = sum(p.numel() for p in model.parameters())
trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
print(f"   Total parameters: {total_params:,}")
print(f"   Trainable: {trainable_params:,}")
print()

# Different learning rates for pre-trained BERT vs new classifier head
optimizer = torch.optim.AdamW([
    {'params': model.bert.parameters(), 'lr': 2e-5},        # Small LR for pre-trained
    {'params': model.classifier.parameters(), 'lr': 1e-3}   # Larger LR for classifier
], weight_decay=0.01)

# Label smoothing loss
criterion = nn.CrossEntropyLoss(label_smoothing=0.1)

# Warmup + Cosine Annealing scheduler
NUM_EPOCHS = 50
WARMUP_EPOCHS = 3


class WarmupCosineScheduler:
    """Linear warmup followed by cosine annealing."""
    def __init__(self, optimizer, warmup_epochs, total_epochs, min_lr_factor=0.01):
        self.optimizer = optimizer
        self.warmup_epochs = warmup_epochs
        self.total_epochs = total_epochs
        self.min_lr_factor = min_lr_factor
        self.base_lrs = [pg['lr'] for pg in optimizer.param_groups]

    def step(self, epoch):
        if epoch < self.warmup_epochs:
            # Linear warmup
            factor = (epoch + 1) / self.warmup_epochs
        else:
            # Cosine annealing
            progress = (epoch - self.warmup_epochs) / max(1, self.total_epochs - self.warmup_epochs)
            factor = self.min_lr_factor + 0.5 * (1 - self.min_lr_factor) * (
                1 + np.cos(np.pi * progress)
            )
        for pg, base_lr in zip(self.optimizer.param_groups, self.base_lrs):
            pg['lr'] = base_lr * factor


scheduler = WarmupCosineScheduler(optimizer, WARMUP_EPOCHS, NUM_EPOCHS)

# ============================================================
# 7. Training with Early Stopping
# ============================================================

PATIENCE = 7  # Stop if no val improvement for this many epochs

print(f"\n🚀 Fine-tuning DistilBERT for up to {NUM_EPOCHS} epochs (patience={PATIENCE})...\n")
print(f"{'Epoch':>6} | {'Train Loss':>10} | {'Train Acc':>9} | {'Val Loss':>8} | {'Val Acc':>7} | {'LR(bert)':>10} | {'LR(cls)':>10} | {'Status':>8}")
print("-" * 95)

best_val_loss = float('inf')
best_val_acc = 0.0
best_model_state = None
epochs_without_improvement = 0
training_history = []


def evaluate(model, data_loader, criterion, device):
    """Evaluate model on a dataset."""
    model.eval()
    total_loss = 0
    correct = 0
    total = 0
    all_preds = []
    all_labels = []

    with torch.no_grad():
        for batch in data_loader:
            input_ids = batch['input_ids'].to(device)
            attention_mask = batch['attention_mask'].to(device)
            batch_labels = batch['labels'].to(device)

            logits = model(input_ids, attention_mask)
            loss = criterion(logits, batch_labels)

            total_loss += loss.item()
            _, preds = torch.max(logits, dim=1)
            correct += (preds == batch_labels).sum().item()
            total += batch_labels.size(0)

            all_preds.extend(preds.cpu().numpy())
            all_labels.extend(batch_labels.cpu().numpy())

    avg_loss = total_loss / len(data_loader)
    accuracy = 100 * correct / total
    return avg_loss, accuracy, all_preds, all_labels


for epoch in range(NUM_EPOCHS):
    # --- Training ---
    model.train()
    total_loss = 0
    correct = 0
    total = 0
    optimizer.zero_grad()

    for batch_idx, batch in enumerate(train_loader):
        input_ids = batch['input_ids'].to(device)
        attention_mask = batch['attention_mask'].to(device)
        batch_labels = batch['labels'].to(device)

        # Forward
        logits = model(input_ids, attention_mask)
        loss = criterion(logits, batch_labels)
        loss = loss / ACCUMULATION_STEPS  # Gradient accumulation

        # Backward
        loss.backward()

        if (batch_idx + 1) % ACCUMULATION_STEPS == 0 or (batch_idx + 1) == len(train_loader):
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            optimizer.zero_grad()

        total_loss += loss.item() * ACCUMULATION_STEPS
        _, preds = torch.max(logits, dim=1)
        correct += (preds == batch_labels).sum().item()
        total += batch_labels.size(0)

    train_loss = total_loss / len(train_loader)
    train_acc = 100 * correct / total

    # --- Validation ---
    val_loss, val_acc, _, _ = evaluate(model, val_loader, criterion, device)

    # --- LR Scheduler ---
    scheduler.step(epoch)

    # --- Early Stopping Check ---
    status = ""
    if val_loss < best_val_loss:
        best_val_loss = val_loss
        best_val_acc = val_acc
        best_model_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}
        epochs_without_improvement = 0
        status = "✅ BEST"
    else:
        epochs_without_improvement += 1
        if epochs_without_improvement >= PATIENCE:
            status = "🛑 STOP"
        else:
            status = f"⏳ {epochs_without_improvement}/{PATIENCE}"

    lr_bert = optimizer.param_groups[0]['lr']
    lr_cls = optimizer.param_groups[1]['lr']

    training_history.append({
        'epoch': epoch + 1, 'train_loss': train_loss, 'train_acc': train_acc,
        'val_loss': val_loss, 'val_acc': val_acc
    })

    print(f"{epoch+1:>6} | {train_loss:>10.4f} | {train_acc:>8.1f}% | {val_loss:>8.4f} | {val_acc:>6.1f}% | {lr_bert:>10.2e} | {lr_cls:>10.2e} | {status}")

    if epochs_without_improvement >= PATIENCE:
        print(f"\n⏹️  Early stopping at epoch {epoch+1} (no improvement for {PATIENCE} epochs)")
        break

# ============================================================
# 8. Final evaluation
# ============================================================

model.load_state_dict(best_model_state)
model.to(device)

# Evaluate on full train set
train_full_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=False)
train_loss, train_acc, train_preds, train_true = evaluate(model, train_full_loader, criterion, device)
val_loss, val_acc, val_preds, val_true = evaluate(model, val_loader, criterion, device)

print(f"\n{'='*60}")
print(f"✅ Final Results (best model):")
print(f"{'='*60}")
print(f"   Train accuracy: {train_acc:.1f}%")
print(f"   Val accuracy:   {val_acc:.1f}%")
print(f"   Best val loss:  {best_val_loss:.4f}")
print()

# Per-class accuracy on validation set
print(f"📊 Per-class accuracy (validation):")
print(f"{'Tag':<25} | {'Correct':>7} | {'Total':>5} | {'Accuracy':>8}")
print("-" * 55)

class_correct = {}
class_total = {}
for pred, true in zip(val_preds, val_true):
    tag_name = tags[true]
    if tag_name not in class_total:
        class_total[tag_name] = 0
        class_correct[tag_name] = 0
    class_total[tag_name] += 1
    if pred == true:
        class_correct[tag_name] += 1

weak_classes = []
for tag_name in sorted(class_total.keys()):
    acc = 100 * class_correct[tag_name] / class_total[tag_name]
    marker = " ⚠️" if acc < 80 else ""
    print(f"   {tag_name:<25} | {class_correct[tag_name]:>7} | {class_total[tag_name]:>5} | {acc:>6.1f}%{marker}")
    if acc < 80:
        weak_classes.append((tag_name, acc))

if weak_classes:
    print(f"\n⚠️  Classes with <80% accuracy (consider adding more training patterns):")
    for tag_name, acc in weak_classes:
        print(f"   - {tag_name}: {acc:.1f}%")
print()

# ============================================================
# 9. Save model, tokenizer info, and tag mapping
# ============================================================

SAVE_DIR = "distilbert_model"
os.makedirs(SAVE_DIR, exist_ok=True)

# Save the fine-tuned model weights
torch.save({
    'model_state': best_model_state,
    'num_classes': num_classes,
    'tags': tags,
    'tag_to_idx': tag_to_idx,
    'max_length': MAX_LENGTH,
    'model_name': MODEL_NAME,
    'dropout_rate': 0.3,
    'training_history': training_history,
    'best_val_acc': best_val_acc,
    'best_val_loss': best_val_loss,
}, os.path.join(SAVE_DIR, 'model_data.pth'))

# Also save tokenizer locally for faster loading
tokenizer.save_pretrained(SAVE_DIR)

print(f"💾 Model saved to: {SAVE_DIR}/")
print(f"   - model_data.pth ({os.path.getsize(os.path.join(SAVE_DIR, 'model_data.pth')) / 1024 / 1024:.1f} MB)")
print(f"\n🎉 Fine-tuning complete! Your DistilBERT chatbot is ready!")
print(f"   Best validation accuracy: {best_val_acc:.1f}%")
