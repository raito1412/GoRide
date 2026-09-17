package com.goride.server;

import com.goride.common.Config;
import com.goride.common.Json;
import com.goride.common.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Đặt xe: SEARCHING -> ACCEPTED -> ARRIVED -> PICKED_UP -> COMPLETED (hoặc CANCELLED). */
public final class RideService {
    static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2);
    private static final int SEARCH_TIMEOUT_S = 120;
    private static final String ACTIVE = "('SEARCHING','ACCEPTED','ARRIVED','PICKED_UP')";
    /** trạng thái mới -> trạng thái bắt buộc trước đó */
    private static final Map<String, String> PREVIOUS = Map.of(
            "ARRIVED", "ACCEPTED", "PICKED_UP", "ARRIVED", "COMPLETED", "PICKED_UP");

    private static final String VIEW = """
            SELECT r.*, u.full_name AS user_name, u.phone AS user_phone,
                   du.full_name AS driver_name, du.phone AS driver_phone,
                   d.plate_number, d.vehicle_brand, d.vehicle_color, d.rating_avg AS driver_rating,
                   rv.stars AS my_stars
            FROM rides r
            JOIN users u ON u.id = r.user_id
            LEFT JOIN users du ON du.id = r.driver_id
            LEFT JOIN drivers d ON d.user_id = r.driver_id
            LEFT JOIN reviews rv ON rv.ref_type = 'RIDE' AND rv.ref_id = r.id
            """;

    private RideService() {}

    // ---------- Giá ----------

    /** Làm tròn tới 1.000đ. durationS = 0 nếu dịch vụ không tính theo phút. */
    static int price(String service, int distanceM, int durationS) {
        JsonObject p = Db.one("SELECT * FROM pricing WHERE service=?", service);
        if (p == null) throw new AppException("Chưa cấu hình giá cho dịch vụ " + service);
        double v = p.get("base_fare").getAsDouble()
                + p.get("per_km").getAsDouble() * distanceM / 1000.0
                + p.get("per_min").getAsDouble() * durationS / 60.0;
        v = Math.max(v, p.get("min_fare").getAsDouble());
        return (int) (Math.round(v / 1000.0) * 1000);
    }

    static JsonObject quote(JsonObject d) {
        double[] from = point(d, "pickup"), to = point(d, "drop");
        JsonObject bike = MapboxClient.route(from[0], from[1], to[0], to[1], "bike");
        int dist = bike.get("distance_m").getAsInt(), dur = bike.get("duration_s").getAsInt();
        bike.add("prices", Json.obj(
                "BIKE", price("BIKE", dist, dur),
                "CAR", price("CAR", dist, (int) (dur * 1.2))));
        return bike;
    }

    // ---------- User ----------

    static JsonObject book(ClientHandler c, JsonObject d) {
        String vehicle = AuthService.vehicleType(Json.str(d, "vehicle_type", "BIKE"));
        String payment = paymentMethod(Json.str(d, "payment_method", "CASH"));
        if (Db.one("SELECT id FROM rides WHERE user_id=? AND status IN " + ACTIVE, c.userId) != null)
            throw new AppException("Bạn đang có một chuyến chưa kết thúc");
        double[] from = point(d, "pickup"), to = point(d, "drop");
        JsonObject route = MapboxClient.route(from[0], from[1], to[0], to[1], vehicle.equals("CAR") ? "car" : "bike");
        int dist = route.get("distance_m").getAsInt(), dur = route.get("duration_s").getAsInt();
        if (dist < 200) throw new AppException("Điểm đón và điểm đến quá gần nhau");

        long id = Db.insert("""
                INSERT INTO rides(user_id, vehicle_type, pickup_address, pickup_lat, pickup_lng,
                                  drop_address, drop_lat, drop_lng, distance_m, duration_s, price, payment_method, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'SEARCHING')""",
                c.userId, vehicle, Json.req(d, "pickup_address", "Điểm đón"), from[0], from[1],
                Json.req(d, "drop_address", "Điểm đến"), to[0], to[1], dist, dur, price(vehicle, dist, dur), payment);
        Log.info("User #" + c.userId + " đặt chuyến #" + id + " (" + vehicle + ")");

        JsonObject ride = view(id);
        dispatch(ride);
        SCHEDULER.schedule(() -> safe(() -> timeout(id)), SEARCH_TIMEOUT_S, TimeUnit.SECONDS);
        ride.add("points", route.get("points"));
        return ride;
    }

    static JsonObject cancelByUser(ClientHandler c, JsonObject d) {
        long id = Json.reqLong(d, "id");
        int n = Db.update("UPDATE rides SET status='CANCELLED', cancel_reason='Khách hàng hủy' "
                + "WHERE id=? AND user_id=? AND status IN ('SEARCHING','ACCEPTED','ARRIVED')", id, c.userId);
        if (n == 0) throw new AppException("Không thể hủy: tài xế đã đón bạn hoặc chuyến đã kết thúc");
        return afterCancel(id, "Khách hàng đã hủy chuyến");
    }

    static JsonObject cancelByAdmin(long id) {
        int n = Db.update("UPDATE rides SET status='CANCELLED', cancel_reason='Admin hủy' WHERE id=? AND status IN " + ACTIVE, id);
        if (n == 0) throw new AppException("Chuyến đã kết thúc, không thể hủy");
        return afterCancel(id, "Admin đã hủy chuyến");
    }

    private static void timeout(long id) {
        int n = Db.update("UPDATE rides SET status='CANCELLED', cancel_reason='Không tìm thấy tài xế' "
                + "WHERE id=? AND status='SEARCHING'", id);
        if (n > 0) afterCancel(id, "Không tìm thấy tài xế phù hợp");
    }

    private static JsonObject afterCancel(long id, String reason) {
        JsonObject ride = view(id);
        if (Json.has(ride, "driver_id")) {
            long driverId = ride.get("driver_id").getAsLong();
            DriverJobs.release(driverId);
            Sessions.push(driverId, "RIDE_CANCELLED", Json.obj("id", id, "reason", reason));
        } else {
            Sessions.pushDrivers("RIDE_TAKEN", Json.obj("id", id), -1);
        }
        Sessions.push(ride.get("user_id").getAsLong(), "RIDE_UPDATE", ride);
        Log.info("Chuyến #" + id + " bị hủy: " + reason);
        return ride;
    }

    static JsonElement currentForUser(long userId) {
        JsonObject r = Db.one(VIEW + " WHERE r.user_id=? AND r.status IN " + ACTIVE + " ORDER BY r.id DESC LIMIT 1", userId);
        if (r == null) return JsonNull.INSTANCE;
        Sessions.addDriverLocation(r);
        return r;
    }

    static JsonArray history(String column, long id) {
        return Db.query(VIEW + " WHERE " + column + "=? ORDER BY r.id DESC LIMIT 100", id);
    }

    // ---------- Driver ----------

    /** Gửi yêu cầu tới tài xế cùng loại xe, rảnh, trong bán kính. */
    private static void dispatch(JsonObject ride) {
        long id = ride.get("id").getAsLong();
        int sent = 0;
        for (ClientHandler h : Sessions.availableDrivers()) {
            JsonObject offer = offerFor(h, ride);
            if (offer != null) {
                h.push("RIDE_REQUEST", offer);
                sent++;
            }
        }
        Log.info("Chuyến #" + id + " đã gửi tới " + sent + " tài xế");
    }

    private static JsonObject offerFor(ClientHandler h, JsonObject ride) {
        long id = ride.get("id").getAsLong();
        if (!ride.get("vehicle_type").getAsString().equals(h.vehicleType) || h.rejectedRides.contains(id)) return null;
        Double km = Sessions.distanceKm(h.userId, ride.get("pickup_lat").getAsDouble(), ride.get("pickup_lng").getAsDouble());
        if (km == null || km > Config.getDouble("MATCH_RADIUS_KM", 5)) return null;
        JsonObject offer = ride.deepCopy();
        offer.addProperty("kind", "RIDE");
        offer.addProperty("distance_to_start_km", Math.round(km * 10) / 10.0);
        return offer;
    }

    static JsonArray pendingFor(ClientHandler c) {
        JsonArray out = new JsonArray();
        for (JsonElement e : Db.query(VIEW + " WHERE r.status='SEARCHING' ORDER BY r.id DESC LIMIT 30")) {
            JsonObject offer = offerFor(c, e.getAsJsonObject());
            if (offer != null) out.add(offer);
        }
        return out;
    }

    static JsonObject accept(ClientHandler c, JsonObject d) {
        long id = Json.reqLong(d, "id");
        DriverJobs.claim(c, "RIDE", id);
        int n;
        try {
            n = Db.update("UPDATE rides SET driver_id=?, status='ACCEPTED' "
                    + "WHERE id=? AND status='SEARCHING' AND driver_id IS NULL AND vehicle_type=?", c.userId, id, c.vehicleType);
        } catch (RuntimeException e) {
            DriverJobs.release(c.userId);
            throw e;
        }
        if (n == 0) {
            DriverJobs.release(c.userId);
            throw new AppException("Chuyến này đã có tài xế khác nhận hoặc đã bị hủy");
        }
        JsonObject ride = view(id);
        long userId = ride.get("user_id").getAsLong();
        Sessions.ACTIVE_JOBS.put(c.userId, new Sessions.Job("RIDE", id, userId));
        Sessions.addDriverLocation(ride);
        Sessions.push(userId, "RIDE_UPDATE", ride);
        Sessions.pushDrivers("RIDE_TAKEN", Json.obj("id", id), c.userId);
        Log.info("Tài xế #" + c.userId + " nhận chuyến #" + id);
        return ride;
    }

    static JsonObject driverStatus(ClientHandler c, JsonObject d) {
        long id = Json.reqLong(d, "id");
        String next = Json.req(d, "status", "Trạng thái");
        String prev = PREVIOUS.get(next);
        if (prev == null) throw new AppException("Trạng thái không hợp lệ");
        String sql = "UPDATE rides SET status=? WHERE id=? AND driver_id=? AND status=?";
        if (next.equals("COMPLETED")) {
            Db.tx(conn -> {
                if (Db.update(conn, sql, next, id, c.userId, prev) == 0) throw new AppException("Chuyến không ở trạng thái phù hợp");
                JsonObject r = Db.one(conn, "SELECT user_id, price, payment_method FROM rides WHERE id=?", id);
                Db.insert(conn, "INSERT INTO payments(user_id, ref_type, ref_id, amount, method, status) VALUES (?, 'RIDE', ?, ?, ?, 'PAID')",
                        r.get("user_id").getAsLong(), id, r.get("price").getAsInt(), r.get("payment_method").getAsString());
                return null;
            });
            DriverJobs.release(c.userId);
        } else if (Db.update(sql, next, id, c.userId, prev) == 0) {
            throw new AppException("Chuyến không ở trạng thái phù hợp");
        }
        JsonObject ride = view(id);
        Sessions.addDriverLocation(ride);
        Sessions.push(ride.get("user_id").getAsLong(), "RIDE_UPDATE", ride);
        return ride;
    }

    static JsonObject currentForDriver(long driverId) {
        return Db.one(VIEW + " WHERE r.driver_id=? AND r.status IN ('ACCEPTED','ARRIVED','PICKED_UP') ORDER BY r.id DESC LIMIT 1", driverId);
    }

    // ---------- Chung ----------

    static JsonObject view(long id) {
        JsonObject r = Db.one(VIEW + " WHERE r.id=?", id);
        if (r == null) throw new AppException("Không tìm thấy chuyến #" + id);
        return r;
    }

    static String paymentMethod(String m) {
        if (!"CASH".equals(m) && !"WALLET".equals(m)) throw new AppException("Phương thức thanh toán không hợp lệ");
        return m;
    }

    private static double[] point(JsonObject d, String prefix) {
        return new double[]{Json.reqDbl(d, prefix + "_lat"), Json.reqDbl(d, prefix + "_lng")};
    }

    static void safe(Runnable r) {
        try { r.run(); } catch (RuntimeException e) { Log.error("Tác vụ nền lỗi", e); }
    }
}
