# 🎯 UNDE Vezi SCORURILE DE RECOMANDĂRI ÎN APP

## 📍 LOCUL 1: Pe Cardurile din Home Screen

**Fișier**: `app/src/main/res/layout/item_place_card.xml`

Când derulezi prin locațiile recomandate, fiecare card va arăta:

```
┌─────────────────────────┐
│   [IMAGINE LOCAȚIE]     │
│                         │
│ ┌─────────────────────┐ │
│ │ 🎯 Potrivire: 87%   │ │  ← CONFIDENCE SCORE
│ │ Corespunde interese │ │  ← REASONING
│ │ ⭐ 4.8 (1250 review)│ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

### Detalii pe card:
- **🎯 Potrivire 87%** - Confidence score (culoare verde = bun)
- **Text mic** - "Corespunde intereselor tale" - reasoning
- **⭐ Rating + reviews** - Popularitate
- **Click pe card** - Deschide detaliile complete

---

## 📋 LOCUL 2: În Detaliile Locației (Place Detail Activity)

**Fișier**: `app/src/main/res/layout/activity_place_detail.xml`

Când deschizi un loc, vezi:

```
┌─────────────────────────────────────────┐
│   SALA PALATULUI                 87% ✅  │  ← CONFIDENCE BADGE
├─────────────────────────────────────────┤
│ 🎯 De ce ți-am recomandat asta?         │
│                                         │
│ Corespunde intereselor tale •           │  ← REASONING
│ Nu ai vizitat încă                      │
│                                         │
│ 📊 FACTORI DE DECIZIE:                  │
│                                         │
│ Potrivire Interese        30%  ████     │
│ Noutate (Nu ai vizitat)   25%  ███      │  ← DETAILED BREAKDOWN
│ Popularitate              20%  ██       │
│ Nivel Potrivit            12%  █        │
│ Diversitate               10%  █        │
│                                         │
│ ─────────────────────────────────────── │
│ Total Încredere                  97% ✅  │  ← TOTAL CONFIDENCE
└─────────────────────────────────────────┘
```

### Ce vezi:
1. **87% BADGE** - Confidence score în colț
2. **"De ce ți-am recomandat asta?"** - Titlu
3. **Reasoning text** - Explicație în 1-2 linii
4. **Progress bars** - Fiecare factor cu scorul %
5. **Total Încredere** - Suma tuturor factorilor

---

## 🔧 LOCUL 3: În Cod - Unde Modifici

### Android Layout Changes:

```kotlin
// ✅ Adăugat pe card:
<LinearLayout android:id="@+id/recommendation_info">
    <TextView android:id="@+id/confidence_score" />      // "87%"
    <TextView android:id="@+id/ai_suggestion" />         // "Corespunde..."
</LinearLayout>

// ✅ Adăugat în detalii:
<CardView android:id="@+id/aiAnalysisSection">
    <TextView android:id="@+id/confidenceScoreBadge" />  // "87%"
    <ProgressBar id="@+id/factor_interest_progress" />   // Bara pt fiecare factor
    <TextView android:id="@+id/totalConfidence" />       // "97%"
</CardView>
```

### ViewModel (Kotlin):

```kotlin
class RecommendationViewModel(private val apiService: ApiService) : ViewModel() {
    // Fetch recommendations
    fun fetchRecommendations(
        userId: String,
        latitude: Double,
        longitude: Double,
        interests: List<String>
    ) { ... }

    // Get factor percentage
    fun getFactorPercentage(factor: Factor): Int { ... }

    // Get confidence color
    fun getConfidenceColor(confidence: String): Int { ... }
}
```

---

## 📲 API RESPONSE - Ce Primești de la Backend

Când apelezi `/recommendations/explainable`, primești:

```json
{
  "status": "success",
  "user_id": "mihaela-123",
  "city": "București",
  "count": 5,
  "recommendations": [
    {
      "name": "Sala Palatului",
      "confidence": "87.3%",
      "reasoning": "Corespunde intereselor • Nu ai vizitat încă",
      "explanation": {
        "factors": [
          {
            "name": "Potrivire Interese",
            "score": "30%",
            "description": "Cât de bine se potrivește..."
          },
          {
            "name": "Noutate",
            "score": "25%",
            "description": "Dacă nu ai vizitat deja..."
          },
          // ... 3 mai mulți factori
        ],
        "total_confidence": "97%"
      }
    },
    // ... 4 recomandări mai
  ]
}
```

---

## 🎯 FLOW-UL COMPLET

```
1. User in Home
   ↓
2. Văd cardurile cu locații
   ├─ Fiecare card are: 🎯 87% + Reasoning
   └─ Cardurile sunt sortate descrescător pe confidence
   ↓
3. Click pe card
   ↓
4. Se deschide Place Detail cu:
   ├─ 87% BADGE în top
   ├─ "De ce ți-am recomandat asta?"
   ├─ 5 factori cu progress bars
   └─ Total Confidence: 97%
   ↓
5. User poate marca: "Visited" / "Liked" / "Reject"
   ↓
6. System learns din feedback → następne recomandări mai bune
```

---

## 🔥 TRENDING + SPECIFICITY

Ceea ce ai cerut:
- ✅ **Specific per user** - Factori calculați pe baza istoricului individual
- ✅ **Max 10** - `limit` parameter clampat la 1-10
- ✅ **Trending** - Includ places visited by other similar users
- ✅ **City Name** - Apare ca `"city": "București"` în response

### Example Request (Trending + City):

```bash
curl -X POST http://localhost:5001/recommendations/explainable \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "mihaela-123",
    "lat": 44.4268,
    "lng": 26.1025,
    "interests": ["art", "history"],
    "city_name": "București",
    "limit": 10,
    "trending": true
  }'
```

### Response va fi cu city:

```json
{
  "status": "success",
  "user_id": "mihaela-123",
  "city": "București",        ← CITY NAME
  "count": 10,               ← Exact 10 (sau mai puțin dacă nu sunt 10 disponibile)
  "recommendations": [
    {
      "name": "Sala Palatului",
      "confidence": "87.3%",
      "is_trending": true    ← Marcat dacă e trending
      ...
    },
    // ... max 9 mai
  ]
}
```

---

## 🎨 COLOR CODING PENTRU CONFIDENCE

```
87% → 🟢 GREEN    (Excellent match)
65% → 🟡 YELLOW   (Good match)
42% → 🔴 RED      (Low match)
```

Codul asta e în `PlaceDetailBindingAdapter.kt`:

```kotlin
@BindingAdapter("bindConfidenceScore")
fun bindConfidenceScore(textView: TextView, rec: ExplainableRecommendation?) {
    val percentage = rec?.confidence?.replace("%", "")?.toDoubleOrNull() ?: 0.0
    val color = when {
        percentage >= 80 -> "#4CAF50"  // Green
        percentage >= 60 -> "#FFC107"  // Yellow
        else -> "#F44336"              // Red
    }
    textView.setTextColor(android.graphics.Color.parseColor(color))
}
```

---

## ✅ TESTING CHECKLIST

- [ ] Home screen - Cardurile arată confidence score?
- [ ] Click pe card - Se deschide detaliile cu 5 factori?
- [ ] Progress bars - Se umplu corect (30%, 25%, etc)?
- [ ] Total Confidence - Se calculează corect (97%)?
- [ ] City name - Apare "București" în response?
- [ ] Trending - Sunt locuri cu `is_trending: true`?
- [ ] Max 10 - Niciodată mai mult de 10 recomandări?
- [ ] Specific user - Recomandări se schimbă per user?

---

## 🚀 NEXT STEPS

1. **Integreaza ViewModel în PlaceDetailActivity**
   ```kotlin
   val viewModel = RecommendationViewModel(apiService)
   viewModel.fetchRecommendations(userId, lat, lng, interests)
   viewModel.selectedRecommendation.observe(this) { rec ->
       bindConfidenceScore(scoreView, rec)
       bindReasoning(reasoningView, rec)
       // etc
   }
   ```

2. **Setup data binding în Activity**
   ```kotlin
   binding.viewModel = viewModel
   binding.lifecycleOwner = this
   ```

3. **Test cu real user data**
   - Create test user with interests
   - Make API call cu coordinates
   - Verify scores appear

4. **Monitor accuracy**
   - Track which recommendations users visit
   - Check if high confidence recommendations are actually visited
   - Adjust factor weights if needed

---

**Summa summarum**: 
- 🎯 Scorurile se vad pe carduri (87%)
- 📊 Detaliile factorilor se vad în Place Detail
- 🔥 Trending + City name + Max 10 → toate adăugate
- 🎨 Color coding → verde/galben/roșu
- 📱 Kotlin ViewModel bind-ul datele din API la UI

Gata! 🎉
