"""
Adaugă prieteni pentru mihaelabogdan100@gmail.com.
Rulează DUPĂ ce ai creat tabela user_follows în Supabase SQL Editor.
"""
import os, requests
from dotenv import load_dotenv

load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL")
SERVICE_KEY  = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

headers = {
    "apikey": SERVICE_KEY,
    "Authorization": f"Bearer {SERVICE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "resolution=ignore-duplicates",
}

MY_ID       = "8e875b0f-87f3-4e1a-bd08-1c506716f9cf"  # mihaelabogdan100@gmail.com
ALEXANDRU   = "287969aa-3a54-48a1-a38e-3eca6e032c62"
MARIA       = "ff1edc86-4b5c-4cb6-96c9-d2062e99a1e1"
ANDREI      = "3eae54d9-a565-4bd2-bef6-d6d5125601fd"
ELENA       = "ae46985a-f6a0-4bb3-9af0-4b8e05a4b403"
MIHAI       = "89fa7e32-a622-484b-be82-bbf71cce6f74"
MIHAELA2    = "da789843-6486-4a90-afba-c690c744d2a8"
DAN         = "2b4de16a-4dce-4b78-a760-bff6e83c9000"

follows = [
    (MY_ID, ALEXANDRU, "Mihaela → Alexandru Ionescu"),
    (MY_ID, MARIA,     "Mihaela → Maria Popescu"),
    (MY_ID, ANDREI,    "Mihaela → Andrei Constantin"),
    (MY_ID, ELENA,     "Mihaela → Elena Dumitrescu"),
    (MY_ID, MIHAI,     "Mihaela → Mihai Stanescu"),
    (MY_ID, MIHAELA2,  "Mihaela → Mihaela (mihaelaaa)"),
    (MY_ID, DAN,       "Mihaela → Dan Bogdan"),
    (ALEXANDRU, MY_ID, "Alexandru → Mihaela (mutual)"),
    (MARIA,     MY_ID, "Maria → Mihaela (mutual)"),
    (ANDREI,    MY_ID, "Andrei → Mihaela (mutual)"),
    (ELENA,     MY_ID, "Elena → Mihaela (mutual)"),
    (MIHAI,     MY_ID, "Mihai → Mihaela (mutual)"),
    (MIHAELA2,  MY_ID, "Mihaela2 → Mihaela (mutual)"),
    (DAN,       MY_ID, "Dan → Mihaela (mutual)"),
    (ALEXANDRU, MARIA,  "Alexandru → Maria"),
    (MARIA,  ANDREI,    "Maria → Andrei"),
    (ANDREI, MIHAI,     "Andrei → Mihai"),
    (MIHAI,  ALEXANDRU, "Mihai → Alexandru"),
    (ELENA,  MARIA,     "Elena → Maria"),
    (DAN,    ALEXANDRU, "Dan → Alexandru"),
]

url = f"{SUPABASE_URL}/rest/v1/user_follows"
ok = 0
for follower_id, following_id, label in follows:
    r = requests.post(url, headers=headers, json={
        "follower_id": follower_id,
        "following_id": following_id,
    })
    status = "✅" if r.status_code in (200, 201) else f"❌ {r.status_code}: {r.text[:80]}"
    print(f"{status}  {label}")
    if r.status_code in (200, 201):
        ok += 1

print(f"\nGata: {ok}/{len(follows)} relații adăugate.")
