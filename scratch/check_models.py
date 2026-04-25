import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()
genai.configure(api_key=os.getenv("GOOGLE_API_KEY"))

try:
    print("Listing models...")
    models = genai.list_models()
    count = 0
    for m in models:
        if 'generateContent' in m.supported_generation_methods:
            print(f" - {m.name}")
            count += 1
    if count == 0:
        print("No models found with 'generateContent' support.")
except Exception as e:
    print(f"Error listing models: {e}")
