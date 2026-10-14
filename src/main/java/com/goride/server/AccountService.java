package com.goride.server;

import com.goride.common.Config;
import com.goride.common.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Thanh toán, đánh giá, thu nhập tài xế. */
public final class AccountService {
    private AccountService() {}

    static JsonArray payments(long userId) {
        return Db.query("SELECT id, ref_type, ref_id, amount, method, status, created_at FROM payments "
                + "WHERE user_id=? ORDER BY id DESC", userId);
    }

    /** target = DRIVER (mặc định) đánh giá tài xế, RESTAURANT đánh giá quán của đơn đồ ăn. */
    static JsonObject review(ClientHandler c, JsonObject d) {
        String refType = Json.req(d, "ref_type", "Loại");
        long refId = Json.reqLong(d, "ref_id");
        int stars = Json.integer(d, "stars", 0);
        if (stars < 1 || stars > 5) throw new AppException("Số sao từ 1 đến 5");
        String comment = Json.str(d, "comment", "").trim();
        if (comment.length() > 500) throw new AppException("Nhận xét tối đa 500 ký tự");
        if ("RESTAURANT".equals(Json.str(d, "target", "DRIVER"))) return reviewRestaurant(c, refType, refId, stars, comment);
        JsonObject ref = switch (refType) {
            case "RIDE" -> Db.one("SELECT user_id, driver_id FROM rides WHERE id=? AND status='COMPLETED'", refId);
            case "FOOD" -> Db.one("SELECT user_id, driver_id FROM food_orders WHERE id=? AND status='DELIVERED'", refId);
            default -> throw new AppException("Loại đánh giá không hợp lệ");
        };
        if (ref == null || ref.get("user_id").getAsLong() != c.userId || !Json.has(ref, "driver_id"))
            throw new AppException("Chỉ đánh giá được chuyến/đơn đã hoàn thành của bạn");
        if (Db.one("SELECT id FROM reviews WHERE ref_type=? AND ref_id=? AND target='DRIVER'", refType, refId) != null)
            throw new AppException("Bạn đã đánh giá tài xế của " + (refType.equals("RIDE") ? "chuyến" : "đơn") + " này rồi");
        long driverId = ref.get("driver_id").getAsLong();
        Db.insert("INSERT INTO reviews(user_id, target, driver_id, ref_type, ref_id, stars, comment) VALUES (?, 'DRIVER', ?,?,?,?,?)",
                c.userId, driverId, refType, refId, stars, comment.isEmpty() ? null : comment);
        recalcRating(driverId);
        return Json.obj("message", "Cảm ơn bạn đã đánh giá tài xế");
    }

    private static JsonObject reviewRestaurant(ClientHandler c, String refType, long refId, int stars, String comment) {
        if (!"FOOD".equals(refType)) throw new AppException("Chỉ đánh giá được quán của đơn đồ ăn");
        JsonObject o = Db.one("SELECT user_id, restaurant_id FROM food_orders WHERE id=? AND status='DELIVERED'", refId);
        if (o == null || o.get("user_id").getAsLong() != c.userId)
            throw new AppException("Chỉ đánh giá được quán của đơn đã giao xong của bạn");
        if (Db.one("SELECT id FROM reviews WHERE ref_type='FOOD' AND ref_id=? AND target='RESTAURANT'", refId) != null)
            throw new AppException("Bạn đã đánh giá quán của đơn này rồi");
        long restaurantId = o.get("restaurant_id").getAsLong();
        Db.insert("INSERT INTO reviews(user_id, target, restaurant_id, ref_type, ref_id, stars, comment) "
                + "VALUES (?, 'RESTAURANT', ?, 'FOOD', ?, ?, ?)", c.userId, restaurantId, refId, stars,
                comment.isEmpty() ? null : comment);
        recalcRestaurantRating(restaurantId);
        JsonObject shop = Db.one("SELECT owner_id, name FROM restaurants WHERE id=?", restaurantId);
        if (shop != null && Json.has(shop, "owner_id"))
            Sessions.push(shop.get("owner_id").getAsLong(), "MERCHANT_REVIEW",
                    Json.obj("restaurant_name", Json.str(shop, "name"), "stars", stars, "comment", comment));
        return Json.obj("message", "Cảm ơn bạn đã đánh giá quán");
    }

    static void recalcRestaurantRating(long restaurantId) {
        Db.update("""
                UPDATE restaurants SET
                  rating_avg   = (SELECT COALESCE(AVG(stars), 0) FROM reviews
                                  WHERE restaurant_id=? AND target='RESTAURANT' AND is_hidden=0),
                  rating_count = (SELECT COUNT(*) FROM reviews
                                  WHERE restaurant_id=? AND target='RESTAURANT' AND is_hidden=0)
                WHERE id=?""", restaurantId, restaurantId, restaurantId);
    }

    static void recalcRating(long driverId) {
        Db.update("""
                UPDATE drivers SET
                  rating_avg   = (SELECT COALESCE(AVG(stars), 0) FROM reviews
                                  WHERE driver_id=? AND target='DRIVER' AND is_hidden=0),
                  rating_count = (SELECT COUNT(*) FROM reviews
                                  WHERE driver_id=? AND target='DRIVER' AND is_hidden=0)
                WHERE user_id=?""", driverId, driverId, driverId);
    }

    static JsonObject earnings(long driverId) {
        double share = Config.getDouble("DRIVER_SHARE", 0.8);
        JsonObject r = Db.one("""
                SELECT COUNT(*) AS n, COALESCE(SUM(price + discount), 0) AS gross,
                       COALESCE(SUM(CASE WHEN DATE(updated_at) = CURDATE() THEN price + discount END), 0) AS today
                FROM rides WHERE driver_id=? AND status='COMPLETED'""", driverId);
        JsonObject f = Db.one("""
                SELECT COUNT(*) AS n, COALESCE(SUM(delivery_fee), 0) AS gross,
                       COALESCE(SUM(CASE WHEN DATE(updated_at) = CURDATE() THEN delivery_fee END), 0) AS today
                FROM food_orders WHERE driver_id=? AND status='DELIVERED'""", driverId);
        double rideIncome = r.get("gross").getAsDouble() * share;
        double foodIncome = f.get("gross").getAsDouble() * share;
        double today = (r.get("today").getAsDouble() + f.get("today").getAsDouble()) * share;
        return Json.obj("share", share,
                "ride_count", r.get("n").getAsInt(), "ride_income", Math.round(rideIncome),
                "food_count", f.get("n").getAsInt(), "food_income", Math.round(foodIncome),
                "today_income", Math.round(today), "total_income", Math.round(rideIncome + foodIncome));
    }

    static JsonObject driverReviews(long driverId) {
        JsonObject o = Db.one("SELECT rating_avg, rating_count FROM drivers WHERE user_id=?", driverId);
        o.add("reviews", Db.query("""
                SELECT rv.ref_type, rv.ref_id, rv.stars, rv.comment, rv.created_at, u.full_name AS user_name
                FROM reviews rv JOIN users u ON u.id = rv.user_id
                WHERE rv.driver_id=? AND rv.target='DRIVER' AND rv.is_hidden=0 ORDER BY rv.id DESC""", driverId));
        return o;
    }
}
