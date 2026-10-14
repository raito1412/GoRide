package com.goride.server;

import com.goride.common.Config;
import com.goride.common.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Bảng đối soát thu nhập theo tháng cho tài xế và chủ quán.
 *
 * Mô hình tiền (áp dụng cho cả hai phía):
 *  - Mọi khoản khách trả đều đi qua nền tảng; thẻ giảm giá là khuyến mãi của nền tảng nên nền tảng chịu,
 *    tài xế và quán vẫn được tính trên giá gốc.
 *  - Nền tảng giữ chiết khấu: tài xế 20% (DRIVER_SHARE = 0.8), quán 25% tiền món (RESTAURANT_SHARE = 0.75).
 *  - Thuế khoán của hộ/cá nhân kinh doanh hợp tác với nền tảng (Thông tư 40/2021/TT-BTC): nền tảng khấu trừ
 *    và nộp thay trên phần doanh thu được hưởng. Mặc định GTGT 3% + TNCN 1,5% cho cả vận tải và dịch vụ
 *    ăn uống; miễn khi doanh thu được hưởng lũy kế trong năm chưa vượt ngưỡng (mặc định 200 triệu theo
 *    Luật thuế GTGT 2024). Các con số đều đọc từ .env để đối chiếu và chỉnh theo quy định hiện hành.
 *  - Tài xế nhận tiền mặt của khách thì coi như đã cầm hộ nền tảng, số đó được trừ vào kỳ chi trả;
 *    kết quả âm nghĩa là tài xế phải nộp lại cho nền tảng.
 *  - Kỳ đối soát theo tháng dương lịch, chi trả vào ngày PAYOUT_DAY (mặc định 10) của tháng kế tiếp.
 */
public final class StatementService {
    private StatementService() {}

    static double driverShare()     { return Config.getDouble("DRIVER_SHARE", 0.8); }
    static double restaurantShare() { return Config.getDouble("RESTAURANT_SHARE", 0.75); }
    private static double vatRate() { return Config.getDouble("TAX_VAT_RATE", 0.03); }
    private static double pitRate() { return Config.getDouble("TAX_PIT_RATE", 0.015); }
    private static long threshold() { return (long) Config.getDouble("TAX_THRESHOLD_YEAR", 200_000_000); }
    private static int payoutDay()  { return Math.max(1, Math.min(28, Config.getInt("PAYOUT_DAY", 10))); }

    // ---------- Tài xế ----------

    static JsonObject driver(long driverId, JsonObject d) {
        YearMonth ym = month(d);
        String[] p = period(ym);
        JsonArray lines = new JsonArray();
        // chuyến xe: giá gốc = giá khách trả + phần thẻ giảm giá nền tảng chịu
        for (JsonElement e : Db.query("""
                SELECT id, updated_at AS done_at, price + discount AS gross, price AS paid, payment_method
                FROM rides WHERE driver_id=? AND status='COMPLETED' AND updated_at >= ? AND updated_at < ?
                ORDER BY updated_at""", driverId, p[0], p[1])) {
            JsonObject r = e.getAsJsonObject();
            lines.add(line("Chuyến #" + Json.lng(r, "id", 0), r, "CASH".equals(Json.str(r, "payment_method"))
                    ? Json.integer(r, "paid", 0) : 0));
        }
        // đơn đồ ăn: tài xế được tính trên phí giao; nếu khách trả tiền mặt thì tài xế cầm cả tổng đơn
        for (JsonElement e : Db.query("""
                SELECT id, updated_at AS done_at, delivery_fee AS gross, total AS paid, payment_method
                FROM food_orders WHERE driver_id=? AND status='DELIVERED' AND updated_at >= ? AND updated_at < ?
                ORDER BY updated_at""", driverId, p[0], p[1])) {
            JsonObject r = e.getAsJsonObject();
            lines.add(line("Giao đơn #" + Json.lng(r, "id", 0), r, "CASH".equals(Json.str(r, "payment_method"))
                    ? Json.integer(r, "paid", 0) : 0));
        }
        long ytdGross = driverGrossYtd(driverId, ym);
        JsonObject who = Db.one("SELECT full_name, phone FROM users WHERE id=?", driverId);
        return build("DRIVER", ym, who, lines, 1 - driverShare(), ytdGross, true);
    }

    private static long driverGrossYtd(long driverId, YearMonth ym) {
        String from = ym.getYear() + "-01-01", to = period(ym)[1];
        JsonObject r = Db.one("""
                SELECT (SELECT COALESCE(SUM(price + discount), 0) FROM rides
                          WHERE driver_id=? AND status='COMPLETED' AND updated_at >= ? AND updated_at < ?)
                     + (SELECT COALESCE(SUM(delivery_fee), 0) FROM food_orders
                          WHERE driver_id=? AND status='DELIVERED' AND updated_at >= ? AND updated_at < ?) AS v""",
                driverId, from, to, driverId, from, to);
        return Json.lng(r, "v", 0);
    }

    // ---------- Chủ quán ----------

    static JsonObject merchant(long ownerId, JsonObject d) {
        YearMonth ym = month(d);
        String[] p = period(ym);
        JsonArray lines = new JsonArray();
        for (JsonElement e : Db.query("""
                SELECT o.id, o.updated_at AS done_at, o.items_total AS gross, o.payment_method, r.name AS shop
                FROM food_orders o JOIN restaurants r ON r.id = o.restaurant_id
                WHERE r.owner_id=? AND o.status='DELIVERED' AND o.updated_at >= ? AND o.updated_at < ?
                ORDER BY o.updated_at""", ownerId, p[0], p[1])) {
            JsonObject r = e.getAsJsonObject();
            // quán không cầm tiền của khách (tài xế giao và thu), nên không có khoản tiền mặt cần trừ
            lines.add(line("Đơn #" + Json.lng(r, "id", 0) + " · " + Json.str(r, "shop", ""), r, 0));
        }
        JsonObject ytd = Db.one("""
                SELECT COALESCE(SUM(o.items_total), 0) AS v FROM food_orders o JOIN restaurants r ON r.id=o.restaurant_id
                WHERE r.owner_id=? AND o.status='DELIVERED' AND o.updated_at >= ? AND o.updated_at < ?""",
                ownerId, ym.getYear() + "-01-01", p[1]);
        JsonObject who = Db.one("SELECT full_name, phone FROM users WHERE id=?", ownerId);
        who.add("shops", Db.query("SELECT name, address FROM restaurants WHERE owner_id=? ORDER BY id", ownerId));
        return build("MERCHANT", ym, who, lines, 1 - restaurantShare(), Json.lng(ytd, "v", 0), false);
    }

    // ---------- Tính chung ----------

    private static JsonObject line(String title, JsonObject r, int cashHeld) {
        return Json.obj("title", title, "done_at", Json.str(r, "done_at", ""),
                "gross", Json.integer(r, "gross", 0), "payment_method", Json.str(r, "payment_method", ""),
                "cash_held", cashHeld);
    }

    private static JsonObject build(String kind, YearMonth ym, JsonObject who, JsonArray lines,
                                    double rawRate, long ytdGross, boolean hasCash) {
        // 1 - 0.8 = 0.19999999999999996 -> làm tròn để tính tiền và hiển thị đúng 20%
        double commissionRate = Math.round(rawRate * 10000) / 10000.0;
        long gross = 0, cash = 0, commission = 0;
        for (JsonElement e : lines) {
            JsonObject l = e.getAsJsonObject();
            long g = Json.lng(l, "gross", 0);
            long c = Json.lng(l, "cash_held", 0);
            long fee = Math.round(g * commissionRate);
            l.addProperty("commission", fee);
            l.addProperty("earned", g - fee);
            gross += g;
            cash += c;
            // cộng từng dòng thay vì làm tròn trên tổng: hoá đơn luôn cộng khớp dù tỷ lệ là số lẻ
            commission += fee;
        }
        long earned = gross - commission;

        // ngưỡng miễn thuế so trên doanh thu ĐƯỢC HƯỞNG lũy kế từ đầu năm, cùng gốc với cách tính thuế
        long ytdEarned = Math.round(ytdGross * (1 - commissionRate));
        boolean taxable = ytdEarned > threshold();
        long vat = taxable ? Math.round(earned * vatRate()) : 0;
        long pit = taxable ? Math.round(earned * pitRate()) : 0;
        long payout = earned - vat - pit - cash;

        LocalDate payDate = ym.plusMonths(1).atDay(payoutDay());
        LocalDate today = LocalDate.now();
        String status;
        if (!ym.isBefore(YearMonth.from(today))) status = "Kỳ đang diễn ra — số liệu tạm tính, chốt sổ ngày "
                + ym.plusMonths(1).atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        else if (today.isBefore(payDate)) status = "Đã chốt sổ — còn " + ChronoUnit.DAYS.between(today, payDate) + " ngày tới hạn chi trả";
        else status = "Đã tới hạn chi trả";

        return Json.obj(
                "kind", kind, "month", ym.toString(), "period_from", ym.atDay(1).toString(),
                "period_to", ym.atEndOfMonth().toString(), "issued_at", today.toString(),
                "full_name", Json.str(who, "full_name", ""), "phone", Json.str(who, "phone", ""),
                "shops", who.has("shops") ? who.get("shops") : new JsonArray(),
                "lines", lines, "count", lines.size(),
                "gross", gross, "commission_rate", commissionRate, "commission", commission, "earned", earned,
                "ytd_earned", ytdEarned, "tax_threshold", threshold(), "taxable", taxable,
                "vat_rate", vatRate(), "vat", vat, "pit_rate", pitRate(), "pit", pit, "tax", vat + pit,
                "has_cash", hasCash, "cash_held", cash, "payout", payout,
                "payout_date", payDate.toString(), "payout_day", payoutDay(), "status", status);
    }

    private static YearMonth month(JsonObject d) {
        String m = Json.str(d, "month", "");
        if (m.isBlank()) return YearMonth.now();
        try { return YearMonth.parse(m.trim()); }
        catch (DateTimeParseException e) { throw new AppException("Tháng không hợp lệ, dạng đúng là YYYY-MM"); }
    }

    /** [ngày đầu tháng, ngày đầu tháng sau) — dùng so sánh nửa mở để không sót giây cuối tháng. */
    private static String[] period(YearMonth ym) {
        return new String[]{ym.atDay(1).toString(), ym.plusMonths(1).atDay(1).toString()};
    }

    // ---------- Phí nền tảng cho dashboard Admin ----------

    /** Tổng chiết khấu nền tảng thu được (chưa trừ khuyến mãi — khuyến mãi là chi phí riêng). */
    static long platformCommission() {
        JsonObject r = Db.one("""
                SELECT (SELECT COALESCE(SUM(price + discount), 0) FROM rides WHERE status='COMPLETED') AS ride_gross,
                       (SELECT COALESCE(SUM(delivery_fee), 0) FROM food_orders WHERE status='DELIVERED') AS fee_gross,
                       (SELECT COALESCE(SUM(items_total), 0) FROM food_orders WHERE status='DELIVERED') AS item_gross""");
        double driverCut = 1 - driverShare(), shopCut = 1 - restaurantShare();
        return Math.round((Json.dbl(r, "ride_gross", 0) + Json.dbl(r, "fee_gross", 0)) * driverCut
                + Json.dbl(r, "item_gross", 0) * shopCut);
    }
}
