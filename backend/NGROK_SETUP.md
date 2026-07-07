# CityScape Flask + ngrok Setup

Rulează serverul Flask local și expune-l public cu ngrok - fără deploy!

## Setup (o singură dată)

### 1. Instalează ngrok
```bash
npm install -g ngrok
```

### 2. Crează cont ngrok (gratuit)
- Mergi pe https://ngrok.com
- Crează cont
- Copiază authtoken din dashboard

### 3. Autentifică-te
```bash
ngrok config add-authtoken YOUR_TOKEN_HERE
```

### 4. Instalează dependencies Flask
```bash
cd /Users/mihaela/Desktop/Licenta/backend
pip install -r requirements.txt
```

## Pornire

### Opțiunea 1: Python (Cross-platform - Recommended ✅)
```bash
cd /Users/mihaela/Desktop/Licenta/backend
python3 start_with_ngrok.py
```

### Opțiunea 2: Bash (Mac/Linux)
```bash
cd /Users/mihaela/Desktop/Licenta/backend
./start_with_ngrok.sh
```

### Opțiunea 3: Manual
Terminal 1:
```bash
python3 app.py
```

Terminal 2:
```bash
ngrok http 5001
```

## Output

Vei vedea ceva de genul:
```
==================================================
✅ Servers started successfully!
==================================================
🔗 Public URL: https://abc123def456.ngrok.io
🖥️  Local URL: http://localhost:5001
==================================================

📝 Update email confirmation page with:
const backendUrl = 'https://abc123def456.ngrok.io';
```

## Integrare cu Email Confirmation

Actualizeaza pagina de confirmare (email_confirm.html):

```javascript
const backendUrl = 'https://abc123def456.ngrok.io'; // <- Pune URL-ul din output
```

## Endpoints disponibili

```
GET  https://abc123def456.ngrok.io/health
POST https://abc123def456.ngrok.io/api/auth/confirm-email
```

## Important ⚠️

- **URL schimbă de fiecare dată când restartes!**
- Versiunea free ngrok nu păstrează URL-ul
- Pentru URL static, upgrade la plan paid
- Serverul ruleaza doar cât timp scriptul e activ

## Debugging

Dacă nu merge:

1. Verifica dacă port 5001 e liber:
   ```bash
   lsof -i :5001
   ```

2. Verifica ngrok logs:
   ```bash
   ngrok api endpoints http
   ```

3. Test direct:
   ```bash
   curl http://localhost:5001/health
   ```

## Stop

Press `CTRL+C` în terminal și ambii servicii (Flask + ngrok) se vor opri.

---

**TL;DR:**
```bash
python3 start_with_ngrok.py
```

Copy URL-ul și pune în email_confirm.html ✅
