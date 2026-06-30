#!/usr/bin/env python3
"""
Fallback Database Generator - Creates local places data as fallback for Google API
"""

import json
import time
from datetime import datetime

# Coordonatele pentru orașe principale
CITIES = {
    "București": {"lat": 44.4268, "lng": 26.1025},
    "Cluj-Napoca": {"lat": 46.7712, "lng": 23.5897},
    "Iași": {"lat": 47.1585, "lng": 27.6014},
    "Timișoara": {"lat": 45.7489, "lng": 21.2087},
}

FALLBACK_PLACES = {
    "București": [
        {
            "name": "Piața Constituției",
            "lat": 44.4272,
            "lng": 26.0915,
            "rating": 4.5,
            "reviews": 22748,
            "address": "Piața Constituției, București",
            "type": "tourist_attraction",
            "photo": "https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=800"
        },
        {
            "name": "Grădina Cișmigiu",
            "lat": 44.4373,
            "lng": 26.0910,
            "rating": 4.4,
            "reviews": 41410,
            "address": "Bulevardul Regina Elisabeta, București",
            "type": "park",
            "photo": "https://images.unsplash.com/photo-1511632765486-a01980e01a18?w=800"
        },
        {
            "name": "Mănăstirea Stavropoleos",
            "lat": 44.4265,
            "lng": 26.1035,
            "rating": 4.6,
            "reviews": 3821,
            "address": "Str. Stavropoleos, București",
            "type": "church",
            "photo": "https://images.unsplash.com/photo-1518156677180-95a2893f3e9f?w=800"
        },
        {
            "name": "Parcul Herăstrău",
            "lat": 44.4760,
            "lng": 26.0850,
            "rating": 4.6,
            "reviews": 18956,
            "address": "Strada Gheorghe Manu, București",
            "type": "park",
            "photo": "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800"
        },
        {
            "name": "Curtea Veche",
            "lat": 44.4270,
            "lng": 26.1090,
            "rating": 4.5,
            "reviews": 12534,
            "address": "Str. Franceza, București",
            "type": "historical_site",
            "photo": "https://images.unsplash.com/photo-1509023016485-f8ccf86f9d5f?w=800"
        },
        {
            "name": "Palatul Parlamentului",
            "lat": 44.4267,
            "lng": 26.0893,
            "rating": 4.4,
            "reviews": 15687,
            "address": "Calea 13 Septembrie, București",
            "type": "government_building",
            "photo": "https://images.unsplash.com/photo-1511649475669-e293d3f3f1e5?w=800"
        },
        {
            "name": "Museum of Senses",
            "lat": 44.4313,
            "lng": 26.0534,
            "rating": 4.4,
            "reviews": 10714,
            "address": "Bulevardul General Paul Teodorescu, București",
            "type": "museum",
            "photo": "https://images.unsplash.com/photo-1578926078328-123456789012?w=800"
        },
        {
            "name": "Grădina Botanică",
            "lat": 44.4372,
            "lng": 26.0626,
            "rating": 4.3,
            "reviews": 15862,
            "address": "Șoseaua Cotroceni, București",
            "type": "park",
            "photo": "https://images.unsplash.com/photo-1469022563149-aa64dbd37d2f?w=800"
        },
        {
            "name": "Obor Mall",
            "lat": 44.4145,
            "lng": 26.1195,
            "rating": 4.2,
            "reviews": 8945,
            "address": "Piața Obor, București",
            "type": "shopping_mall",
            "photo": "https://images.unsplash.com/photo-1555529669-e69e7f1cfd88?w=800"
        },
        {
            "name": "Arcul de Triumf",
            "lat": 44.4723,
            "lng": 26.0843,
            "rating": 4.5,
            "reviews": 19234,
            "address": "Calea Victoriei, București",
            "type": "monument",
            "photo": "https://images.unsplash.com/photo-1464099677214-c97a5c1cdeab?w=800"
        },
        {
            "name": "Cafeneaua Capșa",
            "lat": 44.4383,
            "lng": 26.1037,
            "rating": 4.3,
            "reviews": 2156,
            "address": "Calea Victoriei, București",
            "type": "cafe",
            "photo": "https://images.unsplash.com/photo-1495521821757-a1efb6729352?w=800"
        },
        {
            "name": "Restaurant Crama Domnească",
            "lat": 44.4268,
            "lng": 26.1050,
            "rating": 4.4,
            "reviews": 3245,
            "address": "Str. Lipscani, București",
            "type": "restaurant",
            "photo": "https://images.unsplash.com/photo-1504674900152-b8f579deda90?w=800"
        }
    ],
    "Cluj-Napoca": [
        {
            "name": "Piața Unirii",
            "lat": 46.7712,
            "lng": 23.5897,
            "rating": 4.5,
            "reviews": 12450,
            "address": "Piața Unirii, Cluj-Napoca",
            "type": "tourist_attraction",
            "photo": "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800"
        },
        {
            "name": "Grădina Botanică",
            "lat": 46.7680,
            "lng": 23.5920,
            "rating": 4.3,
            "reviews": 8934,
            "address": "Str. Republicii, Cluj-Napoca",
            "type": "park",
            "photo": "https://images.unsplash.com/photo-1469022563149-aa64dbd37d2f?w=800"
        }
    ],
    "Iași": [
        {
            "name": "Palatul Culturii",
            "lat": 47.1620,
            "lng": 27.5950,
            "rating": 4.4,
            "reviews": 9876,
            "address": "Piața Unirii, Iași",
            "type": "museum",
            "photo": "https://images.unsplash.com/photo-1511635542662-31b50c6a9345?w=800"
        }
    ]
}

def save_fallback_database():
    """Salvează datele în fișier JSON"""
    with open("fallback_places.json", "w", encoding="utf-8") as f:
        json.dump({
            "timestamp": datetime.now().isoformat(),
            "places": FALLBACK_PLACES
        }, f, ensure_ascii=False, indent=2)
    print("✅ Fallback database salvat")

def update_fallback_database():
    """Generează fallback database local"""
    print("💾 Generez fallback database...")
    print("")

    for city in CITIES.keys():
        print(f"✅ {city}: {len(FALLBACK_PLACES.get(city, []))} locuri")

    print("")
    save_fallback_database()
    print("✅ Fallback database gata!")

if __name__ == "__main__":
    update_fallback_database()
