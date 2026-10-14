package com.goride.server;

import com.goride.common.Json;
import com.google.gson.JsonObject;

import java.util.Base64;
import java.util.Set;

/** Ảnh quán / ảnh món: client gửi lên base64, server lưu vào bảng images và trả về id. */
public final class Images {
    /** Client đã thu nhỏ ảnh trước khi gửi; mốc này chỉ để chặn gói quá lớn. */
    public static final int MAX_BYTES = 800 * 1024;
    private static final Set<String> MIMES = Set.of("image/jpeg", "image/png");

    private Images() {}

    static JsonObject upload(JsonObject d) {
        String mime = Json.str(d, "mime", "image/jpeg");
        if (!MIMES.contains(mime)) throw new AppException("Chỉ hỗ trợ ảnh JPG hoặc PNG");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(Json.req(d, "data", "Dữ liệu ảnh"));
        } catch (IllegalArgumentException e) {
            throw new AppException("Dữ liệu ảnh không hợp lệ");
        }
        if (bytes.length == 0) throw new AppException("Ảnh rỗng");
        if (bytes.length > MAX_BYTES) throw new AppException("Ảnh quá lớn (tối đa " + MAX_BYTES / 1024 + " KB)");
        long id = Db.insert("INSERT INTO images(mime, bytes) VALUES (?,?)", mime, bytes);
        return Json.obj("id", id);
    }

    static JsonObject get(long id) {
        JsonObject row = Db.one("SELECT id, mime FROM images WHERE id=?", id);
        if (row == null) throw new AppException("Không tìm thấy ảnh #" + id);
        byte[] bytes = Db.scalarBytes("SELECT bytes FROM images WHERE id=?", id);
        return Json.obj("id", id, "mime", Json.str(row, "mime"),
                "data", Base64.getEncoder().encodeToString(bytes));
    }

    /** Kiểm tra id ảnh do client gửi lên; trả về null nếu không có ảnh. */
    static Long optionalId(JsonObject d, String key) {
        long id = Json.lng(d, key, 0);
        if (id <= 0) return null;
        if (Db.one("SELECT id FROM images WHERE id=?", id) == null) throw new AppException("Ảnh không tồn tại");
        return id;
    }
}
