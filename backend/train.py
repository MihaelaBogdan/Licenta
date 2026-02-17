"""
Fine-tune DistilBERT Multilingual for intent classification.

This script:
1. Loads intent patterns from intents.json
2. Tokenizes them using DistilBERT's tokenizer
3. Fine-tunes the pre-trained DistilBERT model on our data
4. Saves the fine-tuned model for inference
"""

import json
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from transformers import DistilBertTokenizer

from model import IntentClassifier

# ============================================================
# 1. Load intents data
# ============================================================

with open('data/intents.json', 'r', encoding='utf-8') as f:
    intents = json.load(f)

# Collect all patterns and their corresponding tags
patterns = []
labels = []
tags = []

for intent in intents['intents']:
    tag = intent['tag']
    if tag not in tags:
        tags.append(tag)
    for pattern in intent['patterns']:
        patterns.append(pattern)
        labels.append(tags.index(tag))

tags = sorted(set(tags))
# Rebuild labels with sorted tags
tag_to_idx = {tag: idx for idx, tag in enumerate(tags)}
labels = []
for intent in intents['intents']:
    tag = intent['tag']
    for pattern in intent['patterns']:
        labels.append(tag_to_idx[tag])

# Rebuild patterns list to match
patterns = []
for intent in intents['intents']:
    for pattern in intent['patterns']:
        patterns.append(pattern)

print(f"📊 Dataset Statistics:")
print(f"   {len(patterns)} training patterns")
print(f"   {len(tags)} intent categories")
print(f"   Tags: {tags}")
print()

# ============================================================
# 2. Tokenize with DistilBERT tokenizer
# ============================================================

MODEL_NAME = "distilbert-base-multilingual-cased"
tokenizer = DistilBertTokenizer.from_pretrained(MODEL_NAME)

MAX_LENGTH = 64  # Max tokens per sentence (our patterns are short)

print(f"🔤 Tokenizing {len(patterns)} patterns with DistilBERT tokenizer...")
encodings = tokenizer(
    patterns,
    padding=True,
    truncation=True,
    max_length=MAX_LENGTH,
    return_tensors='pt'
)
print(f"   Token shape: {encodings['input_ids'].shape}")
print()

# ============================================================
# 3. Dataset
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

dataset = IntentDataset(encodings, labels)
train_loader = DataLoader(dataset, batch_size=16, shuffle=True)

# ============================================================
# 4. Model setup
# ============================================================

device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f"🖥️  Training on: {device}")

num_classes = len(tags)
model = IntentClassifier(num_classes=num_classes, dropout_rate=0.3).to(device)

# Use different learning rates for BERT (small) and classifier (larger)
optimizer = torch.optim.AdamW([
    {'params': model.bert.parameters(), 'lr': 2e-5},       # Small LR for pre-trained weights
    {'params': model.classifier.parameters(), 'lr': 1e-3}  # Larger LR for new classification head
], weight_decay=0.01)

criterion = nn.CrossEntropyLoss()

# Learning rate scheduler
num_epochs = 30  # Transformers converge much faster than simple NNs
scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=num_epochs)

# ============================================================
# 5. Training
# ============================================================

print(f"\n🚀 Fine-tuning DistilBERT for {num_epochs} epochs...\n")

best_loss = float('inf')
best_acc = 0.0
best_model_state = None

for epoch in range(num_epochs):
    model.train()
    total_loss = 0
    correct = 0
    total = 0
    
    for batch in train_loader:
        input_ids = batch['input_ids'].to(device)
        attention_mask = batch['attention_mask'].to(device)
        batch_labels = batch['labels'].to(device)
        
        # Forward
        logits = model(input_ids, attention_mask)
        loss = criterion(logits, batch_labels)
        
        # Backward
        optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)  # Gradient clipping
        optimizer.step()
        
        total_loss += loss.item()
        _, preds = torch.max(logits, dim=1)
        correct += (preds == batch_labels).sum().item()
        total += batch_labels.size(0)
    
    scheduler.step()
    
    avg_loss = total_loss / len(train_loader)
    accuracy = 100 * correct / total
    
    if avg_loss < best_loss:
        best_loss = avg_loss
        best_acc = accuracy
        best_model_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}
    
    if (epoch + 1) % 5 == 0 or epoch == 0:
        lr_bert = optimizer.param_groups[0]['lr']
        lr_cls = optimizer.param_groups[1]['lr']
        print(f"   Epoch [{epoch+1:3d}/{num_epochs}] | Loss: {avg_loss:.4f} | Acc: {accuracy:.1f}% | LR(bert): {lr_bert:.2e} | LR(cls): {lr_cls:.2e}")

# ============================================================
# 6. Final evaluation
# ============================================================

model.load_state_dict(best_model_state)
model.eval()

correct = 0
total = 0
with torch.no_grad():
    for batch in train_loader:
        input_ids = batch['input_ids'].to(device)
        attention_mask = batch['attention_mask'].to(device)
        batch_labels = batch['labels'].to(device)
        
        logits = model(input_ids, attention_mask)
        _, preds = torch.max(logits, dim=1)
        correct += (preds == batch_labels).sum().item()
        total += batch_labels.size(0)

final_accuracy = 100 * correct / total
print(f"\n✅ Training Results:")
print(f"   Best loss: {best_loss:.4f}")
print(f"   Final accuracy: {final_accuracy:.1f}%")

# ============================================================
# 7. Save model, tokenizer info, and tag mapping
# ============================================================

SAVE_DIR = "distilbert_model"

import os
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
}, os.path.join(SAVE_DIR, 'model_data.pth'))

# Also save tokenizer locally for faster loading
tokenizer.save_pretrained(SAVE_DIR)

print(f"   Model saved to: {SAVE_DIR}/")
print(f"\n🎉 Fine-tuning complete! Your DistilBERT chatbot is ready!")
