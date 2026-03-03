from flask import Flask, render_template, request, jsonify
from flask_cors import CORS
from chatbot import get_response, get_response_with_details
import json

app = Flask(__name__)
CORS(app)

@app.route("/")
def index_get():
    return render_template("base.html")

@app.post("/predict")
def predict():
    """Simple endpoint - returns just the answer text. Supports language parameter."""
    data = request.get_json()
    text = data.get("message")
    language = data.get("language", "ro")  # Default to Romanian
    response = get_response(text, language)
    message = {"answer": response}
    return jsonify(message)

@app.post("/predict/detailed")
def predict_detailed():
    """
    Full endpoint - returns answer, intent, confidence, and suggestions.
    Used by the Android app for conversational flow with quick replies.
    Supports language parameter for bilingual responses.
    
    Response format:
    {
        "answer": "Hai să-ți planificăm ziua! 🌟 Ce ai chef să faci?",
        "intent": "what_to_do",
        "confidence": 0.95,
        "suggestions": ["🍽️ Să mănânc ceva", "🌃 Să ies în oraș", ...]
    }
    """
    data = request.get_json()
    text = data.get("message")
    language = data.get("language", "ro")  # Default to Romanian
    result = get_response_with_details(text, language)
    return jsonify(result)

# Additional endpoint to provide places data to Android app
@app.get("/places")
def get_places():
    with open('data/places.json', 'r') as f:
        places_data = json.load(f)
    return jsonify(places_data)

if __name__ == "__main__":
    app.run(host='0.0.0.0', port=5000, debug=True)
