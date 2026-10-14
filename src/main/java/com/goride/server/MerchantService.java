package com.goride.server;

import com.goride.common.Json;
import com.goride.common.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Đăng ký mở quán ăn: khách gửi hồ sơ (thông tin quán + thực đơn kèm ảnh) -> trạng thái PENDING
 * -> Admin duyệt thì hệ thống tạo restaurants + menu_items thật, từ chối thì kèm lý do.
 */
public final class MerchantService {
    private static final int MAX_ITEMS = 60;
    private static final String LIST = """
            SELECT a.id, a.user_id, a.name, a.category, a.address, a.phone, a.status, a.admin_note,
                   a.restaurant_id, a.owner_name, a.owner_phone, a.owner_email, a.created_at, a.reviewed_at,
                   (SELECT COUNT(*) FROM restaurant_application_items i WHERE i.application_id = a.id) AS item_count
            FROM restaurant_applications a""";

    private MerchantService() {}

    // ---------- Phía khách hàng ----------

    static JsonObject apply(ClientHandler c, JsonObject d) {
        if (Db.one("SELECT id FROM restaurant_applications WHERE user_id=? AND status='PENDING'", c.userId) != null)
            throw new AppException("Bạn đang có một hồ sơ chờ duyệt. Hãy đợi Admin xử lý hoặc rút hồ sơ cũ.");

        String name = Json.req(d, "name", "Tên quán");
        String address = Json.req(d, "address", "Địa chỉ quán");
        String phone = Json.str(d, "phone", "").trim();
        if (!phone.isEmpty() && !phone.matches("0\\d{9,10}"))
            throw new AppException("Số điện thoại quán phải gồm 10-11 chữ số, bắt đầu bằng 0");
        String description = Json.str(d, "description", "").trim();
        if (description.length() > 500) throw new AppException("Mô tả quán tối đa 500 ký tự");
        Long imageId = Images.optionalId(d, "image_id");

        double lat = Json.dbl(d, "lat", 0), lng = Json.dbl(d, "lng", 0);
        if (lat == 0 || lng == 0) {
            double[] p = MapboxClient.geocode(address);
            lat = p[0];
            lng = p[1];
        }

        List<JsonObject> items = items(d);
        JsonObject me = AuthService.profile(c.userId);
        double finalLat = lat, finalLng = lng;
        long id = Db.tx(conn -> {
            long appId = Db.insert(conn, """
                    INSERT INTO restaurant_applications(user_id, name, category, address, lat, lng, phone, description,
                                                        image_id, owner_name, owner_phone, owner_email)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                    c.userId, name, blankToNull(Json.str(d, "category", "")), address, finalLat, finalLng,
                    blankToNull(phone), blankToNull(description), imageId,
                    Json.str(me, "full_name"), Json.str(me, "phone"), Json.str(me, "email"));
            for (JsonObject it : items) {
                Db.insert(conn, "INSERT INTO restaurant_application_items(application_id, name, description, price, image_id) VALUES (?,?,?,?,?)",
                        appId, Json.str(it, "name"), blankToNull(Json.str(it, "description", "")),
                        it.get("price").getAsInt(), Json.has(it, "image_id") ? it.get("image_id").getAsLong() : null);
            }
            return appId;
        });
        Log.info("User #" + c.userId + " gửi hồ sơ mở quán #" + id + " (" + name + ", " + items.size() + " món)");
        JsonObject out = detail(id);
        out.addProperty("message", "Đã gửi hồ sơ. Quán của bạn sẽ hiển thị sau khi Admin duyệt.");
        return out;
    }

    /** Đọc và kiểm tra danh sách món trong hồ sơ. */
    private static List<JsonObject> items(JsonObject d) {
        JsonArray arr = d.has("items") && d.get("items").isJsonArray() ? d.getAsJsonArray("items") : new JsonArray();
        if (arr.size() == 0) throw new AppException("Thực đơn phải có ít nhất 1 món");
        if (arr.size() > MAX_ITEMS) throw new AppException("Thực đơn tối đa " + MAX_ITEMS + " món");
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject it = e.getAsJsonObject();
            String name = Json.req(it, "name", "Tên món");
            int price = Json.integer(it, "price", -1);
            if (price <= 0) throw new AppException("Giá món \"" + name + "\" phải lớn hơn 0");
            if (price > 100_000_000) throw new AppException("Giá món \"" + name + "\" không hợp lệ");
            JsonObject row = Json.obj("name", name, "description", Json.str(it, "description", ""), "price", price);
            Long img = Images.optionalId(it, "image_id");
            if (img != null) row.addProperty("image_id", img);
            out.add(row);
        }
        return out;
    }

    static JsonArray mine(long userId) {
        return Db.query(LIST + " WHERE a.user_id=? ORDER BY a.id DESC LIMIT 50", userId);
    }

    static JsonObject mineDetail(long userId, long id) {
        JsonObject o = detail(id);
        if (o.get("user_id").getAsLong() != userId) throw new AppException("Không tìm thấy hồ sơ");
        return o;
    }

    /** Rút lại hồ sơ khi Admin chưa duyệt. */
    static JsonObject withdraw(long userId, long id) {
        if (Db.update("DELETE FROM restaurant_applications WHERE id=? AND user_id=? AND status='PENDING'", id, userId) == 0)
            throw new AppException("Chỉ rút được hồ sơ đang chờ duyệt");
        return Json.obj("id", id);
    }

    // ---------- Quán đã được duyệt của chủ quán ----------

    static JsonArray myShops(long userId) {
        return Db.query("""
                SELECT r.id, r.name, r.category, r.address, r.phone, r.is_open, r.image_id,
                       r.rating_avg, r.rating_count,
                       (SELECT COUNT(*) FROM menu_items m WHERE m.restaurant_id = r.id) AS item_count,
                       (SELECT COUNT(*) FROM food_orders o WHERE o.restaurant_id = r.id
                          AND o.status NOT IN ('DELIVERED','CANCELLED')) AS active_orders
                FROM restaurants r WHERE r.owner_id=? ORDER BY r.id""", userId);
    }

    /** Đơn đang đổ về các quán của chủ quán này. */
    static JsonArray shopOrders(long userId) {
        return Db.query("""
                SELECT o.id, o.status, o.created_at, o.items_total, o.delivery_fee, o.discount, o.total,
                       o.payment_method, o.note, o.delivery_address,
                       r.name AS restaurant_name, u.full_name AS user_name, u.phone AS user_phone,
                       du.full_name AS driver_name,
                       (SELECT GROUP_CONCAT(CONCAT(i.quantity, ' × ', i.item_name) SEPARATOR ', ')
                          FROM food_order_items i WHERE i.order_id = o.id) AS items_text
                FROM food_orders o
                JOIN restaurants r ON r.id = o.restaurant_id
                JOIN users u ON u.id = o.user_id
                LEFT JOIN users du ON du.id = o.driver_id
                WHERE r.owner_id=? ORDER BY o.id DESC LIMIT 100""", userId);
    }

    /** Chủ quán bấm nhận đơn -> đơn chuyển sang tìm tài xế. */
    static JsonObject acceptOrder(ClientHandler c, long orderId) {
        ownOrder(c.userId, orderId);
        JsonObject o = FoodService.confirm(orderId);
        if (o == null) throw new AppException("Đơn này đã được xử lý hoặc đã bị hủy");
        return o;
    }

    /** Chủ quán từ chối đơn (hết món, quá tải...) — chỉ được khi chưa nhận. */
    static JsonObject rejectOrder(ClientHandler c, JsonObject d) {
        long orderId = Json.reqLong(d, "id");
        String reason = Json.req(d, "reason", "Lý do từ chối");
        if (reason.length() > 200) reason = reason.substring(0, 200);
        ownOrder(c.userId, orderId);
        if (Db.update("UPDATE food_orders SET status='CANCELLED', cancel_reason=? WHERE id=? AND status='PLACED'",
                "Quán từ chối: " + reason, orderId) == 0)
            throw new AppException("Chỉ từ chối được đơn chưa nhận");
        return FoodService.afterCancel(orderId, "Quán từ chối: " + reason);
    }

    private static void ownOrder(long ownerId, long orderId) {
        if (Db.one("SELECT o.id FROM food_orders o JOIN restaurants r ON r.id = o.restaurant_id "
                + "WHERE o.id=? AND r.owner_id=?", orderId, ownerId) == null)
            throw new AppException("Không tìm thấy đơn của quán bạn");
    }

    /** Đánh giá của khách dành cho các quán của chủ quán này. */
    static JsonArray shopReviews(long ownerId) {
        return Db.query("""
                SELECT rv.id, rv.ref_id, rv.stars, rv.comment, rv.created_at, r.name AS restaurant_name,
                       u.full_name AS user_name
                FROM reviews rv JOIN restaurants r ON r.id = rv.restaurant_id JOIN users u ON u.id = rv.user_id
                WHERE rv.target='RESTAURANT' AND rv.is_hidden=0 AND r.owner_id=?
                ORDER BY rv.id DESC LIMIT 200""", ownerId);
    }

    /**
     * Báo cho chủ quán khi đơn của quán họ thay đổi. kind = NEW với đơn vừa đặt để client
     * bật thông báo nổi, UPDATE cho các lần đổi trạng thái sau đó.
     */
    static void notifyOwner(JsonObject order, String kind) {
        if (!Json.has(order, "owner_id")) return;
        JsonObject d = order.deepCopy();
        d.addProperty("kind", kind);
        Sessions.push(order.get("owner_id").getAsLong(), "MERCHANT_ORDER", d);
    }

    // ---------- Phía Admin ----------

    static JsonArray list(JsonObject d) {
        String status = Json.str(d, "status", "");
        return status.isBlank() || status.equals("ALL")
                ? Db.query(LIST + " ORDER BY FIELD(a.status,'PENDING','APPROVED','REJECTED'), a.id DESC LIMIT 300")
                : Db.query(LIST + " WHERE a.status=? ORDER BY a.id DESC LIMIT 300", status);
    }

    static JsonObject detail(long id) {
        JsonObject o = Db.one("SELECT * FROM restaurant_applications WHERE id=?", id);
        if (o == null) throw new AppException("Không tìm thấy hồ sơ #" + id);
        o.add("items", Db.query("SELECT id, name, description, price, image_id FROM restaurant_application_items "
                + "WHERE application_id=? ORDER BY id", id));
        return o;
    }

    /** Admin duyệt (tạo quán thật) hoặc từ chối kèm lý do. */
    static JsonObject review(JsonObject d) {
        long id = Json.reqLong(d, "id");
        String status = Json.req(d, "status", "Kết quả duyệt");
        if (!status.equals("APPROVED") && !status.equals("REJECTED"))
            throw new AppException("Kết quả duyệt không hợp lệ");
        String note = Json.str(d, "admin_note", "").trim();
        if (status.equals("REJECTED") && note.isEmpty()) throw new AppException("Hãy nhập lý do từ chối để chủ quán biết");
        if (note.length() > 255) note = note.substring(0, 255);

        JsonObject app = detail(id);
        if (!"PENDING".equals(Json.str(app, "status"))) throw new AppException("Hồ sơ này đã được xử lý rồi");

        String finalNote = note;
        if (status.equals("APPROVED")) {
            Db.tx(conn -> {
                long rid = Db.insert(conn, """
                        INSERT INTO restaurants(owner_id, name, category, address, lat, lng, phone, description, image_id, is_open)
                        VALUES (?,?,?,?,?,?,?,?,?,1)""",
                        app.get("user_id").getAsLong(), Json.str(app, "name"), Json.str(app, "category"),
                        Json.str(app, "address"), app.get("lat").getAsDouble(), app.get("lng").getAsDouble(),
                        Json.str(app, "phone"), Json.str(app, "description"),
                        Json.has(app, "image_id") ? app.get("image_id").getAsLong() : null);
                for (JsonElement e : app.getAsJsonArray("items")) {
                    JsonObject it = e.getAsJsonObject();
                    Db.insert(conn, "INSERT INTO menu_items(restaurant_id, name, description, price, image_id, is_available) VALUES (?,?,?,?,?,1)",
                            rid, Json.str(it, "name"), Json.str(it, "description"), it.get("price").getAsInt(),
                            Json.has(it, "image_id") ? it.get("image_id").getAsLong() : null);
                }
                Db.update(conn, "UPDATE restaurant_applications SET status='APPROVED', admin_note=?, restaurant_id=?, "
                        + "reviewed_at=NOW() WHERE id=?", blankToNull(finalNote), rid, id);
                return rid;
            });
        } else {
            Db.update("UPDATE restaurant_applications SET status='REJECTED', admin_note=?, reviewed_at=NOW() WHERE id=?", note, id);
        }
        JsonObject out = detail(id);
        Sessions.push(app.get("user_id").getAsLong(), "MERCHANT_UPDATE", out);
        Log.info("Admin " + (status.equals("APPROVED") ? "duyệt" : "từ chối") + " hồ sơ mở quán #" + id);
        return out;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
