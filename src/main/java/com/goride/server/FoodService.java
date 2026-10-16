package com.goride.server;

import com.goride.common.Config;
import com.goride.common.Json;
import com.goride.common.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Đặt đồ ăn: PLACED -> (nhà hàng giả lập tự xác nhận) CONFIRMED -> ACCEPTED -> AT_RESTAURANT
 *            -> PICKED_UP -> DELIVERING -> DELIVERED (hoặc CANCELLED).
 */
public final class FoodService {
    private static final int CONFIRM_DELAY_S = 3;
    private static final int SEARCH_TIMEOUT_S = 180;
    private static final int MAX_DELIVERY_M = 20_000;
    private static final String ACTIVE = "('PLACED','CONFIRMED','ACCEPTED','AT_RESTAURANT','PICKED_UP','DELIVERING')";
    private static final Map<String, String> PREVIOUS = Map.of(
            "AT_RESTAURANT", "ACCEPTED", "PICKED_UP", "AT_RESTAURANT",
            "DELIVERING", "PICKED_UP", "DELIVERED", "DELIVERING");

    private static final String VIEW = """
            SELECT o.*, r.name AS restaurant_name, r.address AS restaurant_address,
                   r.lat AS restaurant_lat, r.lng AS restaurant_lng, r.phone AS restaurant_phone,
                   r.owner_id AS owner_id,
                   u.full_name AS user_name, u.phone AS user_phone,
                   du.full_name AS driver_name, du.phone AS driver_phone,
                   d.plate_number, d.vehicle_brand, d.rating_avg AS driver_rating,
                   rv.stars AS my_stars, rs.stars AS my_shop_stars
            FROM food_orders o
            JOIN restaurants r ON r.id = o.restaurant_id
            JOIN users u ON u.id = o.user_id
            LEFT JOIN users du ON du.id = o.driver_id
            LEFT JOIN drivers d ON d.user_id = o.driver_id
            LEFT JOIN reviews rv ON rv.ref_type = 'FOOD' AND rv.ref_id = o.id AND rv.target = 'DRIVER'
            LEFT JOIN reviews rs ON rs.ref_type = 'FOOD' AND rs.ref_id = o.id AND rs.target = 'RESTAURANT'
            """;

    private record Calc(JsonObject restaurant, List<JsonObject> lines, int itemsTotal, int fee, JsonObject route) {}

    private FoodService() {}

    static JsonArray restaurants() {
        return Db.query("SELECT id, name, category, address, lat, lng, phone, description, image_id, "
                + "rating_avg, rating_count FROM restaurants WHERE is_open=1 ORDER BY name");
    }

    static JsonArray menu(long restaurantId) {
        return Db.query("SELECT id, restaurant_id, name, description, price, image_id FROM menu_items "
                + "WHERE restaurant_id=? AND is_available=1 ORDER BY name", restaurantId);
    }

    /** Tính tiền phía server — không tin giá client gửi lên. */
    private static Calc calc(JsonObject d) {
        long restaurantId = Json.reqLong(d, "restaurant_id");
        JsonObject rest = Db.one("SELECT * FROM restaurants WHERE id=?", restaurantId);
        if (rest == null) throw new AppException("Nhà hàng không tồn tại");
        if (!Json.bool(rest, "is_open", false)) throw new AppException("Nhà hàng đang tạm đóng cửa");
        JsonArray items = d.has("items") && d.get("items").isJsonArray() ? d.getAsJsonArray("items") : new JsonArray();
        if (items.size() == 0) throw new AppException("Giỏ hàng đang trống");

        List<JsonObject> lines = new ArrayList<>();
        int total = 0;
        for (JsonElement e : items) {
            JsonObject it = e.getAsJsonObject();
            int qty = Json.integer(it, "quantity", 1);
            if (qty < 1 || qty > 50) throw new AppException("Số lượng mỗi món từ 1 đến 50");
            JsonObject m = Db.one("SELECT id, name, price, is_available FROM menu_items WHERE id=? AND restaurant_id=?",
                    Json.reqLong(it, "menu_item_id"), restaurantId);
            if (m == null) throw new AppException("Có món không thuộc nhà hàng này");
            if (!Json.bool(m, "is_available", false)) throw new AppException("Món \"" + Json.str(m, "name") + "\" đã hết");
            int price = m.get("price").getAsInt();
            total += price * qty;
            lines.add(Json.obj("menu_item_id", m.get("id").getAsLong(), "item_name", Json.str(m, "name"),
                    "unit_price", price, "quantity", qty));
        }
        JsonObject route = MapboxClient.route(rest.get("lat").getAsDouble(), rest.get("lng").getAsDouble(),
                Json.reqDbl(d, "delivery_lat"), Json.reqDbl(d, "delivery_lng"), "bike");
        int dist = route.get("distance_m").getAsInt();
        if (dist > MAX_DELIVERY_M) throw new AppException("Địa chỉ giao quá xa nhà hàng (tối đa 20 km)");
        return new Calc(rest, lines, total, RideService.price("DELIVERY", dist, 0), route);
    }

    static JsonObject quote(ClientHandler client, JsonObject d) {
        Calc c = calc(d);
        int gross = c.itemsTotal + c.fee;
        int discount = VoucherService.discountFor(client.userId, Json.str(d, "voucher_code"), "FOOD", gross);
        JsonObject o = Json.obj("items_total", c.itemsTotal, "delivery_fee", c.fee,
                "discount", discount, "total", gross - discount);
        c.route.entrySet().forEach(en -> o.add(en.getKey(), en.getValue()));
        return o;
    }

    static JsonObject order(ClientHandler client, JsonObject d) {
        if (Db.one("SELECT id FROM food_orders WHERE user_id=? AND status IN " + ACTIVE, client.userId) != null)
            throw new AppException("Bạn đang có một đơn đồ ăn chưa hoàn tất");
        String payment = RideService.paymentMethod(Json.str(d, "payment_method", "CASH"));
        String address = Json.req(d, "delivery_address", "Địa chỉ giao hàng");
        Calc c = calc(d);
        JsonObject voucher = VoucherService.lookup(client.userId, Json.str(d, "voucher_code"), "FOOD");
        int discount = VoucherService.amount(voucher, c.itemsTotal + c.fee);
        long id = Db.tx(conn -> {
            long oid = Db.insert(conn, """
                    INSERT INTO food_orders(user_id, restaurant_id, delivery_address, delivery_lat, delivery_lng, note,
                                            items_total, delivery_fee, discount, voucher_code, total,
                                            distance_m, duration_s, payment_method, status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PLACED')""",
                    client.userId, c.restaurant.get("id").getAsLong(), address,
                    Json.reqDbl(d, "delivery_lat"), Json.reqDbl(d, "delivery_lng"), Json.str(d, "note"),
                    c.itemsTotal, c.fee, discount, voucher == null ? null : Json.str(voucher, "code"),
                    c.itemsTotal + c.fee - discount,
                    c.route.get("distance_m").getAsInt(), c.route.get("duration_s").getAsInt(), payment);
            VoucherService.markUsed(conn, voucher, "FOOD", oid);
            for (JsonObject l : c.lines) {
                Db.insert(conn, "INSERT INTO food_order_items(order_id, menu_item_id, item_name, unit_price, quantity) VALUES (?,?,?,?,?)",
                        oid, l.get("menu_item_id").getAsLong(), Json.str(l, "item_name"),
                        l.get("unit_price").getAsInt(), l.get("quantity").getAsInt());
            }
            return oid;
        });
        Log.info("User #" + client.userId + " đặt đơn đồ ăn #" + id);
        RideService.SCHEDULER.schedule(() -> RideService.safe(() -> autoConfirm(id)), CONFIRM_DELAY_S, TimeUnit.SECONDS);
        RideService.SCHEDULER.schedule(() -> RideService.safe(() -> timeout(id)), SEARCH_TIMEOUT_S, TimeUnit.SECONDS);
        JsonObject o = view(id);
        MerchantService.notifyOwner(o, "NEW");
        o.add("points", c.route.get("points"));
        return o;
    }

    /**
     * Chủ quán đang mở app thì chờ họ tự bấm nhận đơn. Quán chưa mở app (hoặc quán chưa có chủ) thì
     * hệ thống xác nhận hộ để khách không phải đợi vô ích — giống chế độ giả lập trước đây.
     */
    private static void autoConfirm(long id) {
        JsonObject r = Db.one("SELECT r.owner_id FROM food_orders o JOIN restaurants r ON r.id = o.restaurant_id "
                + "WHERE o.id=?", id);
        if (r != null && Json.has(r, "owner_id") && Sessions.get(r.get("owner_id").getAsLong()) != null) return;
        confirm(id);
    }

    /** Quán xác nhận đơn (tự bấm hoặc hệ thống làm hộ) rồi đẩy yêu cầu cho tài xế gần quán. */
    static JsonObject confirm(long id) {
        if (Db.update("UPDATE food_orders SET status='CONFIRMED' WHERE id=? AND status='PLACED'", id) == 0) return null;
        JsonObject o = view(id);
        Sessions.push(o.get("user_id").getAsLong(), "FOOD_UPDATE", o);
        MerchantService.notifyOwner(o, "UPDATE");
        int sent = 0;
        for (ClientHandler h : Sessions.driversFreeForFood()) {
            JsonObject offer = offerFor(h, o);
            if (offer != null) {
                h.push("FOOD_REQUEST", offer);
                sent++;
            }
        }
        Log.info("Đơn #" + id + " đã gửi tới " + sent + " tài xế");
        return o;
    }

    private static void timeout(long id) {
        // quán chưa bấm nhận thì lý do khác với trường hợp quán đã nhận mà không có tài xế
        if (Db.update("UPDATE food_orders SET status='CANCELLED', cancel_reason='Quán không xác nhận đơn kịp' "
                + "WHERE id=? AND status='PLACED'", id) > 0) {
            afterCancel(id, "Quán không xác nhận đơn kịp");
            return;
        }
        int n = Db.update("UPDATE food_orders SET status='CANCELLED', cancel_reason='Không tìm thấy tài xế' "
                + "WHERE id=? AND status='CONFIRMED' AND driver_id IS NULL", id);
        if (n > 0) afterCancel(id, "Không tìm thấy tài xế giao hàng");
    }

    static JsonObject cancelByUser(ClientHandler c, JsonObject d) {
        long id = Json.reqLong(d, "id");
        int n = Db.update("UPDATE food_orders SET status='CANCELLED', cancel_reason='Khách hàng hủy' "
                + "WHERE id=? AND user_id=? AND status IN ('PLACED','CONFIRMED','ACCEPTED')", id, c.userId);
        if (n == 0) throw new AppException("Không thể hủy: tài xế đã tới nhà hàng hoặc đơn đã kết thúc");
        return afterCancel(id, "Khách hàng đã hủy đơn");
    }

    static JsonObject cancelByAdmin(long id) {
        int n = Db.update("UPDATE food_orders SET status='CANCELLED', cancel_reason='Admin hủy' WHERE id=? AND status IN " + ACTIVE, id);
        if (n == 0) throw new AppException("Đơn đã kết thúc, không thể hủy");
        return afterCancel(id, "Admin đã hủy đơn");
    }

    static JsonObject afterCancel(long id, String reason) {
        VoucherService.release("FOOD", id);
        JsonObject o = view(id);
        if (Json.has(o, "driver_id")) {
            long driverId = o.get("driver_id").getAsLong();
            DriverJobs.release(driverId, "FOOD", id);
            Sessions.push(driverId, "FOOD_CANCELLED", Json.obj("id", id, "reason", reason));
        } else {
            Sessions.pushDrivers("FOOD_TAKEN", Json.obj("id", id), -1);
        }
        Sessions.push(o.get("user_id").getAsLong(), "FOOD_UPDATE", o);
        MerchantService.notifyOwner(o, "UPDATE");
        Log.info("Đơn #" + id + " bị hủy: " + reason);
        return o;
    }

    static JsonElement currentForUser(long userId) {
        JsonObject row = Db.one("SELECT id FROM food_orders WHERE user_id=? AND status IN " + ACTIVE + " ORDER BY id DESC LIMIT 1", userId);
        if (row == null) return JsonNull.INSTANCE;
        JsonObject o = view(row.get("id").getAsLong());
        Sessions.addDriverLocation(o);
        return o;
    }

    static JsonArray history(String column, long id) {
        return Db.query(VIEW + " WHERE " + column + "=? ORDER BY o.id DESC LIMIT 100", id);
    }

    static JsonObject detailForUser(long userId, long id) {
        JsonObject o = view(id);
        if (o.get("user_id").getAsLong() != userId) throw new AppException("Không tìm thấy đơn");
        return o;
    }

    // ---------- Driver ----------

    private static JsonObject offerFor(ClientHandler h, JsonObject o) {
        long id = o.get("id").getAsLong();
        if (h.rejectedOrders.contains(id) || !Sessions.freeForFood(h.userId)) return null;
        Double km = Sessions.distanceKm(h.userId, o.get("restaurant_lat").getAsDouble(), o.get("restaurant_lng").getAsDouble());
        if (km == null || km > Config.getDouble("MATCH_RADIUS_KM", 5)) return null;
        JsonObject offer = o.deepCopy();
        offer.addProperty("kind", "FOOD");
        offer.addProperty("distance_to_start_km", Math.round(km * 10) / 10.0);
        return offer;
    }

    static JsonArray pendingFor(ClientHandler c) {
        JsonArray out = new JsonArray();
        for (JsonElement e : Db.query(VIEW + " WHERE o.status='CONFIRMED' AND o.driver_id IS NULL ORDER BY o.id DESC LIMIT 30")) {
            JsonObject offer = offerFor(c, e.getAsJsonObject());
            if (offer != null) out.add(offer);
        }
        return out;
    }

    static JsonObject accept(ClientHandler c, JsonObject d) {
        long id = Json.reqLong(d, "id");
        DriverJobs.claim(c, "FOOD", id);
        int n;
        try {
            n = Db.update("UPDATE food_orders SET driver_id=?, status='ACCEPTED' "
                    + "WHERE id=? AND status='CONFIRMED' AND driver_id IS NULL", c.userId, id);
        } catch (RuntimeException e) {
            DriverJobs.release(c.userId, "FOOD", id);
            throw e;
        }
        if (n == 0) {
            DriverJobs.release(c.userId, "FOOD", id);
            throw new AppException("Đơn này đã có tài xế khác nhận hoặc đã bị hủy");
        }
        JsonObject o = view(id);
        long userId = o.get("user_id").getAsLong();
        DriverJobs.attachCustomer(c.userId, "FOOD", id, userId);
        Sessions.addDriverLocation(o);
        Sessions.push(userId, "FOOD_UPDATE", o);
        MerchantService.notifyOwner(o, "UPDATE");
        Sessions.pushDrivers("FOOD_TAKEN", Json.obj("id", id), c.userId);
        Log.info("Tài xế #" + c.userId + " nhận đơn #" + id);
        return o;
    }

    static JsonObject driverStatus(ClientHandler c, JsonObject d) {
        long id = Json.reqLong(d, "id");
        String next = Json.req(d, "status", "Trạng thái");
        String prev = PREVIOUS.get(next);
        if (prev == null) throw new AppException("Trạng thái không hợp lệ");
        String sql = "UPDATE food_orders SET status=? WHERE id=? AND driver_id=? AND status=?";
        if (next.equals("AT_RESTAURANT") || next.equals("DELIVERED")) {
            JsonObject p = Db.one("SELECT r.lat AS rlat, r.lng AS rlng, o.delivery_lat, o.delivery_lng FROM food_orders o "
                    + "JOIN restaurants r ON r.id = o.restaurant_id WHERE o.id=? AND o.driver_id=?", id, c.userId);
            if (p == null) throw new AppException("Không tìm thấy đơn #" + id);
            boolean shop = next.equals("AT_RESTAURANT");
            Sessions.requireNear(c.userId, Json.dbl(p, shop ? "rlat" : "delivery_lat", 0),
                    Json.dbl(p, shop ? "rlng" : "delivery_lng", 0), shop ? "quán" : "nhà khách");
        }
        if (next.equals("DELIVERED")) {
            Db.tx(conn -> {
                if (Db.update(conn, sql, next, id, c.userId, prev) == 0) throw new AppException("Đơn không ở trạng thái phù hợp");
                JsonObject o = Db.one(conn, "SELECT user_id, total, payment_method FROM food_orders WHERE id=?", id);
                Db.insert(conn, "INSERT INTO payments(user_id, ref_type, ref_id, amount, method, status) VALUES (?, 'FOOD', ?, ?, ?, 'PAID')",
                        o.get("user_id").getAsLong(), id, o.get("total").getAsInt(), o.get("payment_method").getAsString());
                return null;
            });
            DriverJobs.release(c.userId, "FOOD", id);
        } else if (Db.update(sql, next, id, c.userId, prev) == 0) {
            throw new AppException("Đơn không ở trạng thái phù hợp");
        }
        JsonObject o = view(id);
        Sessions.addDriverLocation(o);
        Sessions.push(o.get("user_id").getAsLong(), "FOOD_UPDATE", o);
        MerchantService.notifyOwner(o, "UPDATE");
        return o;
    }

    /** Mọi đơn tài xế đang giữ (giao gộp tối đa Sessions.MAX_FOOD_BATCH đơn), cũ trước mới sau. */
    static JsonArray activeForDriver(long driverId) {
        JsonArray out = new JsonArray();
        for (JsonElement e : Db.query("SELECT id FROM food_orders WHERE driver_id=? "
                + "AND status IN ('ACCEPTED','AT_RESTAURANT','PICKED_UP','DELIVERING') ORDER BY id", driverId)) {
            JsonObject o = view(e.getAsJsonObject().get("id").getAsLong());
            Sessions.addDriverLocation(o);
            out.add(o);
        }
        return out;
    }

    static JsonObject view(long id) {
        JsonObject o = Db.one(VIEW + " WHERE o.id=?", id);
        if (o == null) throw new AppException("Không tìm thấy đơn #" + id);
        o.add("items", Db.query("SELECT item_name, unit_price, quantity FROM food_order_items WHERE order_id=?", id));
        return o;
    }
}
