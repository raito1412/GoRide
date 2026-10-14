package com.goride.server;

import com.goride.common.Json;
import com.goride.common.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.security.SecureRandom;
import java.time.LocalDate;

/**
 * Thẻ giảm giá. Mỗi khách luôn có sẵn vài thẻ 10-15%; riêng các "ngày đôi" (1/1, 2/2, ... 12/12)
 * thì có thêm thẻ 20%. Thẻ được phát tự động khi khách mở ví, không cần Admin tạo tay.
 */
public final class VoucherService {
    private static final int KEEP_ACTIVE = 3;
    private static final int NORMAL_MIN_PERCENT = 10, NORMAL_MAX_PERCENT = 15;
    private static final int SPECIAL_PERCENT = 20;
    private static final int NORMAL_CAP = 30_000, SPECIAL_CAP = 60_000;
    private static final int VALID_DAYS = 7;
    private static final SecureRandom RNG = new SecureRandom();

    private VoucherService() {}

    /** Hôm nay có phải ngày đôi (1/1, 2/2, ... 12/12) không. */
    static boolean specialDay() {
        return specialDay(LocalDate.now());
    }

    /** Tách riêng theo ngày truyền vào để kiểm thử được mà không phải đổi giờ hệ thống. */
    static boolean specialDay(LocalDate d) {
        return d.getDayOfMonth() == d.getMonthValue();
    }

    /** Ví của khách: tự phát thêm thẻ nếu còn ít, rồi trả về các thẻ còn dùng được. */
    static JsonArray wallet(long userId) {
        topUp(userId);
        JsonArray list = active(userId);
        return list;
    }

    private static JsonArray active(long userId) {
        return Db.query("""
                SELECT id, code, title, percent, max_discount, min_order, scope, expires_at,
                       TIMESTAMPDIFF(HOUR, NOW(), expires_at) AS hours_left
                FROM vouchers
                WHERE user_id=? AND used_at IS NULL AND expires_at > NOW()
                ORDER BY percent DESC, expires_at""", userId);
    }

    /** Bổ sung cho đủ số thẻ thường, và thêm thẻ ngày đôi nếu hôm nay có. */
    private static void topUp(long userId) {
        JsonArray current = active(userId);
        int normal = 0;
        boolean hasSpecial = false;
        for (var e : current) {
            JsonObject v = e.getAsJsonObject();
            if (Json.integer(v, "percent", 0) >= SPECIAL_PERCENT) hasSpecial = true;
            else normal++;
        }
        for (int i = normal; i < KEEP_ACTIVE; i++) {
            int percent = NORMAL_MIN_PERCENT + RNG.nextInt(NORMAL_MAX_PERCENT - NORMAL_MIN_PERCENT + 1);
            create(userId, "Giảm " + percent + "% cho đơn tiếp theo", percent, NORMAL_CAP, "ALL", VALID_DAYS);
        }
        if (specialDay() && !hasSpecial) {
            LocalDate d = LocalDate.now();
            create(userId, "Ngày đôi " + d.getDayOfMonth() + "/" + d.getMonthValue() + " — giảm " + SPECIAL_PERCENT + "%",
                    SPECIAL_PERCENT, SPECIAL_CAP, "ALL", 1);
        }
    }

    private static void create(long userId, String title, int percent, int cap, String scope, int days) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Db.update("INSERT INTO vouchers(user_id, code, title, percent, max_discount, min_order, scope, expires_at) "
                                + "VALUES (?,?,?,?,?,0,?, DATE_ADD(CURDATE(), INTERVAL ? DAY) + INTERVAL '23:59:59' HOUR_SECOND)",
                        userId, newCode(percent), title, percent, cap, scope, days);
                return;
            } catch (RuntimeException e) {
                if (!Db.isConstraintViolation(e)) throw e; // trùng mã thì bốc mã khác
            }
        }
        Log.error("Không sinh được mã giảm giá cho user #" + userId, null);
    }

    private static String newCode(int percent) {
        StringBuilder sb = new StringBuilder("GO").append(percent);
        for (int i = 0; i < 5; i++) sb.append("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".charAt(RNG.nextInt(32)));
        return sb.toString();
    }

    // ---------- Dùng thẻ ----------

    /** Số tiền được giảm cho một hoá đơn; 0 nếu không nhập mã. Ném lỗi nếu mã sai/hết hạn/không đủ điều kiện. */
    static int discountFor(long userId, String code, String scope, int gross) {
        return amount(lookup(userId, code, scope), gross);
    }

    /** Tiền giảm của một thẻ đã kiểm tra hợp lệ. */
    static int amount(JsonObject v, int gross) {
        return v == null ? 0 : discount(v, gross);
    }

    private static int discount(JsonObject v, int amount) {
        int d = (int) Math.round(amount * Json.integer(v, "percent", 0) / 100.0);
        return Math.min(d, Json.integer(v, "max_discount", 0));
    }

    /** Kiểm tra mã và trả về thẻ; null nếu client không gửi mã. */
    static JsonObject lookup(long userId, String code, String scope) {
        if (code == null || code.isBlank()) return null;
        JsonObject v = Db.one("SELECT * FROM vouchers WHERE user_id=? AND code=?", userId, code.trim().toUpperCase());
        if (v == null) throw new AppException("Mã giảm giá không tồn tại");
        if (Json.has(v, "used_at")) throw new AppException("Mã giảm giá này đã được dùng rồi");
        if (Db.one("SELECT 1 v FROM vouchers WHERE id=? AND expires_at > NOW()", v.get("id").getAsLong()) == null)
            throw new AppException("Mã giảm giá đã hết hạn");
        String s = Json.str(v, "scope", "ALL");
        if (!s.equals("ALL") && !s.equals(scope))
            throw new AppException("Mã này chỉ dùng cho " + (s.equals("RIDE") ? "chuyến xe" : "đơn đồ ăn"));
        return v;
    }

    /** Đánh dấu đã dùng; gọi trong transaction đặt chuyến/đơn. */
    static void markUsed(java.sql.Connection conn, JsonObject voucher, String refType, long refId) {
        if (voucher == null) return;
        int n = Db.update(conn, "UPDATE vouchers SET used_at=NOW(), used_ref=? WHERE id=? AND used_at IS NULL",
                refType + "#" + refId, voucher.get("id").getAsLong());
        if (n == 0) throw new AppException("Mã giảm giá vừa được dùng ở nơi khác");
    }

    /** Chuyến/đơn bị huỷ thì trả thẻ lại cho khách (nếu thẻ còn hạn thì dùng tiếp được). */
    static void release(String refType, long refId) {
        int n = Db.update("UPDATE vouchers SET used_at=NULL, used_ref=NULL WHERE used_ref=?", refType + "#" + refId);
        if (n > 0) Log.info("Hoàn thẻ giảm giá của " + refType + " #" + refId);
    }
}
