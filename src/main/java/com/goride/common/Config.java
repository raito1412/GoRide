package com.goride.common;

import io.github.cdimascio.dotenv.Dotenv;

/** Đọc cấu hình từ file .env ở thư mục gốc project (biến môi trường hệ thống được ưu tiên). */
public final class Config {
    private static final Dotenv ENV = Dotenv.configure().ignoreIfMissing().load();

    private Config() {}

    public static String get(String key, String def) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) v = ENV.get(key, def);
        return v == null || v.isBlank() ? def : v.trim();
    }

    public static int getInt(String key, int def) {
        try { return Integer.parseInt(get(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    public static double getDouble(String key, double def) {
        try { return Double.parseDouble(get(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    public static String serverHost() { return get("SERVER_HOST", "127.0.0.1"); }
    public static int tcpPort()       { return getInt("TCP_PORT", 5050); }
    public static int udpPort()       { return getInt("UDP_PORT", 5051); }
    public static double defaultLat() { return getDouble("MAP_DEFAULT_LAT", 10.7769); }
    public static double defaultLng() { return getDouble("MAP_DEFAULT_LNG", 106.7009); }
}
