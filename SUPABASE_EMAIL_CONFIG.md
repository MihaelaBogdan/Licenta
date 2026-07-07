# Configurare Email Confirmation în Supabase

## Pasul 1: Accesează Supabase Dashboard
1. Mergi la https://app.supabase.com
2. Selectează proiectul CityScape
3. Navighează la **Authentication** → **Email Templates**

## Pasul 2: Configurează Confirm Email Template

În secțiunea **Confirm signup**, actualizează link-ul din email template:

### Default template (ce trebuie modificat):
```
{{ .ConfirmationURL }}
```

### Noul template (cu redirect la pagina noastră):
```
https://cityscape-app-backend.onrender.com/auth/confirm?token={{ .TokenHash }}&type=email
```

Sau dacă folosești localhost:
```
http://localhost:5002/auth/confirm?token={{ .TokenHash }}&type=email
```

## Pasul 3: Salvează Template-ul
1. Click pe "Save" (butonul din dreapta jos)
2. Confirmă schimbarea

## Pasul 4: Testează
1. Înregistrează un cont nou în app
2. Ar trebui să primești email cu link-ul
3. Click link → pagina de confirmare
4. Vei vedea "Email Confirmed! Acum te poți loga"

## Variabile disponibile în Supabase email template:
- `{{ .ConfirmationURL }}` - URL-ul complet generat de Supabase
- `{{ .TokenHash }}` - Token-ul de confirmare (ce folosim noi)
- `{{ .Email }}` - Email-ul utilizatorului
- `{{ .Data.* }}` - Date custom trimise la signup

## URL Structure:
```
/auth/confirm?token=TOKEN&type=email
```

Frontend-ul va:
1. Parse token și type din URL
2. POST la `/api/auth/confirm-email` cu token
3. Backend verifica token cu Supabase
4. Afișează pagina de succes

## Debugging:
- Dacă nu primești email: verifica Spam folder
- Dacă linkul nu funcționează: verifica în Supabase logs (Authentication → Logs)
- Dacă error 400: token-ul este expirat (valabil 24 ore)

## Variante de URL (După deploy):
- Production: `https://cityscape-app.com/auth/confirm`
- Staging: `https://staging.cityscape-app.com/auth/confirm`
- Dev: `http://localhost:5002/auth/confirm`

Update-ază URL-ul în Supabase Email Template după fiecare deploy!
