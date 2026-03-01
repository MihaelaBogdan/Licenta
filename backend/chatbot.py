"""
Chatbot inference using fine-tuned DistilBERT.

Loads the fine-tuned model and provides responses
based on intent classification with confidence scoring.
Supports conversational flow with quick reply suggestions.
"""

import os
import random
import json
import torch
from transformers import DistilBertTokenizer
from model import IntentClassifier

# ============================================================
# Load model and data
# ============================================================

SAVE_DIR = "distilbert_model"
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

# Load intents
with open('data/intents.json', 'r', encoding='utf-8') as f:
    intents = json.load(f)

# Build a quick lookup: tag -> intent data
intent_lookup = {}
for intent in intents['intents']:
    intent_lookup[intent['tag']] = intent

# Load model data
model_data = torch.load(
    os.path.join(SAVE_DIR, 'model_data.pth'),
    map_location=device
)

tags = model_data['tags']
tag_to_idx = model_data['tag_to_idx']
num_classes = model_data['num_classes']
MAX_LENGTH = model_data['max_length']
dropout_rate = model_data.get('dropout_rate', 0.3)

# Load tokenizer (saved locally during training)
tokenizer = DistilBertTokenizer.from_pretrained(SAVE_DIR)

# Load model
model = IntentClassifier(num_classes=num_classes, dropout_rate=dropout_rate).to(device)
model.load_state_dict(model_data['model_state'])
model.eval()

bot_name = "MysticMinds"

# ============================================================
# Fallback responses
# ============================================================

FALLBACK_RESPONSES = [
    "Hmm, I'm not sure about that. Could you rephrase? I know a lot about Bucharest! 🤔",
    "I don't quite understand. Try asking about restaurants, parks, museums, or just chat with me!",
    "Nu am inteles exact. Incearca sa intrebi despre locuri, mancare, activitati, sau orice altceva! 😊",
    "Sorry, I couldn't get that! I can help with Bucharest recommendations, travel tips, and general chat!",
    "I'm still learning! Try asking: 'Where should I eat?', 'Best park?', or 'Tell me a joke!' 🌟",
    "Hmm, that's a tough one! Try rephrasing, or ask me about something specific — I know a lot about Bucharest! 🗺️",
    "Nu sunt sigur ce vrei sa spui. Pot sa te ajut cu restaurante, parcuri, muzee, sau orice despre Bucuresti!",
    "Oops! I didn't catch that. I'm great with Bucharest tips, travel advice, food recommendations, and casual chat! 💬"
]

FALLBACK_SUGGESTIONS = ["🍽️ Să mănânc ceva", "🌃 Să ies în oraș", "🏛️ Muzee", "🌳 Parcuri", "💬 Spune-mi o glumă"]

# Confidence thresholds
CONFIDENCE_THRESHOLD = 0.40


def _predict_intent(msg):
    """
    Internal: Run the DistilBERT model on a message.
    Returns (top_tag, top_probability, all_probs).
    """
    encoding = tokenizer(
        msg,
        padding='max_length',
        truncation=True,
        max_length=MAX_LENGTH,
        return_tensors='pt'
    )
    
    input_ids = encoding['input_ids'].to(device)
    attention_mask = encoding['attention_mask'].to(device)
    
    with torch.no_grad():
        logits = model(input_ids, attention_mask)
    
    probs = torch.softmax(logits, dim=1)
    top_prob, top_idx = torch.max(probs, dim=1)
    
    top_tag = tags[top_idx.item()]
    top_probability = top_prob.item()
    
    return top_tag, top_probability, probs


def get_response(msg):
    """
    Process user message and return an appropriate response.
    Uses DistilBERT for intent classification.
    """
    if not msg or not msg.strip():
        return "Please type something! I'm here to help! 😊"
    
    top_tag, confidence, _ = _predict_intent(msg)
    
    if confidence > CONFIDENCE_THRESHOLD:
        if top_tag in intent_lookup:
            return random.choice(intent_lookup[top_tag]['responses'])
    
    return random.choice(FALLBACK_RESPONSES)


def get_response_with_details(msg):
    """
    Extended version that returns response with intent, confidence,
    and optional suggestions for conversational flow.
    """
    if not msg or not msg.strip():
        return {
            "answer": "Please type something! I'm here to help! 😊",
            "intent": "empty",
            "confidence": 0.0,
            "suggestions": FALLBACK_SUGGESTIONS
        }
    
    top_tag, confidence, _ = _predict_intent(msg)
    
    if confidence > CONFIDENCE_THRESHOLD:
        if top_tag in intent_lookup:
            intent_data = intent_lookup[top_tag]
            response = random.choice(intent_data['responses'])
            suggestions = intent_data.get('suggestions', [])
            
            return {
                "answer": response,
                "intent": top_tag,
                "confidence": round(confidence, 4),
                "suggestions": suggestions
            }
    
    return {
        "answer": random.choice(FALLBACK_RESPONSES),
        "intent": "fallback",
        "confidence": round(confidence, 4),
        "suggestions": FALLBACK_SUGGESTIONS
    }
