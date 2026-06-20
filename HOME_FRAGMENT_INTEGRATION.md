# 🎯 HomeFragment Integration - Show Scores on Click

## Problem
Când dai click pe card, nu se arată `matchHistoryPct` și `matchPrefsPct` (procentajele).

## Solution
Trebuie să apelezi API `/recommendations/explainable` și să mapezi response la Place model.

---

## Code to Add in HomeFragment

### 1. Add this method in HomeFragment:

```java
/**
 * Fetch recommendations and populate match percentages
 */
private void fetchAndDisplayRecommendationScores(String userId, double lat, double lng, String[] interests) {
    try {
        // Build request
        JSONObject requestBody = new JSONObject();
        requestBody.put("user_id", userId);
        requestBody.put("lat", lat);
        requestBody.put("lng", lng);
        requestBody.put("interests", new JSONArray(java.util.Arrays.asList(interests)));
        requestBody.put("city_name", "București");
        requestBody.put("limit", 10);
        requestBody.put("trending", true);
        requestBody.put("language", "ro");

        // Make API call
        String url = "http://YOUR_API_URL:5001/recommendations/explainable";
        
        new Thread(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                okhttp3.MediaType JSON = okhttp3.MediaType.get("application/json; charset=utf-8");
                okhttp3.RequestBody body = okhttp3.RequestBody.create(requestBody.toString(), JSON);
                
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

                okhttp3.Response response = client.newCall(request).execute();
                String responseBody = response.body().string();
                
                // Parse response
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray recommendations = jsonResponse.optJSONArray("recommendations");
                
                if (recommendations != null && recommendations.length() > 0) {
                    // Convert to Place objects
                    java.util.List<Place> places = RecommendationMapper.mapRecommendationsToPlaces(recommendations);
                    
                    // Update UI on main thread
                    getActivity().runOnUiThread(() -> {
                        updatePlacesWithRecommendations(places);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        
    } catch (Exception e) {
        e.printStackTrace();
    }
}

/**
 * Update places list with recommendation scores
 */
private void updatePlacesWithRecommendations(java.util.List<Place> recommendedPlaces) {
    if (placesAdapter != null) {
        placesAdapter.updatePlaces(recommendedPlaces);
    }
}
```

### 2. Call it when user opens app or wants recommendations:

```java
@Override
public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    
    // ... other setup code ...
    
    // When you have user data, fetch recommendations
    String userId = getCurrentUserId();
    double latitude = getCurrentLatitude();
    double longitude = getCurrentLongitude();
    String[] interests = new String[]{"museums", "art", "history"};
    
    // THIS WILL FETCH SCORES AND SHOW THEM
    fetchAndDisplayRecommendationScores(userId, latitude, longitude, interests);
}
```

---

## What Happens

### Before (No Scores):
```
Card shows:
- Place name
- Rating
- Address
But NO percentages
```

### After (With Scores):
```
Card shows:
- Place name
- Rating
- Address
+ 87% Potrivire ✅
+ "Corespunde intereselor..."
```

### Click on Card → Shows Details:
```
SALA PALATULUI                    87%

🎯 De ce ți-am recomandat asta?

Corespunde intereselor • Nu ai vizitat

📊 FACTORI:
Interese:     30%  ████
Noutate:      25%  ███
Popularitate: 20%  ██
Nivel:        12%  █
Diversitate:   0%

Total: 87% ✅
```

---

## Key Parts

### RecommendationMapper.java (Already Created)
- Maps API response → Place object
- Extracts percentages from "87.3%" → 87
- Handles factors breakdown

### fetchAndDisplayRecommendationScores()
- Calls `/recommendations/explainable` endpoint
- Gets recommendations with confidence scores
- Updates UI with new Place objects

### HomeFragment (Your Code)
- Calls fetchAndDisplayRecommendationScores()
- Updates RecyclerView with new places
- Existing UI automatically shows percentages

---

## Test It

```bash
# 1. Start backend
python3 backend/app.py

# 2. In app, go to HomeFragment
# 3. Should see cards with percentages like:
#    87% Potrivire
#    Corespunde intereselor
# 
# 4. Click card → Details show breakdown
```

---

## Important!

Update these in code:
```java
// YOUR API URL (not localhost since it's from phone)
String url = "http://192.168.1.132:5001/recommendations/explainable";

// Get real user data
String userId = getCurrentUserId();
double latitude = getCurrentLatitude();
double longitude = getCurrentLongitude();
String[] interests = getUserInterests();
```

---

## Summary

- ✅ No table needed
- ✅ API returns percentages
- ✅ Mapper converts to Place model
- ✅ HomeFragment displays scores
- ✅ Click shows details with factors

That's it! 🎉
