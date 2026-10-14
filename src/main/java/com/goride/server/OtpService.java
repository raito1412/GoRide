package com.goride.server;

import com.goride.common.Json;
import com.goride.common.Log;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Mã OTP 6 số gửi qua email khi đăng ký tài khoản.
 * Hồ sơ đăng ký được giữ tạm trong bảng email_otps (mật khẩu đã băm sẵn);
 * chỉ khi nhập đúng mã còn hạn thì tài khoản mới thực sự được tạo.
 */
public final class OtpService {
    static final int TTL_MINUTES = 5;
    static final int MAX_ATTEMPTS = 5;
    static final int RESEND_COOLDOWN_S = 60;
    /** Gửi lại do bấm nút thì chờ lâu hơn; điền lại biểu mẫu thì chỉ chặn spam trong chốc lát. */
    private static final int REGISTER_COOLDOWN_S = 20;
    private static final String PURPOSE = "REGISTER";
    private static final SecureRandom RNG = new SecureRandom();

    private OtpService() {}

    /** Tạo mã mới cho hồ sơ đăng ký và gửi email. Trả về thông tin cho màn hình nhập mã. */
    static JsonObject start(String email, JsonObject payload, String displayName) {
        return start(email, payload, displayName, REGISTER_COOLDOWN_S);
    }

    private static JsonObject start(String email, JsonObject payload, String displayName, int cooldown) {
        // hồ sơ dang dở đã hết hạn thì xoá hẳn, không giữ lại thông tin của lần đăng ký chưa xong
        Db.update("DELETE FROM email_otps WHERE expires_at < NOW()");
        checkCooldown(email, cooldown);
        String code = newCode();
        Db.update("""
                INSERT INTO email_otps(email, purpose, code_hash, payload, attempts, expires_at, last_sent_at)
                VALUES (?,?,?,?,0, NOW() + INTERVAL ? MINUTE, NOW())
                ON DUPLICATE KEY UPDATE code_hash=VALUES(code_hash), payload=VALUES(payload), attempts=0,
                                        expires_at=VALUES(expires_at), last_sent_at=NOW()""",
                email, PURPOSE, Passwords.hash(code), payload.toString(), TTL_MINUTES);
        deliver(email, displayName, code);
        return info(email, "Mã xác thực gồm 6 số đã được gửi tới " + email
                + ". Mã có hiệu lực trong " + TTL_MINUTES + " phút.");
    }

    /** Gửi lại mã mới cho hồ sơ đang chờ. */
    static JsonObject resend(String email) {
        JsonObject row = row(email);
        if (row == null) throw new AppException("Không có yêu cầu đăng ký nào đang chờ xác thực cho email này");
        JsonObject payload = JsonParser.parseString(Json.str(row, "payload")).getAsJsonObject();
        return start(email, payload, Json.str(payload, "full_name", ""), RESEND_COOLDOWN_S);
    }

    /**
     * Kiểm tra mã. Đúng thì xóa bản ghi và trả về hồ sơ đăng ký đã lưu.
     * Sai / hết hạn / quá số lần thử đều ném AppException với thông báo rõ ràng.
     */
    static JsonObject consume(String email, String code) {
        JsonObject row = row(email);
        if (row == null) throw new AppException("Không có yêu cầu đăng ký nào đang chờ xác thực cho email này");
        long id = row.get("id").getAsLong();
        if (Json.integer(row, "expired", 1) == 1) {
            Db.update("DELETE FROM email_otps WHERE id=?", id);
            throw new AppException("Mã đã hết hạn (" + TTL_MINUTES + " phút). Bấm \"Gửi lại mã\" để nhận mã mới.");
        }
        int attempts = Json.integer(row, "attempts", 0);
        if (attempts >= MAX_ATTEMPTS) {
            Db.update("DELETE FROM email_otps WHERE id=?", id);
            throw new AppException("Bạn đã nhập sai quá " + MAX_ATTEMPTS + " lần. Hãy đăng ký lại từ đầu.");
        }
        if (!Passwords.verify(code.trim(), Json.str(row, "code_hash"))) {
            Db.update("UPDATE email_otps SET attempts = attempts + 1 WHERE id=?", id);
            int left = MAX_ATTEMPTS - attempts - 1;
            throw new AppException(left > 0
                    ? "Mã xác thực không đúng. Bạn còn " + left + " lần thử."
                    : "Mã xác thực không đúng. Hãy đăng ký lại từ đầu.");
        }
        Db.update("DELETE FROM email_otps WHERE id=?", id);
        return JsonParser.parseString(Json.str(row, "payload")).getAsJsonObject();
    }

    /** Thông tin trả cho client: còn bao nhiêu giây thì hết hạn / được gửi lại. */
    static JsonObject info(String email, String message) {
        JsonObject o = Json.obj("otp_required", true, "email", email, "message", message,
                "expires_in_s", TTL_MINUTES * 60, "resend_after_s", RESEND_COOLDOWN_S);
        if (!Mailer.configured())
            o.addProperty("dev_hint", "Server chưa cấu hình GMAIL_USER/GMAIL_APP_PASSWORD — xem mã trong cửa sổ log của server.");
        return o;
    }

    // ---------- Nội bộ ----------

    private static JsonObject row(String email) {
        return Db.one("""
                SELECT id, code_hash, payload, attempts, expires_at < NOW() AS expired
                FROM email_otps WHERE email=? AND purpose=?""", email, PURPOSE);
    }

    private static void checkCooldown(String email, int cooldown) {
        JsonObject r = Db.one("SELECT TIMESTAMPDIFF(SECOND, NOW(), last_sent_at + INTERVAL ? SECOND) AS wait "
                + "FROM email_otps WHERE email=? AND purpose=?", cooldown, email, PURPOSE);
        int wait = r == null ? 0 : Json.integer(r, "wait", 0);
        if (wait > 0) throw new AppException("Mã vừa được gửi tới " + email + " cách đây ít giây — hãy kiểm tra hộp thư "
                + "(kể cả mục Spam). Tài khoản CHƯA được tạo. Đợi " + wait + " giây nữa nếu muốn gửi mã mới.");
    }

    private static String newCode() {
        return String.format("%06d", RNG.nextInt(1_000_000));
    }

    private static void deliver(String email, String displayName, String code) {
        if (!Mailer.configured()) {
            Log.info("[OTP] Chưa cấu hình Gmail — mã cho " + email + " là: " + code
                    + " (hết hạn sau " + TTL_MINUTES + " phút)");
            return;
        }
        try {
            Mailer.sendHtml(email, "Mã xác thực GoRide: " + code, html(displayName, code));
        } catch (IOException e) {
            Log.error("Không gửi được email OTP tới " + email, e);
            Db.update("DELETE FROM email_otps WHERE email=? AND purpose=?", email, PURPOSE);
            throw new AppException("Không gửi được email tới " + email
                    + ". Kiểm tra lại địa chỉ email hoặc cấu hình Gmail của server.");
        }
    }

    private static String html(String name, String code) {
        String hello = name == null || name.isBlank() ? "Xin chào," : "Xin chào " + esc(name) + ",";
        return """
                <div style="font-family:Segoe UI,Roboto,Arial,sans-serif;background:#F7F6FC;padding:28px">
                  <div style="max-width:460px;margin:auto;background:#fff;border-radius:18px;padding:28px 32px">
                    <div style="font-size:24px;font-weight:700;color:#5E66C4">GoRide</div>
                    <p style="color:#35344D">%s</p>
                    <p style="color:#35344D">Mã xác thực để hoàn tất đăng ký tài khoản GoRide của bạn là:</p>
                    <div style="font-size:34px;font-weight:700;letter-spacing:10px;color:#35344D;
                                background:#EFEDF8;border-radius:14px;padding:16px;text-align:center">%s</div>
                    <p style="color:#8B8AA6;font-size:13px">Mã có hiệu lực trong %d phút và chỉ dùng được một lần.
                       Nếu bạn không đăng ký GoRide, hãy bỏ qua email này.</p>
                  </div>
                </div>""".formatted(hello, code, TTL_MINUTES);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
