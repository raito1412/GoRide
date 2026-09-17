package com.goride.server;

import com.goride.common.Config;
import com.goride.common.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public final class AdminService {
    private AdminService() {}

    // ---------- Dashboard ----------

    static JsonObject dashboard() {
        JsonObject o = new JsonObject();
        o.add("users", scalar("SELECT COUNT(*) v FROM users WHERE role='USER'"));
        o.add("drivers", scalar("SELECT COUNT(*) v FROM drivers"));
        o.add("drivers_pending", scalar("SELECT COUNT(*) v FROM drivers WHERE approval='PENDING'"));
        o.addProperty("drivers_online", Sessions.drivers().stream().filter(h -> h.driverOnline).count());
        o.add("rides_today", scalar("SELECT COUNT(*) v FROM rides WHERE DATE(created_at)=CURDATE()"));
        o.add("rides_active", scalar("SELECT COUNT(*) v FROM rides WHERE status IN ('SEARCHING','ACCEPTED','ARRIVED','PICKED_UP')"));
        o.add("rides_total", scalar("SELECT COUNT(*) v FROM rides WHERE status='COMPLETED'"));
        o.add("orders_today", scalar("SELECT COUNT(*) v FROM food_orders WHERE DATE(created_at)=CURDATE()"));
        o.add("orders_active", scalar("SELECT COUNT(*) v FROM food_orders WHERE status NOT IN ('DELIVERED','CANCELLED')"));
        o.add("orders_total", scalar("SELECT COUNT(*) v FROM food_orders WHERE status='DELIVERED'"));
        o.add("revenue_today", scalar("SELECT COALESCE(SUM(amount),0) v FROM payments WHERE status='PAID' AND DATE(created_at)=CURDATE()"));
        long revenue = scalar("SELECT COALESCE(SUM(amount),0) v FROM payments WHERE status='PAID'").getAsLong();
        o.addProperty("revenue_total", revenue);
        o.addProperty("commission_total", Math.round(revenue * (1 - Config.getDouble("DRIVER_SHARE", 0.8))));

        // doanh thu 7 ngày gần nhất theo loại
        Map<String, JsonObject> byDay = new HashMap<>();
        JsonArray days = new JsonArray();
        for (int i = 6; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).toString();
            JsonObject row = Json.obj("day", day, "RIDE", 0, "FOOD", 0);
            byDay.put(day, row);
            days.add(row);
        }
        for (JsonElement e : Db.query("""
                SELECT DATE_FORMAT(created_at, '%Y-%m-%d') d, ref_type, SUM(amount) v FROM payments
                WHERE status='PAID' AND created_at >= CURDATE() - INTERVAL 6 DAY GROUP BY d, ref_type""")) {
            JsonObject r = e.getAsJsonObject();
            JsonObject row = byDay.get(Json.str(r, "d"));
            if (row != null) row.add(Json.str(r, "ref_type"), r.get("v"));
        }
        o.add("last7", days);
        return o;
    }

    private static JsonElement scalar(String sql) { return Db.one(sql).get("v"); }

    // ---------- Danh sách ----------

    static JsonArray list(JsonObject d) {
        String entity = Json.req(d, "entity", "entity");
        return switch (entity) {
            case "users" -> Db.query("SELECT id, full_name, phone, email, address, status, created_at FROM users WHERE role='USER' ORDER BY id DESC");
            case "drivers" -> Db.query("""
                    SELECT u.id, u.full_name, u.phone, u.status, d.vehicle_type, d.plate_number, d.vehicle_brand,
                           d.vehicle_color, d.license_number, d.approval, d.is_online, d.rating_avg, d.rating_count, u.created_at
                    FROM users u JOIN drivers d ON d.user_id = u.id ORDER BY FIELD(d.approval,'PENDING','APPROVED','REJECTED'), u.id DESC""");
            case "rides" -> Db.query(RIDE_LIST + " ORDER BY r.id DESC LIMIT 300");
            case "rides_active" -> Db.query(RIDE_LIST + " WHERE r.status IN ('SEARCHING','ACCEPTED','ARRIVED','PICKED_UP') ORDER BY r.id DESC");
            case "restaurants" -> Db.query("SELECT * FROM restaurants ORDER BY id");
            case "menu" -> Db.query("SELECT * FROM menu_items WHERE restaurant_id=? ORDER BY name", Json.reqLong(d, "restaurant_id"));
            case "food_orders" -> Db.query("""
                    SELECT o.id, u.full_name AS user_name, r.name AS restaurant_name, du.full_name AS driver_name,
                           o.delivery_address, o.items_total, o.delivery_fee, o.total, o.payment_method, o.status, o.created_at
                    FROM food_orders o JOIN users u ON u.id=o.user_id JOIN restaurants r ON r.id=o.restaurant_id
                    LEFT JOIN users du ON du.id=o.driver_id ORDER BY o.id DESC LIMIT 300""");
            case "pricing" -> Db.query("SELECT * FROM pricing ORDER BY FIELD(service,'BIKE','CAR','DELIVERY')");
            case "payments" -> Db.query("""
                    SELECT p.id, u.full_name AS user_name, p.ref_type, p.ref_id, p.amount, p.method, p.status, p.created_at
                    FROM payments p JOIN users u ON u.id=p.user_id ORDER BY p.id DESC LIMIT 300""");
            case "reviews" -> Db.query("""
                    SELECT rv.id, u.full_name AS user_name, du.full_name AS driver_name, rv.ref_type, rv.ref_id,
                           rv.stars, rv.comment, rv.is_hidden, rv.created_at
                    FROM reviews rv JOIN users u ON u.id=rv.user_id JOIN users du ON du.id=rv.driver_id ORDER BY rv.id DESC""");
            case "online_drivers" -> onlineDrivers();
            default -> throw new AppException("Danh sách không hỗ trợ: " + entity);
        };
    }

    private static final String RIDE_LIST = """
            SELECT r.id, u.full_name AS user_name, du.full_name AS driver_name, r.vehicle_type,
                   r.pickup_address, r.drop_address, r.distance_m, r.price, r.payment_method, r.status, r.created_at
            FROM rides r JOIN users u ON u.id=r.user_id LEFT JOIN users du ON du.id=r.driver_id""";

    private static JsonArray onlineDrivers() {
        JsonArray out = new JsonArray();
        for (ClientHandler h : Sessions.drivers()) {
            if (!h.driverOnline) continue;
            double[] p = Sessions.LOCATIONS.get(h.userId);
            Sessions.Job job = Sessions.ACTIVE_JOBS.get(h.userId);
            out.add(Json.obj("id", h.userId, "full_name", h.fullName, "vehicle_type", h.vehicleType,
                    "lat", p == null ? null : p[0], "lng", p == null ? null : p[1],
                    "job", job == null ? "Rảnh" : (job.refType().equals("RIDE") ? "Chuyến #" : "Đơn #") + job.refId()));
        }
        return out;
    }

    // ---------- User / Driver ----------

    static JsonObject setUserStatus(JsonObject d) {
        long id = Json.reqLong(d, "id");
        String status = Json.req(d, "status", "Trạng thái");
        if (!status.equals("ACTIVE") && !status.equals("LOCKED")) throw new AppException("Trạng thái không hợp lệ");
        if (status.equals("LOCKED") && DriverJobs.busy(id)) throw new AppException("Tài xế đang có việc, hãy hủy việc trước");
        if (Db.update("UPDATE users SET status=? WHERE id=? AND role<>'ADMIN'", status, id) == 0)
            throw new AppException("Không tìm thấy tài khoản");
        if (status.equals("LOCKED")) {
            ClientHandler h = Sessions.get(id);
            if (h != null) h.kick("Tài khoản của bạn đã bị Admin khóa");
        }
        return Json.obj("id", id, "status", status);
    }

    static JsonObject setDriverApproval(JsonObject d) {
        long id = Json.reqLong(d, "id");
        String approval = Json.req(d, "approval", "Trạng thái duyệt");
        if (!approval.matches("PENDING|APPROVED|REJECTED")) throw new AppException("Trạng thái duyệt không hợp lệ");
        boolean ok = approval.equals("APPROVED");
        if (!ok && DriverJobs.busy(id)) throw new AppException("Tài xế đang có việc, không thể hủy duyệt lúc này");
        if (Db.update("UPDATE drivers SET approval=?" + (ok ? "" : ", is_online=0") + " WHERE user_id=?", approval, id) == 0)
            throw new AppException("Không tìm thấy tài xế");
        ClientHandler h = Sessions.get(id);
        if (h != null) {
            h.approved = ok;
            if (!ok) h.driverOnline = false;
            h.push("DRIVER_APPROVAL", Json.obj("approval", approval));
        }
        return Json.obj("id", id, "approval", approval);
    }

    static JsonObject updateVehicle(JsonObject d) {
        long id = Json.reqLong(d, "id");
        String type = AuthService.vehicleType(Json.req(d, "vehicle_type", "Loại xe"));
        if (DriverJobs.busy(id)) throw new AppException("Tài xế đang có việc, thử lại sau");
        Db.update("UPDATE drivers SET vehicle_type=?, plate_number=?, vehicle_brand=?, vehicle_color=?, license_number=? WHERE user_id=?",
                type, Json.req(d, "plate_number", "Biển số").toUpperCase(), Json.str(d, "vehicle_brand"),
                Json.str(d, "vehicle_color"), Json.str(d, "license_number"), id);
        ClientHandler h = Sessions.get(id);
        if (h != null) h.vehicleType = type;
        return Json.obj("id", id);
    }

    // ---------- Nhà hàng / menu ----------

    static JsonObject saveRestaurant(JsonObject d) {
        long id = Json.lng(d, "id", 0);
        String name = Json.req(d, "name", "Tên nhà hàng");
        String address = Json.req(d, "address", "Địa chỉ");
        double lat = Json.dbl(d, "lat", 0), lng = Json.dbl(d, "lng", 0);
        if (lat == 0 || lng == 0) {
            double[] p = MapboxClient.geocode(address);
            lat = p[0];
            lng = p[1];
        }
        Object[] params = {name, Json.str(d, "category"), address, lat, lng, Json.str(d, "phone"), Json.bool(d, "is_open", true) ? 1 : 0};
        if (id == 0) {
            id = Db.insert("INSERT INTO restaurants(name, category, address, lat, lng, phone, is_open) VALUES (?,?,?,?,?,?,?)", params);
        } else {
            Object[] p = java.util.Arrays.copyOf(params, params.length + 1);
            p[params.length] = id;
            Db.update("UPDATE restaurants SET name=?, category=?, address=?, lat=?, lng=?, phone=?, is_open=? WHERE id=?", p);
        }
        return Json.obj("id", id);
    }

    static JsonObject deleteRestaurant(long id) {
        try {
            Db.update("DELETE FROM restaurants WHERE id=?", id);
        } catch (RuntimeException e) {
            if (Db.isConstraintViolation(e))
                throw new AppException("Nhà hàng đã có đơn hàng, hãy chuyển sang tạm đóng thay vì xóa");
            throw e;
        }
        return Json.obj("id", id);
    }

    static JsonObject saveMenuItem(JsonObject d) {
        long id = Json.lng(d, "id", 0);
        String name = Json.req(d, "name", "Tên món");
        int price = Json.integer(d, "price", -1);
        if (price < 0) throw new AppException("Giá món không hợp lệ");
        int available = Json.bool(d, "is_available", true) ? 1 : 0;
        if (id == 0) {
            id = Db.insert("INSERT INTO menu_items(restaurant_id, name, description, price, is_available) VALUES (?,?,?,?,?)",
                    Json.reqLong(d, "restaurant_id"), name, Json.str(d, "description"), price, available);
        } else {
            Db.update("UPDATE menu_items SET name=?, description=?, price=?, is_available=? WHERE id=?",
                    name, Json.str(d, "description"), price, available, id);
        }
        return Json.obj("id", id);
    }

    static JsonObject deleteMenuItem(long id) {
        Db.update("DELETE FROM menu_items WHERE id=?", id);
        return Json.obj("id", id);
    }

    // ---------- Giá / thanh toán / review ----------

    static JsonObject savePricing(JsonObject d) {
        String service = Json.req(d, "service", "Dịch vụ");
        int base = Json.integer(d, "base_fare", -1), perKm = Json.integer(d, "per_km", -1);
        int perMin = Json.integer(d, "per_min", 0), min = Json.integer(d, "min_fare", -1);
        if (base < 0 || perKm < 0 || perMin < 0 || min < 0) throw new AppException("Giá không được âm");
        if (Db.update("UPDATE pricing SET base_fare=?, per_km=?, per_min=?, min_fare=? WHERE service=?",
                base, perKm, perMin, min, service) == 0) throw new AppException("Dịch vụ không tồn tại");
        return Json.obj("service", service);
    }

    static JsonObject setPaymentStatus(JsonObject d) {
        String status = Json.req(d, "status", "Trạng thái");
        if (!status.matches("PENDING|PAID|REFUNDED")) throw new AppException("Trạng thái không hợp lệ");
        Db.update("UPDATE payments SET status=? WHERE id=?", status, Json.reqLong(d, "id"));
        return Json.obj("status", status);
    }

    static JsonObject toggleReview(long id) {
        JsonObject r = Db.one("SELECT driver_id FROM reviews WHERE id=?", id);
        if (r == null) throw new AppException("Không tìm thấy đánh giá");
        Db.update("UPDATE reviews SET is_hidden = 1 - is_hidden WHERE id=?", id);
        AccountService.recalcRating(r.get("driver_id").getAsLong());
        return Json.obj("id", id);
    }
}
