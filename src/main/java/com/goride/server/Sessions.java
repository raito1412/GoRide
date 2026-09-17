package com.goride.server;

import com.goride.common.Geo;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Trạng thái realtime trong bộ nhớ: ai đang online, vị trí tài xế, tài xế đang làm việc gì. */
public final class Sessions {
    /** Việc tài xế đang làm: refType = RIDE | FOOD. */
    public record Job(String refType, long refId, long customerId) {}

    static final Map<Long, ClientHandler> ONLINE = new ConcurrentHashMap<>();
    static final Map<String, Long> UDP_TOKENS = new ConcurrentHashMap<>();
    static final Map<Long, double[]> LOCATIONS = new ConcurrentHashMap<>();
    static final Map<Long, Job> ACTIVE_JOBS = new ConcurrentHashMap<>();

    private Sessions() {}

    static void bind(ClientHandler h) {
        ClientHandler old = ONLINE.put(h.userId, h);
        if (old != null && old != h) old.kick("Tài khoản vừa đăng nhập ở nơi khác");
        if (h.udpToken != null) UDP_TOKENS.put(h.udpToken, h.userId);
    }

    static void unbind(ClientHandler h) {
        if (h.udpToken != null) UDP_TOKENS.remove(h.udpToken);
        ONLINE.remove(h.userId, h);
    }

    static ClientHandler get(long userId) { return ONLINE.get(userId); }

    static void push(long userId, String event, JsonObject data) {
        ClientHandler h = ONLINE.get(userId);
        if (h != null) h.push(event, data);
    }

    static List<ClientHandler> drivers() {
        return ONLINE.values().stream().filter(h -> "DRIVER".equals(h.role)).toList();
    }

    /** Tài xế đã duyệt, đang bật online và không bận. */
    static List<ClientHandler> availableDrivers() {
        return drivers().stream()
                .filter(h -> h.driverOnline && h.approved && !ACTIVE_JOBS.containsKey(h.userId))
                .toList();
    }

    static void pushDrivers(String event, JsonObject data, long exceptId) {
        for (ClientHandler h : drivers()) if (h.userId != exceptId) h.push(event, data);
    }

    /** null nếu chưa biết vị trí tài xế. */
    static Double distanceKm(long driverId, double lat, double lng) {
        double[] p = LOCATIONS.get(driverId);
        return p == null ? null : Geo.km(p[0], p[1], lat, lng);
    }

    static void addDriverLocation(JsonObject view) {
        if (!view.has("driver_id") || view.get("driver_id").isJsonNull()) return;
        double[] p = LOCATIONS.get(view.get("driver_id").getAsLong());
        if (p != null) {
            view.addProperty("driver_lat", p[0]);
            view.addProperty("driver_lng", p[1]);
        }
    }
}
