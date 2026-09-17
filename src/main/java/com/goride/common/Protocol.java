package com.goride.common;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Giao thức TCP: mỗi gói = [4 byte độ dài][JSON UTF-8].
 *   Request : {"type":"RIDE_BOOK","rid":7,"data":{...}}
 *   Response: {"type":"RESPONSE","rid":7,"ok":true,"message":"...","data":...}
 *   Push    : {"type":"PUSH","event":"RIDE_UPDATE","data":{...}}
 * Giao thức UDP (tài xế -> server, mỗi 2 giây): "GPS;token;lat;lng;seq"
 */
public final class Protocol {
    public static final int MAX_FRAME = 8 * 1024 * 1024;
    public static final String RESPONSE = "RESPONSE";
    public static final String PUSH = "PUSH";

    private Protocol() {}

    public static void write(DataOutputStream out, JsonObject msg) throws IOException {
        byte[] bytes = Json.GSON.toJson(msg).getBytes(StandardCharsets.UTF_8);
        synchronized (out) {
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();
        }
    }

    public static JsonObject read(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0 || len > MAX_FRAME) throw new IOException("Kích thước gói không hợp lệ: " + len);
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    public static String gpsPacket(String token, double lat, double lng, long seq) {
        return String.format(Locale.US, "GPS;%s;%.6f;%.6f;%d", token, lat, lng, seq);
    }
}
