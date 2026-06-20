# 🎯 Explainable Recommendations API Documentation

## Overview

The Explainable Recommendations System provides AI-powered, transparent recommendations with full explainability. Every recommendation includes:

- **Confidence Score** (0-100%): How confident the system is about this recommendation
- **Reasoning**: Human-readable explanation of why this place is recommended
- **Factor Breakdown**: Detailed scores for each decision factor
- **Complete History**: Track all recommendations and their outcomes

---

## Key Features

### 1. **Transparent Confidence Scoring**
Each recommendation shows exactly how confident the algorithm is:
```json
{
  "confidence": "87.3%",
  "reasoning": "Corespunde intereselor tale • Nu ai vizitat încă • Foarte apreciat de alți vizitatori",
  "explanation": {
    "factors": [
      {"name": "Interest Match", "score": "30.0%", "description": "..."},
      {"name": "Freshness", "score": "25.0%", "description": "..."},
      ...
    ]
  }
}
```

### 2. **Decision Factors**

The recommendation algorithm considers 5 key factors:

| Factor | Max Score | Description |
|--------|-----------|-------------|
| **Interest Match** | 30% | How well the place aligns with user's interests |
| **Freshness** | 25% | Whether the user hasn't visited this place before |
| **Popularity** | 20% | Rating and review count from other visitors |
| **User Level** | 15% | Whether the place suits the user's experience level |
| **Diversity** | 10% | Difference from recently visited places |

### 3. **Complete History Tracking**

Every recommendation is logged with:
- Timestamp when recommended
- User response (visited/accepted/rejected)
- Final confidence score
- All reasoning factors

---

## API Endpoints

### 1. Get Explainable Recommendations

**POST** `/recommendations/explainable`

Get personalized recommendations with full transparency.

**Request Body:**
```json
{
  "user_id": "uuid-here",
  "lat": 44.4268,
  "lng": 26.1025,
  "interests": ["art", "history", "museums"],
  "language": "ro",
  "limit": 5
}
```

**Response:**
```json
{
  "status": "success",
  "user_id": "uuid-here",
  "timestamp": "2026-06-04T10:30:45.123Z",
  "recommendations": [
    {
      "id": "place_id_123",
      "name": "Sala Palatului",
      "address": "Calea 13 Septembrie, București",
      "rating": 4.8,
      "reviews": 1250,
      "type": "museum",
      "confidence": "87.3%",
      "reasoning": "Corespunde intereselor tale • Nu ai vizitat încă",
      "explanation": {
        "summary": "Pe baza analizei factorilor de personalizare",
        "factors": [
          {
            "name": "Potrivire interese",
            "score": "30.0%",
            "description": "Cât de bine se potrivește cu interesele tale salvate"
          },
          {
            "name": "Noutate",
            "score": "25.0%",
            "description": "Dacă nu ai vizitat deja acest loc"
          }
          // ... more factors
        ],
        "total_confidence": "87.3%"
      }
    }
    // ... more recommendations
  ],
  "metadata": {
    "total_considered": 50,
    "method": "explainable_ai",
    "language": "ro"
  }
}
```

---

### 2. Get Recommendation History

**GET** `/recommendations/history/<user_id>?limit=50`

Fetch complete history of all recommendations given to a user.

**Response:**
```json
{
  "status": "success",
  "user_id": "uuid-here",
  "total_count": 127,
  "history": [
    {
      "id": "rec_uuid",
      "place_name": "Sala Palatului",
      "place_id": "place_123",
      "confidence": 87.3,
      "reasoning": "Interest match + fresh content",
      "recommended_at": "2026-06-04T10:30:45Z",
      "status": "visited",
      "visited_at": "2026-06-04T14:20:15Z",
      "user_feedback": "Amazing place!"
    },
    // ... more history
  ]
}
```

**Query Parameters:**
- `limit`: Maximum number of records (default: 50)

---

### 3. Get Recommendation Statistics

**GET** `/recommendations/stats/<user_id>`

Get analytics about recommendation accuracy and user preferences.

**Response:**
```json
{
  "status": "success",
  "user_id": "uuid-here",
  "total_recommendations": 127,
  "visited_count": 95,
  "accepted_count": 20,
  "rejected_count": 12,
  "accuracy_rate": "90.6%",
  "avg_confidence": "82.4%"
}
```

**Interpretation:**
- **Accuracy Rate**: Percentage of recommendations that were visited or accepted
- **Avg Confidence**: Average confidence score of all recommendations
- Higher accuracy = better personalization

---

### 4. Update Recommendation Status

**PATCH** `/recommendations/<rec_id>/status`

Update the status of a recommendation (helps system learn).

**Request Body:**
```json
{
  "status": "visited"
}
```

**Valid Status Values:**
- `pending` - Not yet acted upon
- `visited` - User visited this place
- `accepted` - User accepted but hasn't visited yet
- `rejected` - User explicitly rejected

**Response:**
```json
{
  "status": "success",
  "message": "Recommendation status updated to 'visited'"
}
```

---

### 5. Compare User Recommendations

**GET** `/recommendations/comparison?user_id_1=<id1>&user_id_2=<id2>`

Compare recommendations between two users to find similarity.

**Response:**
```json
{
  "status": "success",
  "user_1": {
    "user_id": "user-1",
    "total_recommendations": 127,
    "places": ["Sala Palatului", "Teatrul Național", ...]
  },
  "user_2": {
    "user_id": "user-2",
    "total_recommendations": 95,
    "places": ["Teatrul Național", "Art Safari", ...]
  },
  "common_recommendations": ["Teatrul Național", "Parcul Cismigiu"],
  "similarity_percentage": "42.3%"
}
```

**Query Parameters:**
- `user_id_1`: First user (required)
- `user_id_2`: Second user (optional)
- `professor_id`: Professor ID for comparison (optional)

---

## Integration Examples

### JavaScript/React Example

```javascript
async function getRecommendations(userId, lat, lng, interests) {
  const response = await fetch('http://api.cityscape.local/recommendations/explainable', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      user_id: userId,
      lat: lat,
      lng: lng,
      interests: interests,
      language: 'ro',
      limit: 5
    })
  });

  return response.json();
}

// Display recommendations with explanations
async function displayRecommendations() {
  const recs = await getRecommendations('user-123', 44.4268, 26.1025, ['art']);

  recs.recommendations.forEach(rec => {
    console.log(`${rec.name} - ${rec.confidence} confidence`);
    console.log(`Why: ${rec.reasoning}`);

    rec.explanation.factors.forEach(factor => {
      console.log(`  • ${factor.name}: ${factor.score}`);
    });
  });
}
```

### Python Example

```python
import requests

def get_recommendations(user_id, lat, lng, interests):
    response = requests.post(
        'http://localhost:5001/recommendations/explainable',
        json={
            'user_id': user_id,
            'lat': lat,
            'lng': lng,
            'interests': interests,
            'language': 'ro',
            'limit': 5
        }
    )
    return response.json()

# Get history
history = requests.get(
    f'http://localhost:5001/recommendations/history/{user_id}'
).json()

# Get stats
stats = requests.get(
    f'http://localhost:5001/recommendations/stats/{user_id}'
).json()

print(f"Accuracy: {stats['accuracy_rate']}")
print(f"Average Confidence: {stats['avg_confidence']}")
```

### Android/Kotlin Example

```kotlin
// Using Retrofit
interface RecommendationService {
    @POST("recommendations/explainable")
    suspend fun getRecommendations(@Body request: RecommendationRequest): RecommendationResponse

    @GET("recommendations/history/{userId}")
    suspend fun getHistory(@Path("userId") userId: String): HistoryResponse

    @PATCH("recommendations/{recId}/status")
    suspend fun updateStatus(
        @Path("recId") recId: String,
        @Body request: StatusUpdateRequest
    ): StatusResponse
}

data class RecommendationRequest(
    val user_id: String,
    val lat: Double,
    val lng: Double,
    val interests: List<String>,
    val language: String = "ro",
    val limit: Int = 5
)

// Usage
viewModelScope.launch {
    val recs = service.getRecommendations(
        RecommendationRequest(
            user_id = userId,
            lat = 44.4268,
            lng = 26.1025,
            interests = listOf("art", "history")
        )
    )

    recs.recommendations.forEach { rec ->
        // Display with confidence: ${rec.confidence}
        // Reasoning: ${rec.reasoning}
    }
}
```

---

## Data Schema

### recommendation_history Table

```sql
CREATE TABLE recommendation_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    place_name VARCHAR(255) NOT NULL,
    place_id VARCHAR(255),
    place_type VARCHAR(100),
    confidence FLOAT,          -- 0-100
    reasoning TEXT,            -- Human-readable reason
    factors JSONB,            -- Detailed factor scores
    recommended_at TIMESTAMP,  -- When recommended
    status VARCHAR(50),        -- pending|visited|accepted|rejected
    visited_at TIMESTAMP,      -- When user visited
    user_feedback TEXT,        -- Optional user comment
    updated_at TIMESTAMP,      -- Last updated
    created_at TIMESTAMP
);
```

---

## Best Practices

### 1. **Update Status Regularly**
Send feedback about recommendations so the system improves:
```javascript
// User visited a recommended place
await fetch('/recommendations/{recId}/status', {
  method: 'PATCH',
  body: JSON.stringify({ status: 'visited' })
});
```

### 2. **Display Confidence Clearly**
Always show confidence scores:
```
"Sala Palatului"
🎯 87.3% match | ⭐ 4.8 rating
Why: Interest match + fresh content
```

### 3. **Use History for Analytics**
Track recommendation accuracy over time:
```javascript
const stats = await fetch(`/recommendations/stats/${userId}`).json();
console.log(`Success rate: ${stats.accuracy_rate}`);
```

### 4. **Compare Users for Insights**
Identify user segments and preferences:
```javascript
const comparison = await fetch(
  `/recommendations/comparison?user_id_1=A&user_id_2=B`
).json();
console.log(`Similarity: ${comparison.similarity_percentage}`);
```

---

## Performance & Limits

- **Rate Limiting**: 100 requests per minute per user
- **History Retention**: 1 year of recommendation history
- **Max Results**: 100 recommendations per query
- **Search Radius**: Up to 50km from coordinates

---

## Troubleshooting

### Low Confidence Scores?
- Ensure user interests are properly set in profile
- Add more place visits to improve personalization
- Check if nearby places match interests

### No Recommendations?
- Verify coordinates are correct (lat/lng)
- Check if user profile exists in database
- Ensure sufficient nearby places available

### Historical Data Not Appearing?
- Create `recommendation_history` table (see migration script)
- Check user_id matches profile table
- Verify Supabase credentials are correct

---

## Future Enhancements

- [ ] Machine learning model to improve scoring
- [ ] Collaborative filtering (user-to-user recommendations)
- [ ] Time-based recommendations (what's busy now)
- [ ] Weather-aware recommendations
- [ ] Group recommendations (multiple users)
- [ ] Predictive recommendations (before user asks)

---

**Last Updated**: June 2026
**Version**: 1.0
**Status**: Production Ready ✅
