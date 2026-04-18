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
from transformers import DistilBertTokenizerFast
from model import IntentClassifier

# ============================================================
# Load model and data
# ============================================================

BASE_PATH = os.path.dirname(os.path.abspath(__file__))
SAVE_DIR = os.path.join(BASE_PATH, "distilbert_model")
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

import google.generativeai as genai
import os

# Initialize Gemini
genai.configure(api_key=os.getenv("GOOGLE_API_KEY"))
gemini_model = genai.GenerativeModel("gemini-2.0-flash") # Reset to 2.0 with the new key

# Load intents
intents_path = os.path.join(BASE_PATH, 'data', 'intents.json')
with open(intents_path, 'r', encoding='utf-8') as f:
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
tokenizer = DistilBertTokenizerFast.from_pretrained(SAVE_DIR)

# Load model
model = None
try:
    model = IntentClassifier(num_classes=num_classes, dropout_rate=dropout_rate).to(device)
    model.load_state_dict(model_data['model_state'], strict=False)
    model.eval()
    print("✅ Chatbot model loaded successfully!")
except Exception as e:
    print(f"⚠️ Warning: Chatbot model failed to load: {e}")
    print("Fallback to basic response logic will be used.")

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


def _get_gemini_response(msg, language="ro"):
    """
    Call Gemini for high-quality, contextual responses when local intent 
    confidence is low.
    """
    system_prompt = (
        "Ești 'MysticMinds', asistentul virtual inteligent al aplicației CityScape. "
        "Locația ta actuală este București, România. "
        f"Răspunde utilizatorului în limba: {'română' if language == 'ro' else 'engleză'}. "
        "Fii prietenos, creativ și scurt. Oferă sfaturi de călătorie, recomandări de locuri și discuții relaxate. "
        "La finalul răspunsului, include maxim 3 sugestii de replici scurte pe care utilizatorul le-ar putea da, sub forma: [SUGGESTIONS: Sugestia 1 | Sugestia 2 | Sugestia 3]"
    )
    
    try:
        chat = gemini_model.start_chat()
        full_msg = f"{system_prompt}\n\nMesaj utilizator: {msg}"
        response = chat.send_message(full_msg)
        text = response.text
        
        # Parse suggestions
        suggestions = []
        if "[SUGGESTIONS:" in text:
            parts = text.split("[SUGGESTIONS:")
            text = parts[0].strip()
            raw_sug = parts[1].replace("]", "").strip()
            suggestions = [s.strip() for s in raw_sug.split("|")][:3]
        else:
            suggestions = _get_fallback_suggestions(language)[:3]
            
        return text, suggestions
        
    except Exception as e:
        print(f"⚠️ Gemini Error: {e}")
        return random.choice(_get_fallback_responses(language)), _get_fallback_suggestions(language)


def _predict_intent(msg):
    """
    Internal: Run the DistilBERT model on a message.
    Returns (top_tag, top_probability, all_probs).
    """
    msg = msg.lower()
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


def get_response_with_rag(msg, user_id=None, lat=None, lng=None, language="ro"):
    """
    PREMIUM RAG LOGIC:
    1. Retrieve User Profile (Interests/History) from Supabase.
    2. Retrieve Nearby Places from Google.
    3. Feed context + question to Gemini.
    """
    if not msg or not msg.strip():
        return {"answer": _get_empty_message(language), "intent": "empty", "suggestions": _get_fallback_suggestions(language)}

    # Context collection
    user_context = "Nu avem date despre preferințele utilizatorului."
    nearby_context = "Nu avem date despre locațiile din apropiere."
    
    # 1. Fetch Supabase Profile
    if user_id:
        try:
            from app import SUPABASE_URL, SUPABASE_KEY
            import requests
            headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
            profile_url = f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{user_id}&select=*"
            p_res = requests.get(profile_url, headers=headers).json()
            if p_res:
                p = p_res[0]
                user_context = f"Utilizatorul se numește {p.get('full_name', 'Călător')}. Interese: {p.get('interests', 'generale')}. Buget: {p.get('budget_range', 'mediu')}."
        except Exception as e:
            print(f"⚠️ RAG Profile Error: {e}")

    # 2. Fetch Nearby Places (Mini-RAG)
    if lat and lng:
        try:
            from app import FOURSQUARE_API_KEY
            import requests
            url = "https://api.foursquare.com/v3/places/search"
            headers = {
                "Accept": "application/json",
                "Authorization": FOURSQUARE_API_KEY
            }
            params = {
                "ll": f"{lat},{lng}",
                "radius": "2000",
                "categories": "16000,10000,13000", # Sights, Arts, Dining
                "limit": 5,
                "fields": "name"
            }
            res = requests.get(url, params=params, headers=headers).json()
            places = res.get("results", [])
            if places:
                places_str = ", ".join([p.get("name") for p in places])
                nearby_context = f"Locații din apropiere (via Foursquare): {places_str}."
        except Exception as e:
            print(f"⚠️ RAG Nearby Error: {e}")

    # 3. Create Rich Prompt
    system_prompt = (
        "Ești 'MysticMinds', ghidul AI inteligent din CityScape. Locația: București. "
        f"Context Utilizator: {user_context} "
        f"Context Locație: {nearby_context} "
        f"Limba: {'română' if language == 'ro' else 'engleză'}. "
        "Fii personal, folosește datele primite pentru a oferi recomandări specifice. "
        "La final, pune [SUGGESTIONS: Sugestie 1 | Sugestie 2 | Sugestie 3]"
    )

    # Try Gemini, if fails, use local model
    try:
        chat = gemini_model.start_chat()
        full_msg = f"{system_prompt}\n\nÎntrebare utilizator: {msg}"
        response = chat.send_message(full_msg)
        text = response.text
        
        # Parse suggestions
        suggestions = []
        if "[SUGGESTIONS:" in text:
            parts = text.split("[SUGGESTIONS:")
            text = parts[0].strip()
            raw_sug = parts[1].replace("]", "").strip()
            suggestions = [s.strip() for s in raw_sug.split("|")][:3]
        else:
            suggestions = _get_fallback_suggestions(language)[:3]
            
        return {
            "answer": text,
            "intent": "rag_gemini",
            "confidence": 1.0,
            "suggestions": suggestions
        }
    except Exception as e:
        print(f"⚠️ RAG Gemini Error: {e}. Falling back to local model.")
        
        # LOCAL FALLBACK LOGIC (DistilBERT)
        if model is not None:
            top_tag, confidence, _ = _predict_intent(msg)
            if confidence > CONFIDENCE_THRESHOLD:
                if top_tag in intent_lookup:
                    intent_data = intent_lookup[top_tag]
                    response = _get_response_for_intent(intent_data, language)
                    suggestions = _get_suggestions_for_intent(intent_data, language)
                    return {
                        "answer": response + " (Notă: Folosesc modelul de rezervă până activezi Gemini)",
                        "intent": top_tag,
                        "confidence": round(confidence, 4),
                        "suggestions": suggestions
                    }
        
        return {
            "answer": "Momentan întâmpin o mică dificultate tehnică, dar sunt aici să te ajut cu restul funcțiilor din București! 😊",
            "intent": "error_fallback",
            "suggestions": _get_fallback_suggestions(language)
        }

def get_response(msg, language="ro"):
    # Legacy wrapper
    res = get_response_with_details(msg, language)
    return res["answer"]

def get_response_with_details(msg, language="ro"):
    # Wrap with RAG (simplified for direct calls)
    return get_response_with_rag(msg, language=language)
