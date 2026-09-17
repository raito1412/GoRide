package com.goride.server;

import com.goride.common.Config;
import com.goride.common.Geo;
import com.goride.common.Json;
import com.goride.common.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gọi Mapbox REST API (Geocoding v6 + Directions v5). Token chỉ nằm ở server (.env: MAPBOX_TOKEN).
 * Không có token: route() tính gần đúng bằng đường chim bay để vẫn demo được.
 * Lưu ý: Mapbox không có profile xe máy nên Bike và Car đều dùng "driving".
 */
public final class MapboxClient {
    private static final String BASE = "https://api.mapbox.com";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private MapboxClient() {}

    static boolean enabled() { return !token().isBlank(); }

    private static String token() { return Config.get("MAPBOX_TOKEN", ""); }

    private static JsonObject get(String path, Map<String, String> params) {
        if (!enabled()) throw new AppException("Server chưa cấu hình MAPBOX_TOKEN trong file .env");
        StringBuilder url = new StringBuilder(BASE).append(path).append("?access_token=").append(enc(token()));
        params.forEach((k, v) -> url.append('&').append(k).append('=').append(enc(v)));
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString())).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            switch (res.statusCode()) {
                case 200 -> { }
                case 401, 403 -> throw new AppException("MAPBOX_TOKEN không hợp lệ hoặc không đủ quyền");
                case 429 -> throw new AppException("Mapbox: vượt giới hạn số request, thử lại sau");
                default -> throw new AppException("Mapbox trả lỗi HTTP " + res.statusCode());
            }
            return JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            Log.error("Mapbox " + path, e);
            throw new AppException("Không kết nối được Mapbox API");
        }
    }

    /** Tham số chung cho Geocoding: ưu tiên Việt Nam, tiếng Việt. */
    private static Map<String, String> geoParams() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("country", "vn");
        p.put("language", "vi");
        return p;
    }

    /**
     * Gợi ý địa chỉ theo từ khóa.
     * Kết quả Mapbox đã có toạ độ nên place_id tự mang "lat,lng|địa chỉ" -> detail() không cần gọi API nữa.
     */
    static JsonArray search(String input, double lat, double lng) {
        Map<String, String> p = geoParams();
        p.put("q", input);
        p.put("limit", "8");
        p.put("autocomplete", "true");
        if (!Double.isNaN(lat)) p.put("proximity", lngLat(lat, lng));
        JsonArray out = new JsonArray();
        for (JsonElement e : features(get("/search/geocode/v6/forward", p))) {
            JsonObject f = e.getAsJsonObject();
            double[] c = coords(f);
            String address = address(f);
            out.add(Json.obj("place_id", String.format(Locale.US, "%.6f,%.6f|%s", c[0], c[1], address),
                    "description", address));
        }
        return out;
    }

    /** place_id (dạng "lat,lng|địa chỉ" do search() tạo) -> toạ độ + địa chỉ. */
    static JsonObject detail(String placeId) {
        try {
            int bar = placeId.indexOf('|');
            String[] ll = placeId.substring(0, bar).split(",");
            return Json.obj("lat", Double.parseDouble(ll[0]), "lng", Double.parseDouble(ll[1]),
                    "address", placeId.substring(bar + 1));
        } catch (RuntimeException e) {
            throw new AppException("Không tìm thấy địa điểm");
        }
    }

    /** Toạ độ -> địa chỉ. Không có token thì trả chuỗi toạ độ. */
    static String reverse(double lat, double lng) {
        String fallback = String.format(Locale.US, "Vị trí %.5f, %.5f", lat, lng);
        if (!enabled()) return fallback;
        try {
            Map<String, String> p = geoParams();
            p.put("latitude", String.format(Locale.US, "%.6f", lat));
            p.put("longitude", String.format(Locale.US, "%.6f", lng));
            p.put("limit", "1");
            JsonArray fs = features(get("/search/geocode/v6/reverse", p));
            return fs.isEmpty() ? fallback : address(fs.get(0).getAsJsonObject());
        } catch (AppException e) {
            return fallback;
        }
    }

    /** Địa chỉ -> toạ độ (Admin thêm nhà hàng). */
    static double[] geocode(String address) {
        Map<String, String> p = geoParams();
        p.put("q", address);
        p.put("limit", "1");
        p.put("autocomplete", "false");
        JsonArray fs = features(get("/search/geocode/v6/forward", p));
        if (fs.isEmpty()) throw new AppException("Không tìm được toạ độ cho địa chỉ này");
        return coords(fs.get(0).getAsJsonObject());
    }

    /** Tuyến đường: {distance_m, duration_s, points:[[lat,lng],...], source}. vehicle: bike | car */
    static JsonObject route(double lat1, double lng1, double lat2, double lng2, String vehicle) {
        if (enabled()) {
            try {
                Map<String, String> p = new LinkedHashMap<>();
                p.put("geometries", "polyline"); // polyline độ chính xác 1e5, dùng lại Geo.decodePolyline
                p.put("overview", "full");
                String path = "/directions/v5/mapbox/driving/" + lngLat(lat1, lng1) + ";" + lngLat(lat2, lng2);
                JsonArray routes = get(path, p).getAsJsonArray("routes");
                if (routes != null && !routes.isEmpty()) {
                    JsonObject r = routes.get(0).getAsJsonObject();
                    JsonArray points = new JsonArray();
                    for (double[] pt : Geo.decodePolyline(r.get("geometry").getAsString())) points.add(pair(pt[0], pt[1]));
                    return Json.obj("distance_m", (int) Math.round(r.get("distance").getAsDouble()),
                            "duration_s", (int) Math.round(r.get("duration").getAsDouble()),
                            "points", points, "source", "mapbox");
                }
            } catch (AppException | IllegalStateException e) {
                Log.error("Mapbox Directions lỗi, dùng ước lượng", e);
            }
        }
        // Ước lượng: đường thực tế ~1.3 lần đường chim bay, tốc độ trung bình 25 km/h
        double km = Geo.km(lat1, lng1, lat2, lng2) * 1.3;
        JsonArray points = new JsonArray();
        points.add(pair(lat1, lng1));
        points.add(pair(lat2, lng2));
        return Json.obj("distance_m", (int) Math.round(km * 1000), "duration_s", (int) Math.round(km / 25 * 3600),
                "points", points, "source", "estimate");
    }

    // ---------- tiện ích ----------

    private static JsonArray features(JsonObject res) {
        return res.has("features") && res.get("features").isJsonArray() ? res.getAsJsonArray("features") : new JsonArray();
    }

    /** {lat, lng} của một feature (GeoJSON lưu [lng, lat]). */
    private static double[] coords(JsonObject f) {
        JsonArray c = f.getAsJsonObject("geometry").getAsJsonArray("coordinates");
        return new double[]{c.get(1).getAsDouble(), c.get(0).getAsDouble()};
    }

    private static String address(JsonObject f) {
        JsonObject prop = f.has("properties") ? f.getAsJsonObject("properties") : new JsonObject();
        String full = Json.str(prop, "full_address", "");
        if (!full.isBlank()) return full;
        String name = Json.str(prop, "name", "");
        String ctx = Json.str(prop, "place_formatted", "");
        return ctx.isBlank() ? name : name + ", " + ctx;
    }

    private static JsonArray pair(double lat, double lng) {
        JsonArray a = new JsonArray();
        a.add(lat);
        a.add(lng);
        return a;
    }

    /** Mapbox dùng thứ tự kinh độ,vĩ độ. */
    private static String lngLat(double lat, double lng) { return String.format(Locale.US, "%.6f,%.6f", lng, lat); }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    static List<String> status() {
        return List.of(enabled() ? "Mapbox API: đã cấu hình" : "Mapbox API: CHƯA có token (tìm địa chỉ tắt, quãng đường ước lượng)");
    }
}
