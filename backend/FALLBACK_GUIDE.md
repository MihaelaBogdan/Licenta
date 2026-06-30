# 📦 Fallback Database Guide

## Ce e Fallback-ul?

Când Google Places API nu merge (timeout, no connection, API key invalid), aplicația folosește o **bază de date locală** cu 15+ locuri populare din Bucuresti, Cluj, Iasi etc.

## Cum funcționează?

### 1. **Fallback Database File**: `fallback_places.json`
```json
{
  "timestamp": "2026-06-24T...",
  "places": {
    "București": [
      {
        "name": "Piața Constituției",
        "lat": 44.4272,
        "lng": 26.0915,
        "rating": 4.5,
        "address": "...",
        "type": "tourist_attraction"
      },
      ...
    ]
  }
}
```

### 2. **Backend Flow**:
```
API Request
    ↓
Try Google API (5s timeout)
    ↓ FAILS (timeout/no connection)
    ↓
Return Fallback Places
    ↓
✅ App gets data anyway!
```

## Cum se Actualizează?

```bash
cd /Users/mihaela/Desktop/Licenta/backend

# Regenerează fallback din date hardcoded
python3 scraper_fallback.py

# Output: 15 locuri în Bucuresti, 2 în Cluj, etc.
```

## Cum Adaugi Mai Multe Locuri?

Edit `scraper_fallback.py`:
```python
"București": [
    {...existing places...},
    {
        "name": "Noul Meu Loc",
        "lat": 44.XXX,
        "lng": 26.XXX,
        "rating": 4.5,
        "reviews": 5000,
        "address": "Strada X, București",
        "type": "restaurant",
        "photo": "https://..."
    }
]
```

Apoi rulează:
```bash
python3 scraper_fallback.py
```

## Status

✅ **Fallback încărcat**: 15 locuri în BD
✅ **Google API down?**: App continuă să merge
✅ **Photos fail?**: Unsplash fallback images
✅ **Weather down?**: Default sunny day (20°C)

## Log Output

Când fallback-ul se activează, vei vedea:
```
⚠️ Google Timeout → Using fallback for tourist_attraction
📦 Using fallback for București: 15 places
```

Perfect! Acum aplicația ALWAYS funcționează! 🎉
