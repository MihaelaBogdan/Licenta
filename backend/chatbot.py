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

# Try to import torch + transformers (optional — only needed for local DistilBERT fallback)
try:
    import torch
    from transformers import DistilBertTokenizerFast
    from model import IntentClassifier
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False
    print("⚠️ torch/transformers not installed — local DistilBERT fallback disabled. Gemini will be used.")

# ============================================================
# Load model and data
# ============================================================

BASE_PATH = os.path.dirname(os.path.abspath(__file__))
SAVE_DIR = os.path.join(BASE_PATH, "distilbert_model")

if TORCH_AVAILABLE:
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
else:
    device = None

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

# Load model data (only if torch available)
tags = []
tag_to_idx = {}
num_classes = 0
MAX_LENGTH = 64
dropout_rate = 0.3
tokenizer = None
model = None

if TORCH_AVAILABLE:
    try:
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
        model = IntentClassifier(num_classes=num_classes, dropout_rate=dropout_rate).to(device)
        model.load_state_dict(model_data['model_state'], strict=False)
        model.eval()
        print("✅ Chatbot model loaded successfully!")
    except Exception as e:
        print(f"⚠️ Warning: Chatbot model failed to load: {e}")
        print("Fallback to basic response logic will be used.")
        model = None

# Load SITUR places once for dynamic RAG and fallback queries
SITUR_PLACES = []
try:
    situr_path = os.path.join(BASE_PATH, 'data', 'situr_places.json')
    if os.path.exists(situr_path):
        with open(situr_path, 'r', encoding='utf-8') as f:
            SITUR_PLACES = json.load(f)
        print(f"✅ Loaded {len(SITUR_PLACES)} SITUR places from Ministry of Tourism!")
except Exception as e:
    print(f"⚠️ Failed to load SITUR places: {e}")

def search_situr_places(query_text, limit=10):
    if not SITUR_PLACES or not query_text:
        return []
    
    q = query_text.lower().strip()
    
    # Clean query of common stop words
    stop_words = ["vreau", "recomanda", "cauta", "gaseste", "un", "o", "in", "din", "la", "pe", "de", "sau", "unde", "mananc", "ies", "restaurant", "bar", "cafenea", "local", "mancare", "show", "find", "recommend", "eat", "places", "in", "near"]
    words = [w for w in q.split() if w not in stop_words and len(w) > 2]
    if not words:
        words = [w for w in q.split() if len(w) > 2]
        
    candidates = []
    
    for p in SITUR_PLACES:
        score = 0
        name = p.get("name", "").lower()
        ptype = p.get("type", "").lower()
        county = p.get("county", "").lower()
        locality = p.get("locality", "").lower()
        
        for w in words:
            if w in locality:
                score += 50
            if w in county:
                score += 40
            if w in name:
                score += 20
            if w in ptype:
                score += 15
                
        if score > 0:
            candidates.append((score, p))
            
    candidates.sort(key=lambda x: -x[0])
    return [c[1] for c in candidates[:limit]]

bot_name = "CityScape AI"
CHAT_HISTORIES = {}

# ============================================================
# Fallback responses (bilingual)
# ============================================================

FALLBACK_RESPONSES_RO = [
    "Hmm, nu sunt sigur de asta. Poți reformula? Învăț constant despre noi orașe! 🤔",
    "Nu am înțeles exact. Încearcă să întrebi despre restaurante, parcuri sau muzee din zona ta!",
    "Îmi pare rău, nu am prins asta! Pot să te ajut cu recomandări, sfaturi de călătorie și discuții generale! 😊",
    "Hmm, asta e una dificilă! Încearcă să reformulezi, sau întreabă-mă despre ceva specific din orașul tău! 🗺️",
    "Nu sunt sigur ce vrei să spui. Pot să te ajut cu restaurante, parcuri, muzee sau activități locale!",
    "Ups! Nu am prins asta. Sunt grozav cu sfaturi de călătorie, recomandări de mâncare și discuții generale! 💬",
    "Încă învăț! Încearcă să întrebi: 'Unde ar trebui să mănânc?', 'Cel mai bun parc?', sau 'Spune-mi o glumă!' 🌟"
]

FALLBACK_RESPONSES_EN = [
    "Hmm, I'm not sure about that. Could you rephrase? I'm always learning about new cities! 🤔",
    "I don't quite understand. Try asking about restaurants, parks, or museums in your area!",
    "Sorry, I couldn't get that! I can help with local recommendations, travel tips, and general chat! 😊",
    "Hmm, that's a tough one! Try rephrasing, or ask me about something specific in your city! 🗺️",
    "I'm not sure what you mean. I can help with restaurants, parks, museums, or local activities!",
    "Oops! I didn't catch that. I'm great with travel tips, food recommendations, and casual chat! 💬",
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
    if language == "en":
        refuse_msg = "I am designed exclusively to help you with local recommendations, events, and the CityScape app."
        system_prompt = (
            "You are 'CityScape AI', the friendly urban guide and assistant of the CityScape app. "
            "Language: English. "
            "MAXIMUM 1-2 sentences. No intros, no fluff. Answer DIRECTLY. "
            "STRICT DOMAIN LIMITATION (CRITICAL): Only answer queries related to urban tourism, place recommendations (restaurants, cafes, parks, museums, attractions), local events, or CityScape app features. "
            "STRICT EXCLUSION RULES: It is STRICTLY FORBIDDEN to provide recipes or cooking instructions, write programming code/scripts, solve homework or academic problems, or provide general knowledge details. If the query is not related to urban tourism or the CityScape app, politely decline in maximum one sentence (e.g. '" + refuse_msg + "'). "
            "GOOGLE MAPS LINKS: When recommending a place, always include its Google Maps link as an HTML hyperlink, for example: <a href=\"https://www.google.com/maps/search/?api=1&query=LAT,LNG(NAME)\">Place Name</a>. No markdown bold/italic, just plain text and HTML hyperlinks."
            "REQUIRED at the end: [SUGGESTIONS: S1 | S2 | S3] (short, 2-4 words)."
        )
    else:
        system_prompt = (
            "Ești 'CityScape AI', ghidul urban și asistentul aplicației CityScape. "
            f"Limba: {'română' if language == 'ro' else 'engleză'}. "
            "MAXIM 1-2 propoziții. Fără introduceri, fără polologhii. Răspunde DIRECT și la obiect. "
            "LIMITARE STRICTĂ DOMENIU (CRITIC): Răspunde EXCLUSIV la întrebări legate de turism urban, recomandări de locuri (restaurante, cafenele, parcuri, muzee, atracții), evenimente locale sau funcționalități ale aplicației CityScape. "
            "REGULI DE EXCLUDERE CATEGORICĂ: Este STRICT INTERZIS să oferi rețete de bucătărie sau instrucțiuni de gătit, să scrii cod sau scripturi de programare, să rezolvi teme de școală sau probleme academice, sau să oferi detalii de cultură generală (cum ar fi istoria generală a unor personalități). Dacă întrebarea nu are legătură cu turismul urban sau aplicația CityScape, refuză politicos în maximum o propoziție (ex: 'Sunt conceput exclusiv pentru a te ajuta cu recomandări de locuri, evenimente și utilizarea aplicației CityScape.'). "
            "LINK-URI GOOGLE MAPS: Când recomanzi o locație, include întotdeauna link-ul ei Google Maps sub formă de hyperlink HTML, de exemplu: <a href=\"https://www.google.com/maps/search/?api=1&query=LAT,LNG(NUME)\">Nume Locație</a>. Fără markdown bold/italic, doar text simplu și hyperlink-uri HTML."
            "OBLIGATORIU la final: [SUGGESTIONS: S1 | S2 | S3] (scurte, 2-4 cuvinte)."
        )
    
    try:
        chat = gemini_model.start_chat()
        if language == "en":
            full_msg = f"{system_prompt}\n\nUser message: {msg}\n(IMPORTANT: Answer ONLY in English. Do NOT use Romanian.)"
        else:
            full_msg = f"{system_prompt}\n\nMesaj utilizator: {msg}\n(IMPORTANT: Raspunde doar in limba romana.)"
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


def format_event_date_ro(date_str):
    if not date_str or date_str == "TBA":
        return "TBA"
    days_names = ["Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică"]
    if any(date_str.startswith(d) for d in days_names):
        return date_str
    
    # Try parsing as ISO date (YYYY-MM-DD)
    try:
        from datetime import datetime
        dt = datetime.strptime(date_str[:10], "%Y-%m-%d")
        months_ro = ["Ian","Feb","Mar","Apr","Mai","Iun","Iul","Aug","Sep","Oct","Nov","Dec"]
        days_ro = ["Luni","Marți","Miercuri","Joi","Vineri","Sâmbătă","Duminică"]
        return f"{days_ro[dt.weekday()]}, {dt.day} {months_ro[dt.month-1]}"
    except:
        return date_str


# App-related keywords filter
APP_KEYWORDS_RO = [
    "locație", "loc", "locuri", "restaurant", "café", "cafenea", "parc", "muzeu",
    "atracție", "recomandare", "recomanzi", "recomanda", "explore", "vizita", "vizitez",
    "hartă", "harta", "rută", "ruta", "distanță", "rating", "review", "eveniment",
    "activitate", "plan", "itinerar", "nearby", "trending", "popular", "vizitat",
    "calitate", "liber", "open", "închis", "pret", "cost", "gratis", "city", "oras",
    "bucuresti", "cluj", "iasi", "timisoara", "locuri", "unde", "cum", "mers",
    "ce fac", "ce sa fac", "buna", "salut", "azi", "azi", "weekend", "seara",
    "plimbare", "iesire", "distractie", "manca", "bea", "cafea", "bar", "club",
    "concert", "festival", "teatru", "film", "sport", "ajung", "directii",
    "aproape", "zona", "cartier", "centru", "vechi", "nou", "mai bun", "top",
    "zi", "noapte", "dimineata", "pranz", "cina", "copii", "familie", "prieteni",
    "romantic", "solo", "ieftin", "scump", "buget", "gratuit", "deschis"
]
APP_KEYWORDS_EN = ["location", "place", "restaurant", "cafe", "park", "museum", "attraction", "recommendation", "explore", "visit", "map", "route", "distance", "rating", "review", "event", "activity", "plan", "itinerary", "nearby", "trending", "popular", "visited", "rate", "quality", "open", "closed", "price", "cost", "free", "city", "bucharest", "where", "how", "go"]

def is_app_related(msg, language="ro"):
    """Check if message is related to app functionality"""
    msg_lower = msg.lower().strip()

    keywords = APP_KEYWORDS_RO if language == "ro" else APP_KEYWORDS_EN

    # Check if any app keyword is in the message
    for keyword in keywords:
        if keyword in msg_lower:
            return True

    # If contains location-like terms
    if any(c in msg_lower for c in ["@", "lat:", "lng:", "52°", "°", "m away", "km"]):
        return True

    return False

def format_location_links(text, locations=None):
    """Format location names as clickable Google Maps links"""
    if not text or not locations:
        return text

    # Known trending locations in Bucharest with coordinates
    location_coords = {
        "Piața Constituției": (44.4272, 26.0915),
        "Grădina Cișmigiu": (44.4373, 26.0910),
        "Arcul de Triumf": (44.4723, 26.0843),
        "Lipscani": (44.4270, 26.1090),
        "Parcul Herăstrău": (44.4760, 26.0850),
        "Mănăstirea Stavropoleos": (44.4265, 26.1035),
        "Palatul Parlamentului": (44.4267, 26.0893),
        "Grădina Botanică": (44.4372, 26.0626),
        "Cafeneaua Capșa": (44.4383, 26.1037),
        "Crama Domnească": (44.4268, 26.1050),
    }

    result = text
    for loc_name, (lat, lng) in location_coords.items():
        if loc_name in text:
            # Format: [Name](https://www.google.com/maps/search/lat,lng (Name))
            maps_url = f"https://www.google.com/maps/search/{lat},{lng}+({loc_name})"
            html_link = f'<a href="{maps_url}"><b>{loc_name}</b></a>'
            result = result.replace(loc_name, html_link)

    return result

def get_response_with_rag(msg, user_id=None, lat=None, lng=None, language="ro", city_name=None, interests=None, user_xp=None, user_level=None, places_visited=None):
    if not msg or not msg.strip():
        return {"answer": _get_empty_message(language), "intent": "empty", "suggestions": _get_fallback_suggestions(language)}

    # Soft filter: mesaje complet off-topic (cod, retete, teme scoala, bancuri) => refuz scurt instantaneu
    HARD_OFFTOPIC = [
        "recipe", "rețetă", "reteta", "cod python", "homework", "tema scoala", "tema acasa",
        "write code", "scrie cod", "rezolva ecuatia", "integral", "derivata", "java class",
        "c++", "javascript", "html css", "programare", "software development", "leaky pipe",
        "leaky faucet", "plumbing", "math equation", "matematica", "fizica", "chimie",
        "science project", "joke about", "banc cu", "poezie", "poem", "write a story",
        "scrie o poveste", "medical advice", "sfat medical", "doctor", "diagnose"
    ]
    if any(kw in msg.lower() for kw in HARD_OFFTOPIC):
        response = ("Sunt ghidul tău urban CityScape — mă pricep la locuri, evenimente și explorare în oraș. "
                    "Cu ce te pot ajuta să descoperi?") if language == "ro" else \
                   ("I'm your CityScape urban guide — I know places, events and city exploration. "
                    "How can I help you discover something?")
        return {"answer": response, "intent": "out_of_scope", "suggestions": _get_fallback_suggestions(language)}

    user_name = "Explorator CityScape"
    user_level_val = user_level or 1
    user_xp_val = user_xp or 0
    user_interests_val = interests or "Generale"
    user_badges_val = "Nicio insignă momentan"
    visited_places_list = []
    visits_list = []

    nearby_context = ""
    events_context = ""
    social_trends_context = ""
    detected_city = city_name or "un oraș nespecificat"

    if user_id:
        try:
            from app import SUPABASE_URL, SUPABASE_KEY
            import requests
            headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
            profile_url = f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{user_id}&select=*"
            p_res = requests.get(profile_url, headers=headers, timeout=2).json()
            
            visits_url = f"{SUPABASE_URL}/rest/v1/visited_places?user_id=eq.{user_id}&order=visited_at.desc&limit=10"
            v_res = requests.get(visits_url, headers=headers, timeout=2).json()
            
            badges_url = f"{SUPABASE_URL}/rest/v1/user_badges?user_id=eq.{user_id}&select=name"
            b_res = requests.get(badges_url, headers=headers, timeout=2).json()

            if p_res:
                u = p_res[0]
                user_name = u.get("name", "Explorator CityScape")
                user_interests_val = u.get("interests") or interests or "Generale"
                user_xp_val = u.get("total_xp") or user_xp or 0
                user_level_val = u.get("level") or user_level or 1

            if b_res and isinstance(b_res, list):
                badge_names = [b.get("name") for b in b_res if b.get("name")]
                if badge_names:
                    user_badges_val = ", ".join(badge_names)

            if v_res and isinstance(v_res, list):
                visited_places_list = [v.get('place_name') for v in v_res if v.get('place_name')]
                visits_list = [f"{v.get('place_name')} ({v.get('place_type', 'Atracție')})" for v in v_res if v.get('place_name')]
        except Exception as e:
            print(f"⚠️ RAG Supabase Context Fetch Error: {e}")
            pass

    # 2. Fetch REAL EVENTS + NEARBY PLACES
    if lat and lng:
        try:
            port = os.environ.get('PORT', '5001')
            from app import MAPS_API_KEY, google_nearby_search
            import requests

            # Get EVENTS (NEW!)
            try:
                events_url = f"http://localhost:{port}/events?lat={lat}&lng={lng}&interests={interests or ''}"
                events_res = requests.get(events_url, timeout=3).json()
                if events_res and isinstance(events_res, list):
                    # Format events date using helper
                    for event in events_res:
                        event['formatted_date'] = format_event_date_ro(event.get('date', 'TBA'))
                    
                    # Detect weekend query
                    msg_lower = msg.lower()
                    is_weekend_query = any(w in msg_lower for w in ["weekend", "sâmbătă", "sambata", "duminică", "duminica", "sfarsit de saptamana", "sfârșit de săptămână"])
                    
                    if is_weekend_query:
                        weekend_days = ["Vineri", "Sâmbătă", "Duminică"]
                        events_res = [e for e in events_res if any(d in e.get('formatted_date', '') for d in weekend_days)]

                    # Format events nicely
                    events_list = []
                    for event in events_res[:10]:  # Top 10 events
                        evt = f"📌 {event.get('title', 'Event')[:40]} ({event.get('formatted_date', 'TBA')}) [lat: {event.get('latitude', lat)}, lng: {event.get('longitude', lng)}]"
                        events_list.append(evt)

                    if events_list:
                        events_context = "🎭 EVENIMENTE DIN APROPIERE:\n" + "\n".join(events_list) + "\n\n"
                    elif is_weekend_query:
                        events_context = "🎭 EVENIMENTE DIN APROPIERE:\n(Nu s-au găsit evenimente pentru weekend-ul acesta în zona utilizatorului. Spune-i politicos utilizatorului că nu sunt evenimente în weekend-ul acesta, fără să inventezi altele.)\n\n"
            except Exception as e:
                print(f"⚠️ Events fetch error: {e}")

            # Get SOCIAL TRENDS (NEW!)
            social_trends_context = ""
            try:
                msg_lower = msg.lower()
                if any(x in msg_lower for x in ["trend", "popular", "social", "hype", "vibe", "ce se poarta"]):
                    trends_url = f"http://localhost:{port}/social/trending"
                    trends_res = requests.get(trends_url, timeout=3).json()
                    if trends_res:
                        social_trends_context = (
                            f"🔥 TRENDURI SOCIAL MEDIA RECENTE (extrase din feed-ul comunității):\n"
                            f"- Rezumat: {trends_res.get('trending_summary')}\n"
                            f"- Hashtag-uri populare: {', '.join(trends_res.get('top_hashtags', []))}\n"
                            f"- Locații cu cel mai mare Hype: " + 
                            ", ".join([f"{p['name']} (Hype Score: {p['hype_score']})" for p in trends_res.get('hype_places', [])]) + "\n\n"
                        )
            except Exception as e:
                print(f"⚠️ Social trends fetch error for chatbot: {e}")

            # Get nearby places
            near_results = google_nearby_search(lat, lng, "tourist_attraction", radius=10000)
            if near_results:
                places_formatted = []
                for place in near_results[:8]:
                    place_name = place.get('name', 'Locație')
                    rating = place.get('rating', 0)
                    place_type = place.get('types', ['place'])[0].replace('_', ' ').title()
                    
                    loc = place.get('geometry', {}).get('location', {})
                    plat = loc.get('lat', lat)
                    plng = loc.get('lng', lng)
                    
                    places_formatted.append(f"📍 {place_name} ({place_type}, ⭐{rating}) [lat: {plat}, lng: {plng}]")

                nearby_context = "LOCURI REALE ÎN ZONĂ:\n" + "\n".join(places_formatted) + "\n\n"
        except Exception as e:
            print(f"⚠️ Places/Events fetch error: {e}")

    # Query SITUR Ministry of Tourism database dynamically for matching places
    situr_places = search_situr_places(msg, limit=8)
    situr_block = ""
    if situr_places:
        situr_list = []
        for p in situr_places:
            situr_list.append(
                f"• {p.get('name')} ({p.get('type')}) - {p.get('locality')}, Județul {p.get('county')} "
                f"[Clasificare: {p.get('category') or 'N/A'}, Adresă: {p.get('address') or 'N/A'}, Locuri: {p.get('seats') or 'N/A'}]"
            )
        situr_block = "DATE OFICIALE MINISTERUL TURISMULUI (SITUR):\n" + "\n".join(situr_list) + "\n\n"

    nav_keywords = ["ajung", "directii", "ruta", "navigatie", "maps", "cum merg", "drum", "directions", "route", "map", "navigation", "how to get", "get to"]
    wants_navigation = any(k in msg.lower() for k in nav_keywords)
    visited_str = ", ".join(visited_places_list[:5]) if visited_places_list else ("none" if language == "en" else "niciunul")
    nearby_block = (("REAL PLACES IN THE AREA (use these in recommendations):\n" if language == "en" else "LOCURI REALE DIN ZONA (foloseste-le in recomandari):\n") + nearby_context) if nearby_context else ""
    events_block = (("AVAILABLE EVENTS:\n" if language == "en" else "EVENIMENTE DISPONIBILE:\n") + events_context) if events_context else ""
    
    if language == "en":
        nav_instruction = (
            "IMPORTANT: The user is asking for directions. At the end of your response, add [MAPS:Name:LAT:LNG] "
            "for each mentioned place (coordinates are found in the context above). "
            "Example: [MAPS:Herastrau Park:44.4706:26.0827]"
            if wants_navigation else
            "DO NOT add [MAPS:...] tags — the user is not asking for directions."
        )
    else:
        nav_instruction = (
            "IMPORTANT: Utilizatorul cere directii. La finalul raspunsului adauga [MAPS:Nume:LAT:LNG] "
            "pentru fiecare loc mentionat (coordonatele le gasesti in contextul de mai sus). "
            "Exemplu: [MAPS:Parcul Herastrau:44.4706:26.0827]"
            if wants_navigation else
            "NU adauga taguri [MAPS:...] — utilizatorul nu cere directii."
        )

    out_of_scope_reply = (
        "I'm your CityScape urban guide — I know places, events and city exploration. How can I help you discover something?"
        if language == "en" else
        "Sunt ghidul tău urban CityScape — mă pricep la locuri, evenimente și explorare în oraș. Cu ce te pot ajuta să descoperi?"
    )

    if language == "en":
        system_prompt = (
            "You are CityScape AI, the friendly urban guide of the CityScape app.\n\n"
            f"STRICT SCOPE LIMITATION: You are strictly an urban travel guide. You must ONLY answer queries related to "
            f"places, traveling, cities, local recommendations, events, itineraries, local weather, and user stats/profile "
            f"within the CityScape app. If the user asks about ANY other topic (such as general knowledge, history not related to places, "
            f"cooking/recipes, homework, coding, general advice, etc.), you MUST decline to answer by returning EXACTLY this response: "
            f"'{out_of_scope_reply}' and nothing else (except the SUGGESTIONS block at the end).\n\n"
            f"CONTEXT:\n"
            f"- City: {detected_city}\n"
            f"- Username: {user_name}\n"
            f"- User Level: {user_level_val} (XP: {user_xp_val})\n"
            f"- User Preferences & Interests: {user_interests_val}\n"
            f"- User Unlocked Badges: {user_badges_val}\n"
            f"- Already visited places (do not recommend these): {visited_str}\n"
        )
        if visits_list:
            system_prompt += f"- Recent visit history: {', '.join(visits_list)}\n"
        system_prompt += (
            f"\n{situr_block}"
            f"{nearby_block}\n"
            f"{events_block}\n"
            f"{social_trends_context}\n"
            f"You MUST answer ONLY in English, naturally and friendly, like a local friend who knows the city well. "
            f"MAXIMUM 2 short sentences (under 30 words total). Do NOT use bullet points, do NOT use markdown.\n\n"
            f"{nav_instruction}\n\n"
            f"At the end add exactly: [SUGGESTIONS: suggestion1 | suggestion2 | suggestion3]"
        )
    else:
        system_prompt = (
            "Esti CityScape AI, ghidul urban prietenos al aplicatiei CityScape.\n\n"
            f"STRICT SCOPE LIMITATION: You are strictly an urban travel guide. You must ONLY answer queries related to "
            f"places, traveling, cities, local recommendations, events, itineraries, local weather, and user stats/profile "
            f"within the CityScape app. If the user asks about ANY other topic (such as general knowledge, history not related to places, "
            f"cooking/recipes, homework, coding, general advice, etc.), you MUST decline to answer by returning EXACTLY this response: "
            f"'{out_of_scope_reply}' and nothing else (except the SUGGESTIONS block at the end).\n\n"
            f"CONTEXT:\n"
            f"- Oraș: {detected_city}\n"
            f"- Nume Utilizator: {user_name}\n"
            f"- Nivel Utilizator: {user_level_val} (XP: {user_xp_val})\n"
            f"- Preferințe & Interese Utilizator: {user_interests_val}\n"
            f"- Insigne deblocate Utilizator: {user_badges_val}\n"
            f"- Locuri deja vizitate (nu le recomanda): {visited_str}\n"
        )
        if visits_list:
            system_prompt += f"- Istoric vizite recente: {', '.join(visits_list)}\n"
        system_prompt += (
            f"\n{situr_block}"
            f"{nearby_block}\n"
            f"{events_block}\n"
            f"{social_trends_context}\n"
            f"RASPUNDE in română, natural si prietenos, ca un prieten care cunoaste bine orasul. "
            f"MAXIM 2 propozitii scurte (sub 30 cuvinte total). Fara bullet points, fara markdown.\n\n"
            f"{nav_instruction}\n\n"
            f"La final adauga exact: [SUGGESTIONS: sugestie1 | sugestie2 | sugestie3]"
        )

    # Try Gemini, if fails, use local model
    try:
        history = CHAT_HISTORIES.get(user_id, []) if user_id else []
        model_instance = genai.GenerativeModel(
            model_name="gemini-flash-latest",
            system_instruction=system_prompt,
            generation_config=genai.types.GenerationConfig(
                temperature=0.9,
                top_p=0.95,
                top_k=40,
                max_output_tokens=2048,
            )
        )
        chat = model_instance.start_chat(history=history)
        if language == "en":
            gemini_msg = f"{msg}\n(IMPORTANT: You MUST answer ONLY in English, even if the user message or conversation history is in Romanian. Do NOT use Romanian.)"
        else:
            gemini_msg = f"{msg}\n(IMPORTANT: Raspunde doar in limba romana.)"
        response = chat.send_message(gemini_msg)
        try:
            text = response.text
            # Debug: log finish reason
            if response.candidates:
                fr = response.candidates[0].finish_reason
                print(f"[Gemini] finish_reason={fr}, text_len={len(text)}")
        except Exception as e:
            print(f"[Gemini] response.text error: {e}")
            text = "".join(part.text for part in response.candidates[0].content.parts)
        
        # Save updated history
        if user_id:
            pruned_history = list(chat.history)
            if len(pruned_history) > 12:
                pruned_history = pruned_history[-12:]
            CHAT_HISTORIES[user_id] = pruned_history
        
        # Parse suggestions (More robust parsing)
        suggestions = []
        import re
        sug_match = re.search(r"\[SUGGESTIONS:(.*?)\]", text, re.DOTALL)
        if sug_match:
            raw_sug = sug_match.group(1).replace("]", "").strip()
            suggestions = [s.strip() for s in raw_sug.split("|") if s.strip()][:4]
            # Clean text from the tag
            text = re.sub(r"\[SUGGESTIONS:.*?\]", "", text, flags=re.DOTALL).strip()
        else:
            # Fallback suggestions if Gemini forgot the tag
            suggestions = ["📍 Ce e în apropiere?", "🍽️ Unde mănânc?", "🗺️ Planifică ziua", "✨ Recomandă ceva nou"]
        
        # Parse itinerary if generated dynamically
        itinerary_json = None
        iti_match = re.search(r"\[ITINERARY:(.*?)\]", text, re.DOTALL)
        if iti_match:
            itinerary_json = iti_match.group(1).strip()
            # Clean text from the tag
            text = re.sub(r"\[ITINERARY:.*?\]", "", text, flags=re.DOTALL).strip()
            
        return {
            "answer": text,
            "intent": "rag_gemini",
            "confidence": 1.0,
            "suggestions": suggestions,
            "itinerary_json": itinerary_json
        }
    except Exception as e:
        print(f"⚠️ RAG Gemini Error: {e}. Falling back to local model.")
        
        # LOCAL FALLBACK LOGIC (DistilBERT Classifier)
        if model is not None:
            top_tag, confidence, _ = _predict_intent(msg)
            if confidence > CONFIDENCE_THRESHOLD:
                # Dynamic SITUR matching to avoid hardcoding responses
                if top_tag.startswith("situr_") or "situr" in top_tag:
                    situr_results = search_situr_places(msg, limit=5)
                    if situr_results:
                        examples = []
                        for p in situr_results:
                            examples.append(f"• {p.get('name')} ({p.get('type')}) - {p.get('locality')}, Județul {p.get('county')} [Adresă: {p.get('address') or 'N/A'}]")
                        
                        if language == "ro":
                            response = f"Iată câteva dintre locurile oficiale din baza de date a Ministerului Turismului (SITUR) pentru tine:\n" + "\n".join(examples)
                        else:
                            response = f"Here are some official places from the Ministry of Tourism database (SITUR):\n" + "\n".join(examples)
                        
                        return {
                            "answer": response,
                            "intent": top_tag,
                            "confidence": round(confidence, 4),
                            "suggestions": [f"📍 {p.get('locality') or 'Înapoi'}" for p in situr_results[:3]]
                        }

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
        
        # If the local classifier fails or confidence is low, run a keyword-based SITUR backup search
        situr_results = search_situr_places(msg, limit=5)
        if situr_results:
            examples = []
            for p in situr_results:
                examples.append(f"• {p.get('name')} ({p.get('type')}) - {p.get('locality')}, Județul {p.get('county')} [Adresă: {p.get('address') or 'N/A'}]")
            
            if language == "ro":
                response = f"Nu am înțeles perfect, dar am găsit aceste localuri oficiale în baza de date a Ministerului Turismului:\n" + "\n".join(examples)
            else:
                response = f"I didn't fully understand, but I found these official places in the Ministry of Tourism database:\n" + "\n".join(examples)
            
            return {
                "answer": response,
                "intent": "situr_keyword_fallback",
                "confidence": 0.5,
                "suggestions": [f"📍 {p.get('locality') or 'Înapoi'}" for p in situr_results[:3]]
            }

        return {
            "answer": "Momentan întâmpin o mică dificultate tehnică, dar sunt aici să te ajut cu restul funcțiilor din București! 😊",
            "intent": "error_fallback",
            "suggestions": _get_fallback_suggestions(language)
        }

def generate_personalized_itinerary(lat, lng, style, duration, points_count, context, user_query, user_id=None, budget=250, travel_mode="walking", start_hour=8, companion="solo", avoid_crowds=False, language="ro"):
    """
    Generates a fully personalized itinerary using:
    - RAG: real user profile + visit history from Supabase
    - Real nearby places from Google Maps
    - Gemini for intelligent planning
    - Budget filtering + blacklisting bad place types
    """
    import requests, random, concurrent.futures
    from os import getenv
    MAPS_API_KEY = getenv("MAPS_API_KEY")

    FORBIDDEN_ITINERARY_WORDS = {
        "stație", "statie", "bus stop", "bus station", "gară", "gara",
        "metrou", "parcare", "parking", "benzinărie", "benzinarie",
        "strip", "erotic", "casino", "cazino", "pacanele", "păcănele",
        "superbet", "maxbet", "admiral", "winbet", "betano", "loto",
        "pompe funebre", "cimitir", "vulcanizare", "service auto",
    }
    FORBIDDEN_TYPES = {
        "bus_station", "transit_station", "subway_station", "train_station",
        "parking", "gas_station", "casino", "adult_entertainment",
        "funeral_home", "cemetery", "car_repair", "car_dealer",
        "real_estate_agency", "lawyer", "accounting", "insurance_agency",
        "doctor", "hospital", "dentist", "lodging",
    }

    def is_worthy(place):
        name = (place.get("name") or "").lower()
        types = place.get("types") or []
        rating = place.get("rating") or 0
        reviews = place.get("user_ratings_total") or 0
        if any(t in FORBIDDEN_TYPES for t in types):
            return False
        if any(w in name for w in FORBIDDEN_ITINERARY_WORDS):
            return False
        if reviews > 20 and rating < 3.5:
            return False
        return True

    def quick_search(p_type):
        url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
        params = {"location": f"{lat},{lng}", "radius": "8000", "type": p_type,
                  "key": MAPS_API_KEY, "language": language}
        try:
            return requests.get(url, params=params, timeout=8).json().get("results", [])
        except:
            return []

    # 1. RAG: fetch real user profile + visit history from Supabase
    user_rag = ""
    visited_place_names = []
    try:
        from app import SUPABASE_URL, SUPABASE_KEY
        headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
        if user_id:
            p_res = requests.get(
                f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{user_id}&select=*",
                headers=headers, timeout=3
            ).json()
            v_res = requests.get(
                f"{SUPABASE_URL}/rest/v1/visited_places?user_id=eq.{user_id}&order=visited_at.desc&limit=15",
                headers=headers, timeout=3
            ).json()
            fav_res = requests.get(
                f"{SUPABASE_URL}/rest/v1/bookmarks?user_id=eq.{user_id}&select=place_name,place_type&limit=10",
                headers=headers, timeout=3
            ).json()

            if p_res:
                u = p_res[0]
                interests = u.get("interests") or context.get("interests") or ("generale" if language == "ro" else "general")
                if language == "en":
                    user_rag = (
                        f"USER PROFILE (RAG):\n"
                        f"- Name: {u.get('name', 'Explorer')}\n"
                        f"- Level: {u.get('level', 1)} | XP: {u.get('total_xp', 0)}\n"
                        f"- Stated interests: {interests}\n"
                        f"- Badges: {u.get('badges', 'none')}\n"
                    )
                else:
                    user_rag = (
                        f"PROFIL UTILIZATOR (RAG):\n"
                        f"- Nume: {u.get('name', 'Explorator')}\n"
                        f"- Nivel: {u.get('level', 1)} | XP: {u.get('total_xp', 0)}\n"
                        f"- Interese declarate: {interests}\n"
                        f"- Insigne: {u.get('badges', 'niciuna')}\n"
                    )
            if v_res and isinstance(v_res, list):
                visited_place_names = [v.get("place_name") for v in v_res if v.get("place_name")]
                user_rag += f"- Recently visited places: {', '.join(visited_place_names[:10])}\n" if language == "en" else f"- Locuri vizitate recent: {', '.join(visited_place_names[:10])}\n"
            if fav_res and isinstance(fav_res, list):
                favs = [f.get("place_name") for f in fav_res if f.get("place_name")]
                if favs:
                    user_rag += f"- Bookmarked places: {', '.join(favs)}\n" if language == "en" else f"- Locuri favorite (bookmark): {', '.join(favs)}\n"
    except Exception as e:
        print(f"⚠️ RAG Supabase fetch error: {e}")

    if language == "en":
        companion_labels = {"solo": "solo traveler", "couple": "couple", "family": "family with kids", "friends": "group of friends"}
        user_rag += f"- Companions: {companion_labels.get(companion, companion)}\n"
        if avoid_crowds:
            user_rag += "- Prefers quieter, less crowded places\n"
    else:
        companion_labels = {"solo": "singur", "couple": "cuplu", "family": "familie cu copii", "friends": "grup de prieteni"}
        user_rag += f"- Companie: {companion_labels.get(companion, companion)}\n"
        if avoid_crowds:
            user_rag += "- Preferă locuri mai liniștite, fără aglomerație\n"

    # 2. Fetch real nearby candidates in parallel
    style_categories = {
        "cultural": ["museum", "art_gallery", "library", "tourist_attraction", "restaurant", "cafe"],
        "relax":    ["park", "spa", "cafe", "restaurant", "tourist_attraction"],
        "gastronomic": ["restaurant", "cafe", "bakery", "bar", "food"],
        "sport":    ["gym", "park", "stadium", "tourist_attraction", "restaurant"],
        "cinema":   ["movie_theater", "cafe", "restaurant", "bar"],
    }
    categories = next((v for k, v in style_categories.items() if k in style),
                      ["tourist_attraction", "museum", "park", "restaurant", "cafe", "art_gallery"])

    unique_candidates = []
    seen_ids = set()
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(categories)) as executor:
        futures = {executor.submit(quick_search, cat): cat for cat in categories}
        for future in concurrent.futures.as_completed(futures):
            for c in (future.result() or []):
                pid = c.get("place_id")
                if pid and pid not in seen_ids and is_worthy(c):
                    # Skip already visited (to keep itinerary fresh)
                    if c.get("name") not in visited_place_names:
                        unique_candidates.append(c)
                        seen_ids.add(pid)

    # Calculate distance for each candidate and sort them by distance (geographic clustering)
    from app import calculate_distance
    for c in unique_candidates:
        c_lat = c.get("geometry", {}).get("location", {}).get("lat", lat)
        c_lng = c.get("geometry", {}).get("location", {}).get("lng", lng)
        c["_dist"] = calculate_distance(lat, lng, c_lat, c_lng)
        
        # Estimate a realistic cost in RON based on type and price level
        p_type = (c.get("types") or ["place"])[0]
        price_level = c.get("price_level", 2)
        
        est_cost = 0
        if p_type in ["cafe", "bakery"]:
            est_cost = 20 if price_level <= 1 else (35 if price_level == 2 else 55)
        elif p_type in ["restaurant", "bar", "food"]:
            est_cost = 45 if price_level <= 1 else (75 if price_level == 2 else (130 if price_level == 3 else 220))
        elif p_type in ["museum", "art_gallery"]:
            est_cost = 20
        elif p_type in ["spa"]:
            est_cost = 150
        elif p_type in ["park"]:
            est_cost = 0
        else:
            est_cost = 30
            
        c["_est_cost"] = est_cost

    # Sort by distance so they are clustered together
    unique_candidates.sort(key=lambda x: x["_dist"])

    # Budget per slot estimate for Gemini context
    budget_per_slot = round(budget / max(points_count, 1))

    candidates_for_prompt = [
        {
            "name": c["name"],
            "type": (c.get("types") or ["place"])[0],
            "rating": c.get("rating", 0),
            "reviews": c.get("user_ratings_total", 0),
            "price_level": c.get("price_level", 2),
            "estimated_cost_ron": c["_est_cost"],
            "distance_from_start_km": round(c["_dist"], 1),
            "id": c["place_id"],
        }
        for c in unique_candidates[:35]
    ]

    # Build sequential schedule starting at 08:00
    # Label is determined by the actual hour of day, not index
    TRAVEL_MINS = 15

    def label_for_hour(h):
        if language == "en":
            if h < 9:    return "Breakfast"
            if h < 11:   return "Coffee & Walk"
            if h < 13:   return "Morning Activity"
            if h < 14:   return "Lunch"
            if h < 16:   return "Afternoon Activity"
            if h < 17:   return "Tea Time / Break"
            if h < 19:   return "Evening Activity"
            if h < 21:   return "Dinner"
            return "Nightlife"
        else:
            if h < 9:    return "Mic Dejun"
            if h < 11:   return "Cafea & Plimbare"
            if h < 13:   return "Activitate Dimineață"
            if h < 14:   return "Prânz"
            if h < 16:   return "Activitate După-Amiază"
            if h < 17:   return "Pauză / Ceai"
            if h < 19:   return "Activitate Seară"
            if h < 21:   return "Cină"
            return "Ieșire de Seară"

    def duration_for_label(lbl):
        return {
            "Breakfast": 60, "Mic Dejun": 60,
            "Coffee & Walk": 50, "Cafea & Plimbare": 50,
            "Morning Activity": 80, "Activitate Dimineață": 80,
            "Lunch": 70, "Prânz": 70,
            "Afternoon Activity": 80, "Activitate După-Amiază": 80,
            "Tea Time / Break": 40, "Pauză / Ceai": 40,
            "Evening Activity": 80, "Activitate Seară": 80,
            "Dinner": 75, "Cină": 75,
            "Nightlife": 90, "Ieșire de Seară": 90,
        }.get(lbl, 60)

    # Build schedule sequentially
    current_min = start_hour * 60
    slot_labels, slot_starts, slot_durations = [], [], []
    used_labels = set()
    for _ in range(points_count):
        lbl = label_for_hour(current_min // 60)
        # avoid duplicate labels — append number if needed
        if lbl in used_labels:
            lbl = f"{lbl} +"
        used_labels.add(lbl)
        dur = duration_for_label(lbl)
        slot_labels.append(lbl)
        slot_starts.append(current_min)
        slot_durations.append(dur)
        current_min += dur + TRAVEL_MINS

    # 4. Build Gemini prompt — Gemini only picks & orders places, NO times
    if language == "en":
        prompt = f"""You are CityScape AI, the ultimate urban exploration expert. Choose and order {points_count} places for a full day plan that best match the user profile.
        
{user_rag}
SPECIAL REQUEST: {user_query or "no special request"}
TRAVEL STYLE: {style}
TOTAL BUDGET: {budget} RON (~{budget_per_slot} RON/stop)
START COORDINATES: lat={lat}, lng={lng}

DAY SLOTS (in this exact order):
{chr(10).join(f"  {i+1}. {lbl}" for i, lbl in enumerate(slot_labels))}

REAL CANDIDATE PLACES (choose ONLY from this list, ordered by distance from start):
{json.dumps(candidates_for_prompt, ensure_ascii=False)}

RULES:
1. Return EXACTLY {points_count} items, in the exact order of the slots above.
2. Each item in the response must correspond to a real place from the candidate list above. Use the correct "id".
3. Choose places that are geographically close to each other to minimize travel time (preferably under 2-3 km between successive stops).
4. Respect the total budget of {budget} RON. The sum of "estimatedCost" of chosen places must not exceed the budget.
5. Personalize choices based on user interests and the special request.
6. For each place, write a short, useful practical tip/advice in the "tip" field (e.g. what to try there, what to order). Write it in English.

RESPOND EXCLUSIVELY with a valid JSON block, no other text or explanation:
[
  {{
    "slot": "Breakfast",
    "name": "Real name from list",
    "type": "type of place",
    "estimatedCost": 35,
    "address": "real address",
    "latitude": 44.xxx,
    "longitude": 26.xxx,
    "placeId": "ID from list",
    "tip": "Practical tip in English"
  }}
]"""
    else:
        prompt = f"""Ești CityScape AI, expertul în explorare urbană. Alege și ordonează {points_count} locuri pentru o zi completă care se potrivesc cel mai bine profilului utilizatorului.
        
{user_rag}
CERERE SPECIALĂ: {user_query or "nicio cerință specială"}
STIL CĂLĂTORIE: {style}
BUGET TOTAL: {budget} RON (~{budget_per_slot} RON/oprire)
COORDONATE PORNIRE: lat={lat}, lng={lng}

SLOTURILE ZILEI (în această ordine exactă):
{chr(10).join(f"  {i+1}. {lbl}" for i, lbl in enumerate(slot_labels))}

LOCURI REALE DISPONIBILE (alege DOAR din această listă, ordonate după distanța de la pornire):
{json.dumps(candidates_for_prompt, ensure_ascii=False)}

REGULI:
1. Returnează EXACT {points_count} obiecte, în ordinea sloturilor de mai sus.
2. Fiecare obiect din răspuns trebuie să corespundă unui loc din lista de mai sus. Folosește ID-ul corect.
3. Alege locuri care sunt geografic apropiate între ele pentru a minimiza timpul de deplasare (preferabil sub 2-3 km între opriri succesive).
4. Respectă bugetul total de {budget} RON. Suma costurilor estimate ale locurilor alese ("estimatedCost") nu trebuie să depășească bugetul.
5. Personalizează alegerea în funcție de interesele utilizatorului și cererea specială.
6. Pentru fiecare loc, scrie un sfat scurt și util în câmpul "tip" (ex: ce să încerce acolo, ce masă să rezerve).

RĂSPUNDE EXCLUSIV cu un bloc JSON valid, fără alte explicații sau text:
[
  {{
    "slot": "Mic Dejun",
    "name": "Nume real din listă",
    "type": "tipul locului",
    "estimatedCost": 35,
    "address": "adresa reală",
    "latitude": 44.xxx,
    "longitude": 26.xxx,
    "placeId": "ID-ul din listă",
    "tip": "Sfat practic în limba română"
  }}
]"""

    try:
        response = gemini_model.generate_content(prompt)
        text = response.text.strip()
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0].strip()
        elif "```" in text:
            text = text.split("```")[1].split("```")[0].strip()

        plan = json.loads(text)

        # Trim/pad plan to match exactly points_count
        plan = plan[:len(slot_labels)]

        candidates_map = {c["place_id"]: c for c in unique_candidates}

        # Calculate real travel time between consecutive stops using Haversine
        import math

        def haversine_minutes(lat1, lon1, lat2, lon2, mode="walking"):
            R = 6371
            dlat = math.radians(lat2 - lat1)
            dlon = math.radians(lon2 - lon1)
            a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1))*math.cos(math.radians(lat2))*math.sin(dlon/2)**2
            dist_km = R * 2 * math.asin(math.sqrt(a))
            speeds = {"walking": 5, "transit": 20, "driving": 40}
            speed = speeds.get(mode, 5)
            return max(5, int((dist_km / speed) * 60))

        def travel_label(mins, mode):
            icons = {"walking": "🚶", "transit": "🚌", "driving": "🚗"}
            icon = icons.get(mode, "🚶")
            return f"{icon} ~{mins} min"

        travel_mode_param = travel_mode  # passed in from caller

        current_min = slot_starts[0]
        for i, item in enumerate(plan):
            lbl = slot_labels[i]
            dur = slot_durations[i]

            item["slot"] = lbl
            start = current_min
            end = current_min + dur
            
            def format_ampm(total_min):
                h = (total_min // 60) % 24
                m = total_min % 60
                suffix = "AM" if h < 12 else "PM"
                display_h = h % 12
                if display_h == 0:
                    display_h = 12
                return f"{display_h:02d}:{m:02d} {suffix}"
                
            item["time"] = f"{format_ampm(start)} - {format_ampm(end)}"

            if i > 0:
                prev = plan[i - 1]
                try:
                    travel_mins = haversine_minutes(
                        float(prev.get("latitude", 0)), float(prev.get("longitude", 0)),
                        float(item.get("latitude", 0)), float(item.get("longitude", 0)),
                        travel_mode_param
                    )
                except:
                    travel_mins = TRAVEL_MINS
                item["travelMinutes"] = travel_mins
                item["travelLabel"] = travel_label(travel_mins, travel_mode_param)
                current_min = end + travel_mins
            else:
                item["travelMinutes"] = 0
                item["travelLabel"] = ""
                current_min = end + TRAVEL_MINS

            # Google Maps directions link from previous stop to this one
            if i == 0:
                item["mapsUrl"] = f"https://www.google.com/maps/search/?api=1&query={item.get('latitude')},{item.get('longitude')}"
            else:
                prev = plan[i - 1]
                item["mapsUrl"] = (
                    f"https://www.google.com/maps/dir/?api=1"
                    f"&origin={prev.get('latitude')},{prev.get('longitude')}"
                    f"&destination={item.get('latitude')},{item.get('longitude')}"
                    f"&travelmode=walking"
                )

            # Attach real photo and coordinates/address to ensure 100% geographic accuracy
            matched = candidates_map.get(item.get("placeId"))
            if not matched:
                matched = next((c for c in unique_candidates if c["name"] == item.get("name")), None)
            
            if matched:
                # Overwrite with 100% accurate coordinates & details from Google Places candidate
                item["latitude"] = matched.get("geometry", {}).get("location", {}).get("lat", item.get("latitude"))
                item["longitude"] = matched.get("geometry", {}).get("location", {}).get("lng", item.get("longitude"))
                item["address"] = matched.get("vicinity", item.get("address"))
                item["name"] = matched.get("name", item.get("name"))
                item["placeId"] = matched.get("place_id", item.get("placeId"))
                
                if matched.get("photos"):
                    ref = matched["photos"][0]["photo_reference"]
                    item["imageUrl"] = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference={ref}&key={MAPS_API_KEY}"
            
            if not item.get("imageUrl"):
                item["imageUrl"] = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800"
            item.setdefault("is_open", True)
            item.setdefault("tip", "")

        print(f"✅ Gemini personalized itinerary: {len(plan)} stops for user {user_id}")
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
