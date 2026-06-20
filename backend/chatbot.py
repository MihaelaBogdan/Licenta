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
    system_prompt = (
        "Ești 'CityScape AI', un ghid urban inteligent și rapid. "
        f"Limba: {'română' if language == 'ro' else 'engleză'}. "
        "STIL: Răspunde scurt (max 2 fraze), direct. "
        "IMPORTANT: Respectă locația cerută de utilizator. Dacă întreabă de Brașov, oferă recomandări din Brașov, chiar dacă el este în alt oraș. "
        "OBLIGATORIU la final: [SUGGESTIONS: S1 | S2 | S3] unde propui acțiuni LOGICE."
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


def get_response_with_rag(msg, user_id=None, lat=None, lng=None, language="ro", city_name=None, interests=None, user_xp=None, user_level=None, places_visited=None):
    if not msg or not msg.strip():
        return {"answer": _get_empty_message(language), "intent": "empty", "suggestions": _get_fallback_suggestions(language)}

    user_context = ""
    if interests or user_xp or user_level or places_visited:
        user_context = (
            f"PROFIL UTILIZATOR (Offline/Local Cache):\n"
            f"- Preferințe & Interese: {interests or 'Generale'}\n"
            f"- Nivel curent: {user_level or 1}\n"
            f"- Experiență totală (XP): {user_xp or 0} XP\n"
            f"- Total locații vizitate: {places_visited or 0}\n\n"
        )
    nearby_context = ""
    detected_city = city_name or "un oraș nespecificat"

   
    if user_id:
        try:
            from app import SUPABASE_URL, SUPABASE_KEY
            import requests
            headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
            profile_url = f"{SUPABASE_URL}/rest/v1/user_profiles?id=eq.{user_id}&select=*"
            p_res = requests.get(profile_url, headers=headers, timeout=2).json()
            visits_url = f"{SUPABASE_URL}/rest/v1/visited_places?user_id=eq.{user_id}&order=visited_at.desc&limit=5"
            v_res = requests.get(visits_url, headers=headers, timeout=2).json()
            
            if p_res:
                u = p_res[0]
                name = u.get("name", "Explorator CityScape")
                final_interests = u.get("interests") or interests or "Generale (fără preferințe salvate încă)"
                xp = u.get("total_xp") or user_xp or 0
                lvl = u.get("level") or user_level or 1
                badges = u.get("badges", "Nicio insignă momentan")
                
                user_context = (
                    f"PROFIL UTILIZATOR:\n"
                    f"- Nume: {name}\n"
                    f"- Nivel curent: {lvl}\n"
                    f"- Preferințe & Interese: {final_interests}\n"
                    f"- Experiență (XP): {xp} XP\n"
                    f"- Insigne deblocate: {badges}\n"
                )
                
                if v_res and isinstance(v_res, list):
                    visits_list = [f"{v.get('place_name')} ({v.get('place_type', 'Atracție')})" for v in v_res if v.get('place_name')]
                    if visits_list:
                        user_context += f"- Istoric vizite recente (reale): {', '.join(visits_list)}\n"
        except Exception as e:
            print(f"⚠️ RAG Supabase Context Fetch Error: {e}")
            pass

    # 2. Place Search
    if lat and lng:
        try:
            from app import MAPS_API_KEY, google_nearby_search
            near_results = google_nearby_search(lat, lng, "tourist_attraction", radius=10000)
            if near_results:
                nearby_context = "Puncte de interes reale din apropiere: " + ", ".join([r['name'] for r in near_results[:10]])
        except: pass

    system_prompt = (
        f"Ești 'CityScape AI', un asistent urban inteligent și ghid turistic de top, dezvoltat exclusiv pentru platforma CityScape.\n\n"
        f"CONTEXT DESPRE UTILIZATORUL CURENT:\n{user_context}\n"
        f"LOCAȚIE & ZONĂ:\n"
        f"- Oraș detectat: {detected_city}\n"
        f"- Puncte de interes reale: {nearby_context}\n\n"
        f"INSTRUCȚIUNI DE CONVERSAȚIE:\n"
        f"1. Răspunde exclusiv în limba {'română' if language == 'ro' else 'engleză'}.\n"
        f"2. Adresează-te utilizatorului pe nume dacă îl cunoști din contextul de mai sus. Fii prietenos, deschis, cald și folosește un ton primitor de partener de explorare.\n"
        f"3. Cunoști tot istoricul lui (locațiile vizitate deja, interesele lui, XP-ul lui și insignele obținute). Folosește-te inteligent de aceste date pentru a-i face recomandări ultra-personalizate (de exemplu, dacă îi place arta, trimite-l la expoziții/muzee; dacă a vizitat deja un loc, recunoaște asta și felicită-l sau sugerează-i ceva similar dar nou!).\n"
        f"4. Proune doar locații/puncte de interes REALE (dacă ai în contextul local, folosește-le cu încredere).\n"
        f"5. Păstrează istoricul conversației (conversational memory) activ pe tot parcursul sesiunii, astfel încât utilizatorul să poată purta o discuție fluidă, continuă și naturală cu tine (de genul: 'Recomandă-mi un restaurant' urmat de 'Cât costă?' sau 'Ce pot mânca acolo?').\n"
        f"6. STIL: Răspunsurile tale trebuie să fie concise, calde și atractive (maxim 3 fraze). Evită codul Markdown încărcat.\n"
        f"7. GENERARE TRASEE: Dacă utilizatorul îți cere explicit un traseu, plan, itinerariu sau succesiune de locuri de vizitat (ex: 'Fă-mi un traseu de 4 ore' sau 'Planifică-mi un itinerariu de artă'), generează obligatoriu la sfârșitul răspunsului tău un bloc XML compact [ITINERARY: <JSON>] unde <JSON> este un șir valid JSON compact (fără linii noi sau backticks) reprezentând o listă de maxim 3 locații reale în formatul exact: "
        f'[{{\"slot\": \"Activitate\", \"name\": \"Nume Loc\", \"type\": \"Atracție\", \"time\": \"09:00 - 10:30\", \"estimatedCost\": 50, \"address\": \"Adresă\", \"latitude\": 44.43, \"longitude\": 26.01, \"placeId\": \"id1\", \"travelToNext\": \"10 min mers\", \"proTip\": \"Sfat\"}}]. Folosește locurile REALE din contextul de mai sus dacă se potrivesc.\n\n'
        f"OBLIGATORIU la sfârșitul fiecărui răspuns (sugestii rapide de răspuns logic): [SUGGESTIONS: S1 | S2 | S3]"
    )

    # Try Gemini, if fails, use local model
    try:
        history = CHAT_HISTORIES.get(user_id, []) if user_id else []
        model_instance = genai.GenerativeModel(
            model_name="gemini-flash-latest",
            system_instruction=system_prompt
        )
        chat = model_instance.start_chat(history=history)
        response = chat.send_message(msg)
        text = response.text
        
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
        
        # LOCAL FALLBACK LOGIC (DistilBERT)
        if model is not None:
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
    import concurrent.futures

    with concurrent.futures.ThreadPoolExecutor(max_workers=len(categories)) as executor:
        future_to_cat = {executor.submit(quick_search, lat, lng, cat): cat for cat in categories}
        for future in concurrent.futures.as_completed(future_to_cat):
            cat = future_to_cat[future]
            try:
                results = future.result()
                if results:
                    random.shuffle(results) # Randomize before picking top candidates
                    for c in results[:8]:
                        if c.get('place_id') and c['place_id'] not in seen_ids:
                            unique_candidates.append(c)
                            seen_ids.add(c['place_id'])
            except Exception as e:
                print(f"Error quick_searching cat {cat}: {e}")
    
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
