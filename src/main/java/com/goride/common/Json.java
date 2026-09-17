package com.goride.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Tiện ích JSON dùng chung cho server và client. Lỗi dữ liệu đầu vào ném IllegalArgumentException. */
public final class Json {
    public static final Gson GSON = new Gson();

    private Json() {}

    /** Json.obj("id", 1, "name", "A") */
    public static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) put(o, (String) kv[i], kv[i + 1]);
        return o;
    }

    public static void put(JsonObject o, String k, Object v) {
        if (v == null) o.add(k, JsonNull.INSTANCE);
        else if (v instanceof JsonElement e) o.add(k, e);
        else if (v instanceof Number n) o.addProperty(k, n);
        else if (v instanceof Boolean b) o.addProperty(k, b);
        else o.addProperty(k, v.toString());
    }

    public static boolean has(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull();
    }

    public static String str(JsonObject o, String k) {
        return has(o, k) ? o.get(k).getAsString() : null;
    }

    public static String str(JsonObject o, String k, String def) {
        String v = str(o, k);
        return v == null ? def : v;
    }

    /** Chuỗi bắt buộc (đã trim). */
    public static String req(JsonObject o, String k, String label) {
        String v = str(o, k);
        if (v == null || v.isBlank()) throw new IllegalArgumentException(label + " không được để trống");
        return v.trim();
    }

    public static double dbl(JsonObject o, String k, double def) {
        if (!has(o, k)) return def;
        try { return o.get(k).getAsDouble(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Giá trị không hợp lệ: " + k); }
    }

    public static double reqDbl(JsonObject o, String k) {
        if (!has(o, k)) throw new IllegalArgumentException("Thiếu dữ liệu: " + k);
        return dbl(o, k, 0);
    }

    public static long lng(JsonObject o, String k, long def) {
        if (!has(o, k)) return def;
        try { return o.get(k).getAsLong(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Giá trị không hợp lệ: " + k); }
    }

    public static long reqLong(JsonObject o, String k) {
        if (!has(o, k)) throw new IllegalArgumentException("Thiếu dữ liệu: " + k);
        return lng(o, k, 0);
    }

    public static int integer(JsonObject o, String k, int def) {
        return (int) lng(o, k, def);
    }

    public static boolean bool(JsonObject o, String k, boolean def) {
        if (!has(o, k)) return def;
        JsonElement e = o.get(k);
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsInt() != 0;
            String s = p.getAsString();
            return s.equalsIgnoreCase("true") || s.equals("1");
        }
        return def;
    }
}
