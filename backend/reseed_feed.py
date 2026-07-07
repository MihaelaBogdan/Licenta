# -*- coding: utf-8 -*-
"""
Sterge toate postarile din feed si insereaza cate o postare reala
(locuri din Bucuresti, poze reale de pe Unsplash) pentru fiecare
utilizator din user_profiles. Adauga si like-uri ca sa arate viu.

Rulare:  python3 reseed_feed.py
"""
import requests
import os
import random
from datetime import datetime, timedelta, timezone
from dotenv import load_dotenv

backend_dir = os.path.dirname(os.path.abspath(__file__))
load_dotenv(os.path.join(backend_dir, '.env'))

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")

if not SUPABASE_URL or not SUPABASE_KEY:
    print("❌ SUPABASE_URL sau SUPABASE_KEY lipsesc din .env")
    exit(1)

headers = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Content-Type": "application/json",
}

# Locuri reale din Bucuresti: nume, coordonate, poza reala, caption, rating
PLACES = [
    ("Caru' cu Bere", 44.4323, 26.0984,
     "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800&q=80",
     "Cea mai bună mâncare tradițională din București! 🍺🥘 #Tradiție", 4.9),
    ("Cărturești Carusel", 44.4319, 26.1023,
     "https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?w=800&q=80",
     "O după-amiază liniștită între cărți. Arhitectura e superbă! 📚✨", 4.8),
    ("Parcul Herăstrău", 44.4716, 26.0824,
     "https://images.unsplash.com/photo-1519331379826-f10be5486c6f?w=800&q=80",
     "Plimbare pe malul lacului, aer curat și liniște. 🌸🚣", 4.6),
    ("Palatul Parlamentului", 44.4275, 26.0877,
     "https://images.unsplash.com/photo-1588668214407-6ea9a6d8c272?w=800&q=80",
     "Impresionant! Merită vizitat măcar o dată în viață. 🏛️🇷🇴", 4.4),
    ("Ateneul Român", 44.4413, 26.0973,
     "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=800&q=80",
     "Concert superb aseară — acustica sălii e de alt nivel. 🎻", 4.9),
    ("Centrul Vechi", 44.4301, 26.1027,
     "https://images.unsplash.com/photo-1514933651103-005eec06c04b?w=800&q=80",
     "Seară perfectă pe Lipscani, atmosfera e mereu vie aici. 🌃", 4.5),
    ("Grădina Botanică", 44.4372, 26.0625,
     "https://images.unsplash.com/photo-1585320806297-9794b3e4eeae?w=800&q=80",
     "Colț de liniște în mijlocul orașului. Perfect duminica. 🌿", 4.6),
    ("Therme București", 44.6086, 26.0754,
     "https://images.unsplash.com/photo-1571902943202-507ec2618ed8?w=800&q=80",
     "Relaxare totală — cea mai bună idee pentru o zi ploioasă. 💦🌴", 4.7),
    ("Muzeul Satului", 44.4770, 26.0770,
     "https://images.unsplash.com/photo-1449158743715-0a90ebb6d2d8?w=800&q=80",
     "O plimbare prin România de altădată, chiar lângă Herăstrău. 🏡", 4.7),
    ("Arcul de Triumf", 44.4672, 26.0782,
     "https://images.unsplash.com/photo-1507041957456-9c397ce39c97?w=800&q=80",
     "Toamna pe Kiseleff arată incredibil. 🍂", 4.5),
    ("Parcul Cișmigiu", 44.4362, 26.0906,
     "https://images.unsplash.com/photo-1476820865390-c52aeebb9891?w=800&q=80",
     "Cel mai vechi parc din oraș rămâne cel mai romantic. 🦢", 4.6),
    ("Hanul lui Manuc", 44.4295, 26.1030,
     "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800&q=80",
     "Brunch în cel mai vechi han din București. Recomand! 🥐☕", 4.7),
]

def main():
    # 1. Utilizatorii din baza de date
    res = requests.get(f"{SUPABASE_URL}/rest/v1/user_profiles?select=id,name&order=name", headers=headers)
    if res.status_code != 200:
        print(f"❌ Nu pot citi user_profiles: {res.status_code} {res.text}")
        return
    users = [u for u in res.json() if u.get("id")]
    if not users:
        print("❌ Niciun utilizator în user_profiles.")
        return
    print(f"👥 {len(users)} utilizatori găsiți.")

    # 2. Sterge toate postarile (like-urile si comentariile se sterg in cascada)
    d = requests.delete(f"{SUPABASE_URL}/rest/v1/feed_posts?id=not.is.null",
                        headers={**headers, "Prefer": "return=minimal"})
    if d.status_code in (200, 204):
        print("🗑️  Postările vechi au fost șterse.")
    else:
        print(f"⚠️ Ștergere: {d.status_code} {d.text}")

    # 3. Cate o postare per utilizator, cu ore diferite (cele mai noi primele)
    random.seed(7)
    now = datetime.now(timezone.utc)
    created = 0
    post_ids = []
    for i, user in enumerate(users):
        name, lat, lng, img, caption, rating = PLACES[i % len(PLACES)]
        post = {
            "user_id": user["id"],
            "user_name": user.get("name") or "Explorer",
            "place_name": name,
            "image_url": img,
            "caption": caption,
            "rating": rating,
            "latitude": lat,
            "longitude": lng,
            "created_at": (now - timedelta(hours=3 + i * 9, minutes=random.randint(0, 55))).isoformat(),
        }
        r = requests.post(f"{SUPABASE_URL}/rest/v1/feed_posts",
                          headers={**headers, "Prefer": "return=representation"}, json=post)
        if r.status_code in (200, 201):
            pid = r.json()[0]["id"]
            post_ids.append(pid)
            created += 1
            print(f"  ✅ {post['user_name']} → {name}")
        else:
            print(f"  ⚠️ {post['user_name']}: {r.status_code} {r.text[:120]}")

    # 4. Like-uri de la ceilalti utilizatori, ca feed-ul sa arate viu
    likes = 0
    for pid in post_ids:
        for u in random.sample(users, min(len(users), random.randint(2, min(8, len(users))))):
            lr = requests.post(f"{SUPABASE_URL}/rest/v1/feed_likes",
                               headers={**headers, "Prefer": "return=minimal"},
                               json={"post_id": pid, "user_id": u["id"]})
            if lr.status_code in (200, 201, 204):
                likes += 1

    print(f"\n🎉 Gata: {created} postări create, {likes} like-uri adăugate.")

if __name__ == "__main__":
    main()
