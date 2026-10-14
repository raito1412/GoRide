package com.goride.server;

import com.goride.common.Config;
import com.goride.common.Json;
import com.goride.common.Log;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

public final class AuthService {
    private static final String PROFILE_SQL = """
            SELECT u.id, u.role, u.full_name, u.phone, u.email, u.address, u.status, u.created_at,
                   d.vehicle_type, d.plate_number, d.vehicle_brand, d.vehicle_color, d.license_number,
                   d.approval, d.is_online, d.last_lat, d.last_lng, d.rating_avg, d.rating_count
            FROM users u LEFT JOIN drivers d ON d.user_id = u.id
            WHERE u.id = ?""";

    private AuthService() {}

    /**
     * Bước 1: kiểm tra hồ sơ đăng ký rồi gửi mã OTP tới email. Tài khoản CHƯA được tạo ở bước này —
     * hồ sơ (mật khẩu đã băm) nằm tạm trong bảng email_otps cho tới khi nhập đúng mã.
     */
    static JsonObject register(JsonObject d) {
        JsonObject form = validateRegistration(d);
        String email = Json.str(form, "email");
        return OtpService.start(email, form, Json.str(form, "full_name"));
    }

    /** Bước 2: nhập mã OTP -> tạo tài khoản thật. */
    static JsonObject registerVerify(JsonObject d) {
        String email = email(Json.req(d, "email", "Email"));
        String code = Json.req(d, "code", "Mã xác thực");
        if (!code.trim().matches("\\d{6}")) throw new AppException("Mã xác thực gồm 6 chữ số");
        JsonObject form = OtpService.consume(email, code);

        // kiểm tra lại lần cuối: trong 5 phút chờ có thể đã có người khác dùng số/biển số này
        String phone = Json.str(form, "phone");
        boolean driver = "DRIVER".equals(Json.str(form, "role"));
        if (Db.one("SELECT id FROM users WHERE phone=?", phone) != null)
            throw new AppException("Số điện thoại đã được đăng ký");
        if (driver && Db.one("SELECT user_id FROM drivers WHERE plate_number=?", Json.str(form, "plate_number")) != null)
            throw new AppException("Biển số xe đã được đăng ký");

        long id = Db.tx(c -> {
            long uid = Db.insert(c, "INSERT INTO users(role, full_name, phone, email, password_hash) VALUES (?,?,?,?,?)",
                    Json.str(form, "role"), Json.str(form, "full_name"), phone, email, Json.str(form, "password_hash"));
            if (driver) {
                Db.update(c, "INSERT INTO drivers(user_id, vehicle_type, plate_number, vehicle_brand, vehicle_color, license_number) VALUES (?,?,?,?,?,?)",
                        uid, Json.str(form, "vehicle_type"), Json.str(form, "plate_number"),
                        Json.str(form, "vehicle_brand"), Json.str(form, "vehicle_color"), Json.str(form, "license_number"));
            }
            return uid;
        });
        Log.info("Đăng ký mới #" + id + " (" + Json.str(form, "role") + ", email đã xác thực: " + email + ")");
        return Json.obj("id", id, "message", driver
                ? "Xác thực email thành công! Tài khoản tài xế cần Admin duyệt trước khi nhận chuyến."
                : "Xác thực email thành công! Bạn có thể đăng nhập ngay.");
    }

    /** Gửi lại mã OTP cho hồ sơ đang chờ. */
    static JsonObject registerResend(JsonObject d) {
        return OtpService.resend(email(Json.req(d, "email", "Email")));
    }

    /** Kiểm tra toàn bộ hồ sơ đăng ký, trả về bản đã chuẩn hóa với mật khẩu đã băm. */
    private static JsonObject validateRegistration(JsonObject d) {
        String role = Json.str(d, "role", "USER");
        if (!role.equals("USER") && !role.equals("DRIVER")) throw new AppException("Chỉ đăng ký được tài khoản Khách hàng hoặc Tài xế");
        String name = Json.req(d, "full_name", "Họ tên");
        String phone = Json.req(d, "phone", "Số điện thoại");
        String password = Json.req(d, "password", "Mật khẩu");
        String email = email(Json.req(d, "email", "Email"));
        if (!phone.matches("0\\d{9}")) throw new AppException("Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0");
        if (password.length() < 6) throw new AppException("Mật khẩu tối thiểu 6 ký tự");
        if (Db.one("SELECT id FROM users WHERE phone=?", phone) != null)
            throw new AppException("Số điện thoại này đã có tài khoản hoàn tất đăng ký. Hãy đăng nhập, hoặc dùng số khác.");
        if (Db.one("SELECT id FROM users WHERE email=?", email) != null)
            throw new AppException("Email này đã gắn với một tài khoản khác. Hãy dùng email khác.");

        JsonObject form = Json.obj("role", role, "full_name", name, "phone", phone, "email", email,
                "password_hash", Passwords.hash(password));
        if (role.equals("DRIVER")) {
            String plate = Json.req(d, "plate_number", "Biển số xe").toUpperCase();
            if (Db.one("SELECT user_id FROM drivers WHERE plate_number=?", plate) != null)
                throw new AppException("Biển số xe đã được đăng ký");
            form.addProperty("vehicle_type", vehicleType(Json.str(d, "vehicle_type", "BIKE")));
            form.addProperty("plate_number", plate);
            Json.put(form, "vehicle_brand", Json.str(d, "vehicle_brand"));
            Json.put(form, "vehicle_color", Json.str(d, "vehicle_color"));
            Json.put(form, "license_number", Json.str(d, "license_number"));
        }
        return form;
    }

    /** Email phải hợp lệ vì mã xác thực được gửi tới đó. */
    private static String email(String raw) {
        String email = raw.trim().toLowerCase();
        if (!email.matches("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")) throw new AppException("Email không hợp lệ");
        if (email.length() > 120) throw new AppException("Email quá dài");
        return email;
    }

    static JsonObject login(ClientHandler c, JsonObject d) {
        if (c.loggedIn()) throw new AppException("Kết nối này đã đăng nhập");
        String phone = Json.req(d, "phone", "Số điện thoại");
        String password = Json.req(d, "password", "Mật khẩu");
        JsonObject u = Db.one("SELECT id, role, full_name, password_hash, status FROM users WHERE phone=?", phone);
        if (u == null || !Passwords.verify(password, u.get("password_hash").getAsString()))
            throw new AppException("Sai số điện thoại hoặc mật khẩu");
        if ("LOCKED".equals(Json.str(u, "status"))) throw new AppException("Tài khoản đã bị khóa, liên hệ Admin");

        c.userId = u.get("id").getAsLong();
        c.role = u.get("role").getAsString();
        c.fullName = u.get("full_name").getAsString();
        JsonObject profile = profile(c.userId);

        if ("DRIVER".equals(c.role)) {
            c.vehicleType = Json.str(profile, "vehicle_type");
            c.approved = "APPROVED".equals(Json.str(profile, "approval"));
            c.driverOnline = false;
            c.udpToken = UUID.randomUUID().toString().replace("-", "");
            Db.update("UPDATE drivers SET is_online=0 WHERE user_id=?", c.userId);
            DriverJobs.restore(c.userId);
            profile.addProperty("udp_token", c.udpToken);
            profile.addProperty("udp_port", Config.udpPort());
        }
        Sessions.bind(c);
        Log.info("Đăng nhập: " + c.fullName + " (" + c.role + " #" + c.userId + ")");
        return profile;
    }

    static JsonElement logout(ClientHandler c) {
        c.cleanup();
        c.userId = -1;
        c.role = null;
        c.udpToken = null;
        return null;
    }

    static JsonObject profile(long userId) {
        JsonObject p = Db.one(PROFILE_SQL, userId);
        if (p == null) throw new AppException("Không tìm thấy tài khoản");
        return p;
    }

    static JsonObject updateProfile(ClientHandler c, JsonObject d) {
        String name = Json.req(d, "full_name", "Họ tên");
        String email = Json.str(d, "email", "").trim();
        String address = Json.str(d, "address", "").trim();
        Db.update("UPDATE users SET full_name=?, email=?, address=? WHERE id=?",
                name, email.isEmpty() ? null : email, address.isEmpty() ? null : address, c.userId);
        String newPassword = Json.str(d, "new_password", "");
        if (!newPassword.isBlank()) {
            if (newPassword.length() < 6) throw new AppException("Mật khẩu mới tối thiểu 6 ký tự");
            Db.update("UPDATE users SET password_hash=? WHERE id=?", Passwords.hash(newPassword), c.userId);
        }
        c.fullName = name;
        return profile(c.userId);
    }

    static JsonObject updateVehicle(ClientHandler c, JsonObject d) {
        JsonObject cur = profile(c.userId);
        String type = vehicleType(Json.req(d, "vehicle_type", "Loại xe"));
        String plate = Json.req(d, "plate_number", "Biển số").toUpperCase();
        boolean needReview = !type.equals(Json.str(cur, "vehicle_type")) || !plate.equals(Json.str(cur, "plate_number"));
        if (needReview && DriverJobs.busy(c.userId)) throw new AppException("Hoàn tất việc đang làm trước khi đổi xe");
        Db.update("UPDATE drivers SET vehicle_type=?, plate_number=?, vehicle_brand=?, vehicle_color=?, license_number=?"
                        + (needReview ? ", approval='PENDING', is_online=0" : "") + " WHERE user_id=?",
                type, plate, Json.str(d, "vehicle_brand"), Json.str(d, "vehicle_color"), Json.str(d, "license_number"), c.userId);
        c.vehicleType = type;
        if (needReview) {
            c.approved = false;
            c.driverOnline = false;
        }
        JsonObject p = profile(c.userId);
        p.addProperty("message", needReview ? "Đã lưu. Đổi loại xe/biển số cần Admin duyệt lại." : "Đã lưu thông tin xe");
        return p;
    }

    static JsonObject setOnline(ClientHandler c, JsonObject d) {
        boolean online = Json.bool(d, "online", false);
        if (online && !c.approved) throw new AppException("Tài khoản chưa được Admin duyệt");
        if (!online && DriverJobs.busy(c.userId)) throw new AppException("Bạn đang có việc chưa hoàn tất");
        if (online && Json.has(d, "lat")) {
            Sessions.LOCATIONS.put(c.userId, new double[]{Json.reqDbl(d, "lat"), Json.reqDbl(d, "lng")});
        }
        c.driverOnline = online;
        c.rejectedRides.clear();
        c.rejectedOrders.clear();
        Db.update("UPDATE drivers SET is_online=? WHERE user_id=?", online ? 1 : 0, c.userId);
        return Json.obj("online", online);
    }

    /**
     * Tài xế báo vị trí qua TCP. Trước đây vị trí chỉ tới server bằng UDP, nên khi gói UDP không
     * đến được (khác máy, firewall chặn) thì server vẫn tính bán kính theo vị trí lúc đăng nhập
     * và tài xế không bao giờ thấy yêu cầu nào.
     */
    static JsonObject setLocation(ClientHandler c, JsonObject d) {
        double lat = Json.reqDbl(d, "lat"), lng = Json.reqDbl(d, "lng");
        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new AppException("Toạ độ không hợp lệ");
        Sessions.LOCATIONS.put(c.userId, new double[]{lat, lng});
        Db.update("UPDATE drivers SET last_lat=?, last_lng=? WHERE user_id=?", lat, lng, c.userId);
        return Json.obj("lat", lat, "lng", lng);
    }

    static String vehicleType(String v) {
        if (!"BIKE".equals(v) && !"CAR".equals(v)) throw new AppException("Loại xe không hợp lệ");
        return v;
    }
}
