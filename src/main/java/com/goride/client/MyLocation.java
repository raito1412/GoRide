package com.goride.client;

import com.goride.common.Config;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Máy tính không có GPS nên "vị trí hiện tại" lấy gần đúng theo IP (ip-api.com, không cần key).
 * Không lấy được thì dùng MAP_DEFAULT_LAT/LNG. Người dùng có thể bấm lên bản đồ để chỉnh lại.
 */
public final class MyLocation {
    private MyLocation() {}

    /** Gọi trên luồng nền. */
    public static double[] approximate() {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://ip-api.com/json/?fields=status,lat,lon"))
                    .timeout(Duration.ofSeconds(4)).build();
            JsonObject o = JsonParser.parseString(http.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
            if ("success".equals(o.get("status").getAsString()))
                return new double[]{o.get("lat").getAsDouble(), o.get("lon").getAsDouble()};
        } catch (Exception ignored) {
            // dùng mặc định
        }
        return new double[]{Config.defaultLat(), Config.defaultLng()};
    }
}
