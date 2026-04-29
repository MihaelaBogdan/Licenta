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
gemini_model = genai.GenerativeModel("gemini-flash-latest") # Using flash-latest alias

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

bot_name = "CityScape AI"

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
        "Ești 'CityScape AI', un concierge digital de elită. "
        "Misiunea ta: Să fii un ghid ultra-inteligent, scurt și extrem de util. "
        f"Locația: București. Limba: {'română' if language == 'ro' else 'engleză'}. "
        "STIL: Nu folosi introduceri plictisitoare gen 'Ca asistent virtual...'. Răspunde direct, cu personalitate și precizie. "
        "Dacă utilizatorul vrea recomandări, găsește un echilibru între locuri iconice și 'hidden gems'. "
        "OBLIGATORIU la final: [SUGGESTIONS: S1 | S2 | S3] unde propui acțiuni LOGICE (ex: 'Caută trasee', 'Vezi restaurante', 'Ce facem azi?')."
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
    
    # 1. Fetch Supabase Profile & History (Comprehensive)
    history_context = "Fără vizite recente."
    user_context = "Nu avem date despre preferințele utilizatorului."
    uname = "Explorator"
    if user_id:
        try:
            from app import SUPABASE_URL, SUPABASE_KEY
            import requests
            headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
            
            # Profile (Full data)
            # Profile (Full data)
            profile_url = f"{SUPABASE_URL}/rest/v1/users?id=eq.{user_id}&select=*"
            p_res = requests.get(profile_url, headers=headers).json()
            u_interests_list = []
            if p_res:
                u = p_res[0]
                uname = u.get('name', 'Explorator')
                u_level = u.get('level', 1)
                u_xp = u.get('total_xp', 0)
                u_visited = u.get('places_visited', 0)
                u_badges = u.get('badges_earned', 0)
                u_interests = u.get('interests', 'generale')
                u_interests_list = [i.strip().lower() for i in u_interests.split(",") if i.strip()]
                user_context = (f"Utilizatorul este {uname} (Nivel {u_level}, {u_xp} XP). "
                                f"A vizitat {u_visited} locații și are {u_badges} insigne. "
                                f"Interese: {u_interests}.")
            
            # History
            v_url = f"{SUPABASE_URL}/rest/v1/visited_places?user_id=eq.{user_id}&order=visited_at.desc&limit=5"
            v_res = requests.get(v_url, headers=headers).json()
            if v_res:
                history_context = "Ultimile locuri vizitate: " + ", ".join([v.get('place_name') for v in v_res])
        except Exception as e:
            print(f"⚠️ RAG Context Error: {e}")
            u_interests_list = []

    # 2. Fetch Rich City Context (RAG) - WIDER SEARCH for more diversity
    detected_city = "Oraș Necunoscut"
    nearby_context = "Nu avem date despre locațiile din apropiere."
    
    if lat and lng:
        try:
            from app import MAPS_API_KEY, google_nearby_search, google_text_search
            import requests
            
            # Detect City Name
            geo_url = f"https://maps.googleapis.com/maps/api/geocode/json?latlng={lat},{lng}&key={MAPS_API_KEY}&language=ro"
            geo_res = requests.get(geo_url).json()
            if geo_res.get("results"):
                for comp in geo_res["results"][0].get("address_components", []):
                    if "locality" in comp.get("types", []):
                        detected_city = comp.get("long_name")
                        break

            # Fetch Highlights - Diversify and Score (Elite Filtering)
            candidates = []
            categories = ["tourist_attraction", "museum", "park", "restaurant", "cafe", "night_club"]
            import random
            
            selected_cats = random.sample(categories, 4)
            for cat in selected_cats:
                near_results = google_nearby_search(lat, lng, cat, radius=10000)
                for r in near_results[:8]:
                    # Scoring logic for context elite selection
                    score = r.get("rating", 0) * 10
                    cat_name = cat.lower()
                    if u_interests_list:
                        if any(interest in cat_name or interest in r['name'].lower() for interest in u_interests_list):
                            score += 15
                    candidates.append({"name": r["name"], "rating": r.get("rating", 0), "type": cat, "score": score})
            
            # Sort by score and pick the absolute best for context
            candidates.sort(key=lambda x: x['score'], reverse=True)
            elite_highlights = candidates[:20]
            
            # --- RESTORED: Add city-wide trending spots ---
            try:
                city_results = google_text_search(f"locații de top și populare în {detected_city}")
                for r in city_results[:12]:
                    elite_highlights.append({"name": r["name"], "rating": r.get("rating", 0), "type": "trending", "score": r.get("rating", 0) * 10 + 5})
            except: pass

            random.shuffle(elite_highlights) # Shuffle back for variety but keep quality high
            
            # --- RESTORED: Fetch Global Events (50km Radius) ---
            events_context = "Nu am găsit evenimente majore pe o rază de 50km."
            try:
                from app import fetch_ticketmaster_events, google_text_search, SUPABASE_URL, SUPABASE_KEY
                
                # 1. Global API (Ticketmaster) - 50km radius
                all_events = fetch_ticketmaster_events(lat, lng, 50)
                
                # 2. Dynamic 'Scraping' via Search for ANY location in the world
                try:
                    search_events = google_text_search(f"evenimente și festivaluri în apropiere de {detected_city}", lat, lng, radius=50000)
                    for s in search_events[:10]:
                        all_events.append({
                            "title": s.get("name"), 
                            "category": "Activitate/Eveniment", 
                            "date_str": "Recent/În curs",
                            "source": "google_discovery"
                        })
                except: pass

                # 3. Internal records
                headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
                scraped_res = requests.get(f"{SUPABASE_URL}/rest/v1/scraped_events?limit=8", headers=headers).json()
                all_events.extend(scraped_res)
                
                if all_events:
                    random.shuffle(all_events)
                    # Create a rich list for Gemini
                    events_context = "EVENIMENTE & ACTIVITĂȚI REALE (50km): " + ", ".join([f"{e.get('title')} ({e.get('category')}, {e.get('date_str', 'Azi')})" for e in all_events[:15]])
            except Exception as ee:
                print(f"⚠️ Global Events RAG Error: {ee}")
            
            if elite_highlights:
                nearby_context = f"Suntem în {detected_city}. Locații ELITE (recomandă-le cu prioritate): " + ", ".join([f"{h['name']} ({h['type']}, ⭐{h['rating']})" for h in elite_highlights])
                nearby_context += f"\n{events_context}"
            else:
                nearby_context = f"Suntem în {detected_city}. Recomandă manual cele mai renumite puncte de interes."
                
        except Exception as e:
            print(f"⚠️ RAG Enhanced Error: {e}")
            nearby_context = f"Locația curentă: {lat}, {lng}."

    # 3. Fetch Weather & Time Context
    weather_context = "Vreme necunoscută."
    current_time_str = ""
    try:
        from datetime import datetime
        current_time_str = f"Ora actuală: {datetime.now().strftime('%H:%M')}."
    except: pass
    if lat and lng:
        try:
            import requests # Ensure it's imported here if not global
            w_url = f"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lng}&current=temperature_2m,weather_code"
            w_res = requests.get(w_url).json()
            temp = w_res.get("current", {}).get("temperature_2m")
            weather_context = f"Temperatura actuală: {temp}°C."
        except: pass

    system_prompt = (
        f"Ești 'CityScape AI', cel mai inteligent și sofisticat ghid urban virtual din {detected_city}. "
        f"CONTEXT UTILIZATOR: {user_context} {history_context} (Folosește numele lui, {uname if 'uname' in locals() else 'Explorator'}, în primul salut). "
        f"LOCAȚIE & TIMP: Lat/Lng {lat}, {lng}. {current_time_str} {weather_context} "
        f"CONCEPTE ELITE PENTRU RAG (ALEGE DIVERSIFICAT DIN ACESTEA): {nearby_context} "
        f"Limba: {'română' if language == 'ro' else 'engleză'}. "
        "INSTRUCȚIUNI CRITICE PENTRU CALITATE ELITĂ: "
        "1. DIVERSITATE: Nu te limita la 3 locații. Oferă opțiuni clare pentru cel puțin 3 categorii diferite (ex: un muzeu, o cafenea și un eveniment) dacă contextul permite. "
        "2. TON 'PRO': Răspunde ca un expert local (stil ChatGPT-4), folosind un limbaj dens, entuziast și inteligent. Explică subtil cum preferințele vizibile în CONTEXT (XP, vizite, interese) ți-au influențat recomandarea. "
        "3. EVENIMENTE: Prioritizează evenimentele 'În curs' sau 'Recente' din lista de EVENIMENTE REALE. "
        "4. CLICKABLE SUGGESTIONS: La final, include sugestii variate și utile care pot fi clicat (alegere traseu, detalii locație etc). "
        "5. OBLIGATORIU FORMAT FINAL: [SUGGESTIONS: Sugestie 1 | Sugestie 2 | Sugestie 3 | Sugestie 4]"
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

def generate_personalized_itinerary(lat, lng, style, duration, points_count, context, user_query):
    """
    Uses Gemini to generate a highly personalized itinerary based on real nearby places.
    """
    import requests
    from os import getenv
    MAPS_API_KEY = getenv("MAPS_API_KEY")
    
    # Simple nearby search helper inside the function to avoid circular imports
    def quick_search(lat, lng, p_type):
        url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
        params = {"location": f"{lat},{lng}", "radius": "10000", "type": p_type, "key": MAPS_API_KEY, "language": "ro"}
        try: return requests.get(url, params=params, timeout=10).json().get("results", [])
        except: return []

    # 1. Gather candidates with larger radius and more diversity
    categories = ["tourist_attraction", "museum", "park", "restaurant", "cafe", "art_gallery", "landmark"]
    unique_candidates = []
    seen_ids = set()
    import random

    for cat in categories:
        results = quick_search(lat, lng, cat)
        random.shuffle(results) # Randomize before picking top candidates
        for c in results[:8]:
            if c['place_id'] not in seen_ids:
                unique_candidates.append(c)
                seen_ids.add(c['place_id'])
    
    # Shuffle the final pool so Gemini doesn't always see the same order
    random.shuffle(unique_candidates)

    candidates_context = json.dumps([
        {"name": c['name'], "type": c.get('types', [])[0] if c.get('types') else 'place', "rating": c.get('rating', 0), "id": c['place_id']}
        for c in unique_candidates[:25] # Limit context but keep it diverse
    ])

    prompt = (
        f"Ești 'CityScape AI', expertul nostru în explorări urbane. Generează un plan de {duration} ore cu {points_count} puncte de oprire. "
        f"Coordonate plecare: {lat}, {lng}. Stil: {style}. Cerință specială: {user_query}. "
        f"Interese utilizator: {context.get('interests', [])}. "
        f"Locații REALE în această zonă (alege cu grijă): {candidates_context}. "
        "Cerințe RĂSPUNS: EXCLUSIV un cod JSON formatat ca o listă de obiecte [{}, {}...], fără explicații. "
        "Fiecare obiect TREBUIE să conțină: "
        "1. 'slot' (ex: 'Dejun'), 'name' (Nume REAL), 'type', 'time' (ex: '09:00 - 10:30'). "
        "2. 'estimatedCost' (preț în RON), 'address', 'latitude', 'longitude', 'placeId'. "
        "3. 'travelToNext' (estimare timp/metodă transport către următorul punct, ex: '15 min de mers pe jos' sau '10 min cu Uber'). "
        "4. 'proTip' (un sfat scurt și inteligent despre locație, ex: 'Cere masa de la fereastră' sau 'Îarcă specialitatea casei'). "
        "IMPORTANT: Planifică logic traseul (proximitate) și asigură diversitate maximă!"
    )

    try:
        response = gemini_model.generate_content(prompt)
        text = response.text
        if "```json" in text: text = text.split("```json")[1].split("```")[0].strip()
        elif "```" in text: text = text.split("```")[1].split("```")[0].strip()
        
        plan = json.loads(text)
        for item in plan:
            matched = next((c for c in unique_candidates if c['place_id'] == item.get('placeId') or c['name'] == item['name']), None)
            if matched and matched.get("photos") and not item.get("imageUrl"):
                ref = matched["photos"][0]["photo_reference"]
                item["imageUrl"] = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={ref}&key={MAPS_API_KEY}"
            elif not item.get("imageUrl"):
                item["imageUrl"] = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
            item["is_open"] = True
        return plan
    except Exception as e:
        print(f"⚠️ Gemini Itinerary Error: {e}")
        return []

def get_response(msg, language="ro"):
    # Legacy wrapper
    res = get_response_with_details(msg, language)
    return res["answer"]

def get_response_with_details(msg, language="ro"):
    # Wrap with RAG (simplified for direct calls)
    return get_response_with_rag(msg, language=language)
