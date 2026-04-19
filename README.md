# CityScape - Platformă Inteligentă de Navigare și Explorare Urbană

CityScape este o aplicație mobilă modernă destinată explorării urbane, integrând inteligența artificială, realitatea augmentată și funcționalități sociale pentru a oferi o experiență premium turiștilor și localnicilor.

## 🚀 Funcționalități Principale

- **AI Chatbot (MysticMinds)**: Ghid virtual bazat pe modele DistilBERT și Gemini pentru recomandări personalizate de locații în București.
- **Social Feed**: O experiență de tip Instagram-like unde utilizatorii pot vedea recomandări, poze și pot interacționa (like/comment) cu postările prietenilor.
- **Itinerarii Inteligente**: Generare automată de planuri de zi în funcție de interese, buget și proximitate.
- **AR Explorer**: Vizualizarea punctelor de interes în Realitate Augmentată folosind camera dispozitivului.
- **Autentificare Hibridă**: Suport pentru autentificare clasică și Google Sign-In via Supabase.

---

## 🛠️ Ghid de Instalare și Rulare

### 1. Partea de Backend (Server Flask)
Backend-ul se ocupă de procesarea AI, integrarea cu Google Places și sincronizarea datelor sociale.

**Cerințe**: Python 3.9+
**Instalare dependențe**:
```bash
cd backend
pip install -r requirements.txt
```
**Rularea serverului**:
```bash
python3 app.py
```
*Notă: Serverul rulează implicit pe portul 5000. Asigură-te că IP-ul din `local.properties` (Android) se potrivește cu IP-ul laptopului tău.*

### 2. Partea de Mobile (Android Studio)
**Cerințe**: Android Studio Ladybug+, SDK 36.

1. Deschide proiectul în Android Studio.
2. Verifică fișierul `local.properties` să conțină:
   - `MAPS_API_KEY`: Cheia ta de Google Maps.
   - `FLASK_API_URL`: Adresa IP a serverului (ex: `http://192.168.1.132:5000/`).
3. Dă **Sync Project with Gradle Files**.
4. Rulează aplicația pe un emulator (recomandat API 34+) sau un telefon fizic.

---

## 👤 Conturi de Test (Pre-înregistrate)
Poți folosi butoanele de **"Quick Fill"** din ecranul de Login sau poți introduce manual:

- **Admin**: `admin@cityscape.app` / `Admin123!`
- **User**: `test@example.com` / `Password123!`
- **Mihaela**: `mihaela@licenta.ro` / `Mihaela2026!`

---

## 🏗️ Tehnologii folosite

- **Mobile**: Java, Room (DB locală), Retrofit, Glide, Material Design 3.
- **Backend**: Python, Flask, PyTorch (DistilBERT), Google Generative AI (Gemini Flash 2.0).
- **Cloud/Auth**: Supabase (PostgreSQL), Google Auth.
- **Locație/Hărți**: Google Places API, Google Maps SDK, Foursquare Places API.

---
*Proiect realizat de Mihaela Bogdan pentru examenul de Licență.*
