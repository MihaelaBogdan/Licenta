# 🚀 CityScape - Instrucțiuni Complete de Setup

## 📋 Cerințe Preliminare

### 1. **Software necesar**
- Java 21+ instalat
- Android SDK (Studio recomandat)
- Python 3.9+
- Node.js + npm (pentru ngrok)
- Git

### 2. **Conturi online**
- Supabase account cu proiect CityScape configurat
- Google Cloud Console cu Google Maps API
- Vercel account (optional, pentru deploy)

---

## 🔧 Setup Initial (Prima dată)

### Pasul 1: Clone repo-ul
```bash
cd ~/Desktop
git clone https://github.com/MihaelaBogdan/Licenta.git
cd Licenta
```

### Pasul 2: Configurează variabilele de mediu

#### Android App (`app/build.gradle`)
Verifică că sunt setate:
```
GOOGLE_API_KEY=<your-google-maps-api-key>
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_KEY=<your-supabase-anon-key>
FLASK_API_URL=http://10.0.2.2:5001/
```

#### Backend Flask (`.env`)
Creează/editeaza `backend/.env`:
```
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_KEY=<your-supabase-service-role-key>
FLASK_ENV=development
```

### Pasul 3: Setup venv Python
```bash
cd backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### Pasul 4: Configurează Supabase

Mergi la **https://app.supabase.com** → SQL Editor → Rulează:

```sql
ALTER TABLE feed_posts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow users to insert own posts" ON feed_posts;
DROP POLICY IF EXISTS "Allow insert feed posts" ON feed_posts;
DROP POLICY IF EXISTS "Allow select feed posts" ON feed_posts;
DROP POLICY IF EXISTS "Allow update own posts" ON feed_posts;
DROP POLICY IF EXISTS "Allow delete own posts" ON feed_posts;

CREATE POLICY "Allow insert feed posts" ON feed_posts
FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow select feed posts" ON feed_posts
FOR SELECT USING (true);

CREATE POLICY "Allow update own posts" ON feed_posts
FOR UPDATE USING (auth.uid()::text = user_id::text OR auth.uid() IS NULL)
WITH CHECK (auth.uid()::text = user_id::text OR auth.uid() IS NULL);

CREATE POLICY "Allow delete own posts" ON feed_posts
FOR DELETE USING (auth.uid()::text = user_id::text OR auth.uid() IS NULL);

GRANT ALL ON feed_posts TO authenticated;
GRANT ALL ON feed_posts TO anon;
GRANT ALL ON feed_posts TO service_role;
```

---

## ▶️ Rularea Aplicației

### Opțiunea 1: **Local (Recomandat pentru development)**

#### Terminal 1: Start Flask Server
```bash
cd ~/Desktop/Licenta/backend
source venv/bin/activate
python3 app.py
```
✅ Ar trebui să vezi: `Running on http://localhost:5001`

#### Terminal 2: Start Android Emulator
```bash
# În Android Studio: Tools → Device Manager → Launch emulator
# SAU din terminal:
emulator -avd <emulator-name>
```

#### Terminal 3: Build & Run App
```bash
cd ~/Desktop/Licenta
bash gradlew installDebug
# App se va instala pe emulator automat
```

### Opțiunea 2: **Cu ngrok (accesibil din afară)**

#### Terminal 1: Flask + ngrok
```bash
cd ~/Desktop/Licenta/backend
source venv/bin/activate
python3 start_with_ngrok.sh  # (pe Mac/Linux)
```
Vei vedea URL-ul: `https://xxxx-xx-xxx-xxx-xx.ngrok.io`

Apoi setează în `app/build.gradle`:
```
FLASK_API_URL="https://xxxx-xx-xxx-xxx-xx.ngrok.io/"
```

#### Terminal 2: Rebuild app
```bash
cd ~/Desktop/Licenta
bash gradlew clean assembleDebug
bash gradlew installDebug
```

---

## 🧪 Testare

### 1. **Test Login**
- Apasă "Login"
- Email: `test@example.com`
- Parola: `Test123`

### 2. **Test Postare**
- Mergi pe Feed
- Apasă "+" pentru a crea post
- Scrie ceva
- Apasă "Post"
✅ Postarea ar trebui să apară instant în feed

### 3. **Test Raportare**
- Apasă "..." pe orice post
- Selectează "Raportează"
- Alege motiv
✅ Nu ar trebui să dea eroare de sesiune

### 4. **Test Email Confirmation**
- Fă register cu email nou
- Mergi la inbox Gmail
- Click link de confirmare
✅ Ar trebui redirecționat la login

---

## 🐛 Debugging

### Flask server nu pornește?
```bash
# Verifică port 5001
lsof -i :5001
# Kill procesul vechi
pkill -f "python3 app.py"
```

### App nu se conectează la server?
```bash
# Verifică adresa IP
ping 10.0.2.2  # (din emulator)
curl http://localhost:5001/health
```

### Postarile nu se salvează?
- Rulează SQL script din Supabase ✅
- Verifica `feed_posts` RLS policies
- Check `backend/server.log` pentru erori

### "No such method" error?
```bash
bash gradlew clean
bash gradlew assembleDebug
```

---

## 📱 Deployment

### Vercel (Backend)
```bash
cd backend
vercel deploy
```

### Google Play (App)
```bash
bash gradlew bundleRelease
# Upload în Play Console
```

---

## ✅ Checklist Setup

- [ ] Java 21+ instalat
- [ ] Android SDK setup
- [ ] Python venv activat
- [ ] `.env` file configurat
- [ ] Supabase RLS script rulat
- [ ] Google Maps API key valid
- [ ] ngrok instalat (optional)
- [ ] Emulator running
- [ ] `bash gradlew assembleDebug` success
- [ ] Flask server running pe localhost:5001
- [ ] App instalat pe emulator
- [ ] Login funcționează

---

## 🎯 Quick Start Command
```bash
# Terminal 1
cd ~/Desktop/Licenta/backend && source venv/bin/activate && python3 app.py

# Terminal 2
cd ~/Desktop/Licenta && bash gradlew installDebug

# Emulator deja pornit
```

**Gata! 🚀 App-ul ar trebui să funcționeze!**
