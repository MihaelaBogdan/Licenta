"""
Chatbot inference using fine-tuned DistilBERT.

Loads the fine-tuned model and provides responses
based on intent classification with confidence scoring.
Supports conversational flow with quick reply suggestions.
Supports bilingual responses (Romanian and English).
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
# Fallback responses (bilingual)
# ============================================================

FALLBACK_RESPONSES_RO = [
    "Hmm, nu sunt sigur de asta. Poți reformula? Știu multe despre București! 🤔",
    "Nu am înțeles exact. Încearcă să întrebi despre restaurante, parcuri, muzee, sau pur și simplu discută cu mine!",
    "Îmi pare rău, nu am prins asta! Pot să te ajut cu recomandări de locuri în București, sfaturi de călătorie și discuții generale! 😊",
    "Hmm, asta e una dificilă! Încearcă să reformulezi, sau întreabă-mă despre ceva specific — știu multe despre București! 🗺️",
    "Nu sunt sigur ce vrei să spui. Pot să te ajut cu restaurante, parcuri, muzee, sau orice despre București!",
    "Ups! Nu am prins asta. Sunt grozav cu sfaturi despre București, recomandări de mâncare și discuții generale! 💬",
    "Încă învăț! Încearcă să întrebi: 'Unde ar trebui să mănânc?', 'Cel mai bun parc?', sau 'Spune-mi o glumă!' 🌟"
]

FALLBACK_RESPONSES_EN = [
    "Hmm, I'm not sure about that. Could you rephrase? I know a lot about Bucharest! 🤔",
    "I don't quite understand. Try asking about restaurants, parks, museums, or just chat with me!",
    "Sorry, I couldn't get that! I can help with Bucharest recommendations, travel tips, and general chat! 😊",
    "Hmm, that's a tough one! Try rephrasing, or ask me about something specific — I know a lot about Bucharest! 🗺️",
    "I'm not sure what you mean. I can help with restaurants, parks, museums, or anything about Bucharest!",
    "Oops! I didn't catch that. I'm great with Bucharest tips, travel advice, food recommendations, and casual chat! 💬",
    "I'm still learning! Try asking: 'Where should I eat?', 'Best park?', or 'Tell me a joke!' 🌟"
]

FALLBACK_SUGGESTIONS_RO = ["🍽️ Să mănânc ceva", "🌃 Să ies în oraș", "🏛️ Muzee", "🌳 Parcuri", "💬 Spune-mi o glumă"]
FALLBACK_SUGGESTIONS_EN = ["🍽️ Eat something", "🌃 Go out", "🏛️ Museums", "🌳 Parks", "💬 Tell me a joke"]

# Confidence thresholds
CONFIDENCE_THRESHOLD = 0.40


def _get_fallback_responses(language="ro"):
    """Get fallback responses based on language."""
    if language == "en":
        return FALLBACK_RESPONSES_EN
    return FALLBACK_RESPONSES_RO


def _get_fallback_suggestions(language="ro"):
    """Get fallback suggestions based on language."""
    if language == "en":
        return FALLBACK_SUGGESTIONS_EN
    return FALLBACK_SUGGESTIONS_RO


def _get_empty_message(language="ro"):
    """Get empty message based on language."""
    if language == "en":
        return "Please type something! I'm here to help! 😊"
    return "Te rog scrie ceva! Sunt aici să te ajut! 😊"


def _get_response_for_intent(intent_data, language="ro"):
    """
    Get a response from intent data, preferring the correct language.
    If the intent has 'responses_en' and 'responses_ro' fields, use them.
    Otherwise, fall back to the default 'responses' field.
    """
    if language == "en" and "responses_en" in intent_data:
        return random.choice(intent_data["responses_en"])
    elif language == "ro" and "responses_ro" in intent_data:
        return random.choice(intent_data["responses_ro"])
    
    # Fall back to default responses
    return random.choice(intent_data['responses'])


def _get_suggestions_for_intent(intent_data, language="ro"):
    """
    Get suggestions from intent data, preferring the correct language.
    """
    if language == "en" and "suggestions_en" in intent_data:
        return intent_data["suggestions_en"]
    elif language == "ro" and "suggestions_ro" in intent_data:
        return intent_data["suggestions_ro"]
    
    return intent_data.get('suggestions', [])


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


def get_response(msg, language="ro"):
    """
    Process user message and return an appropriate response.
    Uses DistilBERT for intent classification.
    Supports bilingual responses (ro/en).
    """
    if not msg or not msg.strip():
        return _get_empty_message(language)
    
    top_tag, confidence, _ = _predict_intent(msg)
    
    if confidence > CONFIDENCE_THRESHOLD:
        if top_tag in intent_lookup:
            return _get_response_for_intent(intent_lookup[top_tag], language)
    
    return random.choice(_get_fallback_responses(language))


def get_response_with_details(msg, language="ro"):
    """
    Extended version that returns response with intent, confidence,
    and optional suggestions for conversational flow.
    Supports bilingual responses (ro/en).
    """
    if not msg or not msg.strip():
        return {
            "answer": _get_empty_message(language),
            "intent": "empty",
            "confidence": 0.0,
            "suggestions": _get_fallback_suggestions(language)
        }
    
    top_tag, confidence, _ = _predict_intent(msg)
    
    if confidence > CONFIDENCE_THRESHOLD:
        if top_tag in intent_lookup:
            intent_data = intent_lookup[top_tag]
            response = _get_response_for_intent(intent_data, language)
            suggestions = _get_suggestions_for_intent(intent_data, language)
            
            return {
                "answer": response,
                "intent": top_tag,
                "confidence": round(confidence, 4),
                "suggestions": suggestions
            }
    
    return {
        "answer": random.choice(_get_fallback_responses(language)),
        "intent": "fallback",
        "confidence": round(confidence, 4),
        "suggestions": _get_fallback_suggestions(language)
    }
