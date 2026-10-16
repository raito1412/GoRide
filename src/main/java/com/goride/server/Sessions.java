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
    /** Tài xế được giữ cùng lúc tối đa 1 chuyến xe, HOẶC tối đa ngần này đơn đồ ăn (giao gộp). */
    static final int MAX_FOOD_BATCH = 3;
    /** Các việc tài xế đang giữ. Danh sách bất biến, luôn thay cả cụm qua compute() để an toàn đa luồng. */
    static final Map<Long, List<Job>> ACTIVE_JOBS = new ConcurrentHashMap<>();

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

    static List<Job> jobs(long driverId) { return ACTIVE_JOBS.getOrDefault(driverId, List.of()); }

    static boolean hasRide(long driverId) {
        return jobs(driverId).stream().anyMatch(j -> j.refType().equals("RIDE"));
    }

    static int foodCount(long driverId) {
        return (int) jobs(driverId).stream().filter(j -> j.refType().equals("FOOD")).count();
    }

    /** Nhận được chuyến xe: đang rảnh hoàn toàn. */
    static boolean freeForRide(long driverId) { return jobs(driverId).isEmpty(); }

    /** Nhận được thêm đơn đồ ăn: không chở khách và chưa đủ MAX_FOOD_BATCH đơn. */
    static boolean freeForFood(long driverId) { return !hasRide(driverId) && foodCount(driverId) < MAX_FOOD_BATCH; }

    private static List<ClientHandler> ready() {
        return drivers().stream().filter(h -> h.driverOnline && h.approved).toList();
    }

    static List<ClientHandler> driversFreeForRide() {
        return ready().stream().filter(h -> freeForRide(h.userId)).toList();
    }

    static List<ClientHandler> driversFreeForFood() {
        return ready().stream().filter(h -> freeForFood(h.userId)).toList();
    }

    static void pushDrivers(String event, JsonObject data, long exceptId) {
        for (ClientHandler h : drivers()) if (h.userId != exceptId) h.push(event, data);
    }

    /** null nếu chưa biết vị trí tài xế. */
    static Double distanceKm(long driverId, double lat, double lng) {
        double[] p = LOCATIONS.get(driverId);
        return p == null ? null : Geo.km(p[0], p[1], lat, lng);
    }

    /**
     * Các bước "đã tới / hoàn thành / đã giao" chỉ hợp lệ khi xe thật sự ở gần điểm đó. Vị trí lấy từ GPS
     * server đang giữ (UDP 2 giây/lần), nên cho sai số ARRIVE_RADIUS_KM (mặc định 300 m).
     */
    static void requireNear(long driverId, double lat, double lng, String place) {
        Double km = distanceKm(driverId, lat, lng);
        if (km == null) throw new AppException("Chưa nhận được vị trí GPS của bạn, hãy bật Online rồi thử lại");
        double max = com.goride.common.Config.getDouble("ARRIVE_RADIUS_KM", 0.3);
        if (km > max) throw new AppException(String.format("Bạn chưa tới %s (còn cách %.1f km)", place, km));
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
