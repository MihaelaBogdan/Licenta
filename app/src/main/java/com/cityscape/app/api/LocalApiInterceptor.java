package com.cityscape.app.api;

import com.cityscape.app.BuildConfig;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LocalApiInterceptor implements Interceptor {

    private static final String GOOGLE_API_KEY = BuildConfig.GOOGLE_API_KEY;
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String SUPABASE_KEY = BuildConfig.SUPABASE_KEY;
    private static final String OPENWEATHER_API_KEY = BuildConfig.OPENWEATHER_API_KEY;
    private static final String TICKETMASTER_API_KEY = BuildConfig.TICKETMASTER_API_KEY;

    private OkHttpClient httpClient;

    public LocalApiInterceptor() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String path = request.url().encodedPath();
        String jsonResponse = "[]";

        try {
            if (path.contains("/places")) {
                jsonResponse = getPlaces();
            } else if (path.contains("/nearby")) {
                double lat = parseDoubleOpt(request.url().queryParameter("lat"), 0);
                double lng = parseDoubleOpt(request.url().queryParameter("lng"), 0);
                String type = request.url().queryParameter("type");
                if (type == null) type = "restaurant";
                jsonResponse = getNearby(lat, lng, type);
            } else if (path.contains("/weather")) {
                double lat = parseDoubleOpt(request.url().queryParameter("lat"), 0);
                double lng = parseDoubleOpt(request.url().queryParameter("lng"), 0);
                jsonResponse = getWeather(lat, lng);
            } else if (path.contains("/itinerary")) {
                double lat = parseDoubleOpt(request.url().queryParameter("lat"), 0);
                double lng = parseDoubleOpt(request.url().queryParameter("lng"), 0);
                jsonResponse = getItinerary(lat, lng);
            } else if (path.contains("/events")) {
                double lat = parseDoubleOpt(request.url().queryParameter("lat"), 0);
                double lng = parseDoubleOpt(request.url().queryParameter("lng"), 0);
                String interests = request.url().queryParameter("interests");
                int radius = Integer.parseInt(request.url().queryParameter("radius") != null ? request.url().queryParameter("radius") : "50");

                jsonResponse = getEvents(lat, lng, radius, interests != null ? interests : "");
            } else if (path.contains("/predict")) {
                JSONObject rep = new JSONObject();
                rep.put("answer", "Salut! Cum nu vrem servere în fundal, modul complet de planificare direct de pe telefonul tău este activ. Cu ce destinație sau plan începem?");
                rep.put("intent", "itinerary");
                rep.put("confidence", 1.0);
                rep.put("suggestions", new JSONArray().put("Surprinde-mă!").put("Ce e în apropiere?"));
                jsonResponse = rep.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            jsonResponse = "[]";
            if (path.endsWith("/weather") || path.endsWith("/predict") || path.endsWith("/predict/detailed")) {
                jsonResponse = "{}";
            }
        }

        return new Response.Builder()
                .code(200)
                .message("OK")
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .body(ResponseBody.create(jsonResponse, MediaType.parse("application/json; charset=utf-8")))
                .addHeader("content-type", "application/json; charset=utf-8")
                .build();
    }

    private double parseDoubleOpt(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch(Exception e) {
            return def;
        }
    }

    private String get(String url) throws IOException {
        return get(url, null);
    }

    private String get(String url, String authBearer) throws IOException {
        Request.Builder rb = new Request.Builder().url(url);
        if (authBearer != null) {
            rb.addHeader("apikey", authBearer);
            rb.addHeader("Authorization", "Bearer " + authBearer);
        }
        try (Response resp = httpClient.newCall(rb.build()).execute()) {
            if (resp.body() != null) return resp.body().string();
        }
        return "";
    }

    private String getPlaces() throws Exception {
        String url = SUPABASE_URL + "/rest/v1/places?select=*";
        String resp = get(url, SUPABASE_KEY);
        if (resp != null && resp.startsWith("[")) {
            JSONArray arr = new JSONArray(resp);
            for (int i=0; i<arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (!obj.has("imageUrl") || obj.isNull("imageUrl") || obj.getString("imageUrl").contains("placeholder")) {
                    obj.put("imageUrl", "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80");
                }
            }
            return arr.toString();
        }
        return "[]";
    }

    private String getNearby(double lat, double lng, String requestedType) throws Exception {
        List<String> types = new ArrayList<>();
        if ("mixed".equals(requestedType) || "tourist_attraction".equals(requestedType)) {
            types.add("restaurant"); types.add("cafe"); types.add("park"); types.add("tourist_attraction"); types.add("museum");
        } else {
            types.add(requestedType);
        }

        JSONArray resultsArray = new JSONArray();
        java.util.Set<String> seenIds = new java.util.HashSet<>();

        for (String t : types) {
            String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=" + lat + "," + lng + "&radius=5000&type=" + t + "&key=" + GOOGLE_API_KEY;
            String resp = get(url);
            if (resp != null && !resp.isEmpty()) {
                JSONObject root = new JSONObject(resp);
                if (root.has("results")) {
                    JSONArray apiRes = root.getJSONArray("results");
                    for (int i=0; i<apiRes.length(); i++) {
                        JSONObject p = apiRes.getJSONObject(i);
                        String placeId = p.optString("place_id");
                        if (seenIds.contains(placeId)) continue;
                        seenIds.add(placeId);

                        JSONObject mapped = new JSONObject();
                        mapped.put("id", placeId);
                        mapped.put("name", p.optString("name"));
                        mapped.put("address", p.optString("vicinity"));
                        mapped.put("rating", p.optDouble("rating", 0.0));

                        String imgUrl = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80";
                        if (p.has("photos")) {
                            JSONArray photos = p.getJSONArray("photos");
                            if (photos.length() > 0) {
                                String ref = photos.getJSONObject(0).optString("photo_reference");
                                imgUrl = "https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference=" + ref + "&key=" + GOOGLE_API_KEY;
                            }
                        }
                        mapped.put("imageUrl", imgUrl);

                        if (p.has("geometry") && p.getJSONObject("geometry").has("location")) {
                            JSONObject loc = p.getJSONObject("geometry").getJSONObject("location");
                            mapped.put("latitude", loc.optDouble("lat", 0.0));
                            mapped.put("longitude", loc.optDouble("lng", 0.0));
                        }

                        String finalType = t.replace("_", " ");
                        finalType = finalType.substring(0, 1).toUpperCase() + finalType.substring(1);
                        mapped.put("type", finalType);

                        resultsArray.put(mapped);
                    }
                }
            }
        }
        return resultsArray.toString();
    }

    private String getWeather(double lat, double lng) throws Exception {
        String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lng + "&appid=" + OPENWEATHER_API_KEY + "&units=metric";
        String resp = get(url);
        JSONObject mapped = new JSONObject();
        if (resp != null && !resp.isEmpty()) {
            JSONObject root = new JSONObject(resp);
            if (root.has("main")) {
                mapped.put("temp", root.getJSONObject("main").getDouble("temp"));
                JSONObject w = root.getJSONArray("weather").getJSONObject(0);
                mapped.put("condition", w.getString("main"));
                mapped.put("description", w.getString("description"));
                mapped.put("icon", w.getString("icon"));
            }
        }
        return mapped.toString();
    }

    private String getEvents(double lat, double lng, int radius, String interests) throws Exception {
        String keyword = "";
        String lc = interests.toLowerCase();
        if (lc.contains("muzic") || lc.contains("music") || lc.contains("concert")) keyword = "Music";
        else if (lc.contains("art") || lc.contains("cultur")) keyword = "Arts";
        else if (lc.contains("sport")) keyword = "Sports";

        String url = "https://app.ticketmaster.com/discovery/v2/events.json?apikey=" + TICKETMASTER_API_KEY + "&latlong=" + lat + "," + lng + "&radius=50&unit=km&sort=date,asc&size=15";
        if (!keyword.isEmpty()) {
            url += "&keyword=" + keyword;
        }

        String resp = "";
        try {
            resp = get(url);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONArray out = new JSONArray();
        if (resp != null && !resp.isEmpty()) {
            try {
                JSONObject root = new JSONObject(resp);
                if (root.has("_embedded")) {
                    JSONArray evs = root.getJSONObject("_embedded").optJSONArray("events");
                    if (evs != null) {
                        for (int i=0; i<evs.length() && i<10; i++) {
                            JSONObject e = evs.getJSONObject(i);
                            JSONObject mapped = new JSONObject();
                            mapped.put("title", e.optString("name", "Eveniment"));

                            String imgUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=800&q=80";
                            if (e.has("images") && e.getJSONArray("images").length() > 0) {
                                imgUrl = e.getJSONArray("images").getJSONObject(0).optString("url");
                            }
                            mapped.put("imageUrl", imgUrl);
                            mapped.put("url", e.optString("url", ""));

                            String timeStr = "În curând";
                            if (e.has("dates") && e.getJSONObject("dates").has("start")) {
                                JSONObject start = e.getJSONObject("dates").getJSONObject("start");
                                timeStr = start.optString("localDate", "") + " " + start.optString("localTime", "");
                            }
                            mapped.put("time", timeStr.trim());

                            String locName = "Locație necunoscută";
                            double elat = 0, elng = 0;
                            if (e.has("_embedded") && e.getJSONObject("_embedded").has("venues")) {
                                JSONArray venues = e.getJSONObject("_embedded").getJSONArray("venues");
                                if (venues.length() > 0) {
                                    JSONObject v = venues.getJSONObject(0);
                                    locName = v.optString("name", "Locație necunoscută");
                                    if (v.has("location")) {
                                        elat = Double.parseDouble(v.getJSONObject("location").optString("latitude", "0"));
                                        elng = Double.parseDouble(v.getJSONObject("location").optString("longitude", "0"));
                                    }
                                }
                            }
                            mapped.put("location", locName);
                            mapped.put("latitude", elat);
                            mapped.put("longitude", elng);
                            out.put(mapped);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (out.length() == 0) {
            addFallbackEvents(out);
        }
        return out.toString();
    }

    private void addFallbackEvents(JSONArray out) throws Exception {
        String[] titles = {
            "Concert Live: Rock în Parc",
            "Festivalul de Gastronomie Urbană",
            "Expoziția 'Viziuni Contemporane'",
            "Maratonul de Seară",
            "Târgul de Meșteșuguri Arhitecturale",
            "Seară de Jazz & Wine",
            "Spectacol de Magie și Umor"
        };
        String[] locations = {
            "Parcul Central",
            "Piața Victoriei",
            "Muzeul Național de Artă",
            "Arena Națională",
            "Centrul Vechi",
            "Grădina Botanică",
            "Palatul Culturii"
        };
        String[] images = {
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800",
            "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800",
            "https://images.unsplash.com/photo-1518998053502-53cc8de431d8?w=800",
            "https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=800",
            "https://images.unsplash.com/photo-1531050171651-648b742ec76d?w=800",
            "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=800",
            "https://images.unsplash.com/photo-1517457373958-b7bdd4587205?w=800"
        };

        java.util.Calendar cal = java.util.Calendar.getInstance();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());

        for (int i=0; i < titles.length; i++) {
            JSONObject e = new JSONObject();
            e.put("title", titles[i]);
            e.put("location", locations[i]);
            e.put("imageUrl", images[i]);
            e.put("url", "https://google.com/search?q=" + titles[i]);
            
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 18 + (i % 4));
            e.put("time", sdf.format(cal.getTime()));
            
            e.put("latitude", 0.0); // Will use current city coords in UI
            e.put("longitude", 0.0);
            
            out.put(e);
        }
    }

    private String getItinerary(double lat, double lng) throws Exception {
        JSONArray plan = new JSONArray();
        String[] types = {"cafe", "tourist_attraction", "restaurant", "park"};
        String[] labels = {"Mic Dejun", "Explorare", "Prânz", "Relaxare"};

        double currentLat = lat;
        double currentLng = lng;

        for (int i=0; i<types.length; i++) {
            String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=" + currentLat + "," + currentLng + "&radius=4000&type=" + types[i] + "&key=" + GOOGLE_API_KEY;
            String resp = get(url);
            if (resp != null && resp.contains("\"results\"")) {
                JSONObject root = new JSONObject(resp);
                JSONArray resArr = root.optJSONArray("results");
                if (resArr != null && resArr.length() > 0) {
                    JSONObject best = resArr.getJSONObject(0);
                    JSONObject step = new JSONObject();
                    step.put("slot", labels[i]);
                    step.put("name", best.optString("name"));
                    step.put("address", best.optString("vicinity"));

                    String imgUrl = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=800&q=80";
                    if (best.has("photos") && best.getJSONArray("photos").length() > 0) {
                        String ref = best.getJSONArray("photos").getJSONObject(0).optString("photo_reference");
                        imgUrl = "https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photoreference=" + ref + "&key=" + GOOGLE_API_KEY;
                    }
                    step.put("imageUrl", imgUrl);

                    double elat=0, elng=0;
                    if (best.has("geometry") && best.getJSONObject("geometry").has("location")) {
                        elat = best.getJSONObject("geometry").getJSONObject("location").optDouble("lat");
                        elng = best.getJSONObject("geometry").getJSONObject("location").optDouble("lng");
                    }
                    step.put("latitude", elat);
                    step.put("longitude", elng);

                    int priceMap = 85;
                    if (types[i].equals("park")) priceMap = 0;
                    if (types[i].equals("tourist_attraction")) priceMap = 30;
                    step.put("estimatedCost", priceMap);

                    plan.put(step);
                    currentLat = elat;
                    currentLng = elng;
                }
            }
        }
        return plan.toString();
    }
}
