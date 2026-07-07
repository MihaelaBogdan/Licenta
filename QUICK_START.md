# ⚡ Quick Start - Rulează App-ul în 3 pași

## 🚀 Fiecare zi cand vrei sa rulezi:

### Terminal 1: Start Backend (Flask Server)
```bash
cd ~/Desktop/Licenta/backend
source venv/bin/activate
python3 app.py
```
✅ Astepta: `Running on http://localhost:5001`

---

### Terminal 2: Emulator + App
```bash
# 1. Start emulator (daca nu e deja pornit)
emulator -avd Pixel_7_Pro  # Inlocuieste cu al tau

# 2. Build & run app
cd ~/Desktop/Licenta
bash gradlew installDebug
```

✅ App-ul se va deschide automat pe emulator

---

## 📝 DE FACUT ODATA (setup initial)

### 1. Python venv setup
```bash
cd ~/Desktop/Licenta/backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### 2. Supabase SQL (uma singura data!)
Mergi la https://app.supabase.com → SQL Editor si copiaza-paste:
[Vaza SETUP_INSTRUCTIONS.md pentru SQL script complet]

### 3. Verifica .env file
```bash
# backend/.env trebuie sa aiba:
SUPABASE_URL=https://...
SUPABASE_KEY=...
FLASK_ENV=development
```

---

## 🧪 Test Rapid

```bash
# Verifica server
curl http://localhost:5001/health

# Verifica emulator conectare
ping 10.0.2.2
```

---

## 🐛 Daca ceva nu merge

```bash
# Clean build
bash gradlew clean assembleDebug

# Kill port 5001
pkill -f "python3 app.py"

# Restart everything
```

---

## ✅ Checklist Daily

- [ ] `python3 app.py` running in Terminal 1
- [ ] Emulator running
- [ ] `bash gradlew installDebug` success
- [ ] App opens on emulator
- [ ] Can login
- [ ] Can post
- [ ] Can report (no session error!)

**Enjoy! 🎉**
