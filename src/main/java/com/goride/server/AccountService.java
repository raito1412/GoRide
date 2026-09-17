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

    static JsonObject review(ClientHandler c, JsonObject d) {
        String refType = Json.req(d, "ref_type", "Loại");
        long refId = Json.reqLong(d, "ref_id");
        int stars = Json.integer(d, "stars", 0);
        if (stars < 1 || stars > 5) throw new AppException("Số sao từ 1 đến 5");
        JsonObject ref = switch (refType) {
            case "RIDE" -> Db.one("SELECT user_id, driver_id FROM rides WHERE id=? AND status='COMPLETED'", refId);
            case "FOOD" -> Db.one("SELECT user_id, driver_id FROM food_orders WHERE id=? AND status='DELIVERED'", refId);
            default -> throw new AppException("Loại đánh giá không hợp lệ");
        };
        if (ref == null || ref.get("user_id").getAsLong() != c.userId || !Json.has(ref, "driver_id"))
            throw new AppException("Chỉ đánh giá được chuyến/đơn đã hoàn thành của bạn");
        if (Db.one("SELECT id FROM reviews WHERE ref_type=? AND ref_id=?", refType, refId) != null)
            throw new AppException("Bạn đã đánh giá rồi");
        long driverId = ref.get("driver_id").getAsLong();
        String comment = Json.str(d, "comment", "").trim();
        Db.insert("INSERT INTO reviews(user_id, driver_id, ref_type, ref_id, stars, comment) VALUES (?,?,?,?,?,?)",
                c.userId, driverId, refType, refId, stars, comment.isEmpty() ? null : comment);
        recalcRating(driverId);
        return Json.obj("message", "Cảm ơn bạn đã đánh giá");
    }

    static void recalcRating(long driverId) {
        Db.update("""
                UPDATE drivers SET
                  rating_avg   = (SELECT COALESCE(AVG(stars), 0) FROM reviews WHERE driver_id=? AND is_hidden=0),
                  rating_count = (SELECT COUNT(*) FROM reviews WHERE driver_id=? AND is_hidden=0)
                WHERE user_id=?""", driverId, driverId, driverId);
    }

    static JsonObject earnings(long driverId) {
        double share = Config.getDouble("DRIVER_SHARE", 0.8);
        JsonObject r = Db.one("""
                SELECT COUNT(*) AS n, COALESCE(SUM(price), 0) AS gross,
                       COALESCE(SUM(CASE WHEN DATE(updated_at) = CURDATE() THEN price END), 0) AS today
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
                WHERE rv.driver_id=? AND rv.is_hidden=0 ORDER BY rv.id DESC""", driverId));
        return o;
    }
}
