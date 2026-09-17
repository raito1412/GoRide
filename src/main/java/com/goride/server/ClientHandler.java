package com.goride.server;

import com.goride.common.Json;
import com.goride.common.Log;
import com.goride.common.Protocol;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Một thread cho mỗi client TCP. */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private DataOutputStream out;

    volatile long userId = -1;
    volatile String role;
    volatile String fullName;
    volatile String udpToken;
    // chỉ dùng cho tài xế
    volatile boolean driverOnline;
    volatile boolean approved;
    volatile String vehicleType;
    final Set<Long> rejectedRides = ConcurrentHashMap.newKeySet();
    final Set<Long> rejectedOrders = ConcurrentHashMap.newKeySet();

    public ClientHandler(Socket socket) { this.socket = socket; }

    boolean loggedIn() { return userId > 0; }

    @Override
    public void run() {
        String who = socket.getRemoteSocketAddress().toString();
        Log.info("Kết nối mới " + who);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            while (!socket.isClosed()) handle(Protocol.read(in));
        } catch (EOFException | SocketException ignored) {
            // client đóng kết nối
        } catch (IOException | RuntimeException e) {
            Log.error("Lỗi kết nối " + who + " (client gửi dữ liệu không đúng giao thức?)", e);
        } finally {
            cleanup();
            Log.info("Ngắt kết nối " + who + (loggedIn() ? " (user #" + userId + ")" : ""));
        }
    }

    private void handle(JsonObject msg) {
        String type = Json.str(msg, "type", "");
        JsonObject data = msg.has("data") && msg.get("data").isJsonObject() ? msg.getAsJsonObject("data") : new JsonObject();
        JsonObject res = Json.obj("type", Protocol.RESPONSE, "rid", Json.lng(msg, "rid", 0));
        try {
            JsonElement result = Router.handle(this, type, data);
            res.addProperty("ok", true);
            if (result != null) res.add("data", result);
        } catch (AppException | IllegalArgumentException e) {
            res.addProperty("ok", false);
            res.addProperty("message", e.getMessage());
        } catch (Exception | Error e) {
            Log.error("Xử lý " + type + " thất bại", e);
            res.addProperty("ok", false);
            res.addProperty("message", Db.isConstraintViolation(e)
                    ? "Dữ liệu bị trùng hoặc đang được sử dụng ở nơi khác"
                    : "Lỗi máy chủ, vui lòng thử lại");
        }
        send(res);
    }

    void send(JsonObject msg) {
        if (out == null) return;
        try { Protocol.write(out, msg); }
        catch (IOException e) { close(); }
    }

    void push(String event, JsonObject data) {
        send(Json.obj("type", Protocol.PUSH, "event", event, "data", data));
    }

    void kick(String reason) {
        push("SESSION_CLOSED", Json.obj("reason", reason));
        close();
    }

    void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }

    /** Gọi khi đăng xuất hoặc mất kết nối. */
    void cleanup() {
        if (!loggedIn()) return;
        Sessions.unbind(this);
        if ("DRIVER".equals(role) && Sessions.get(userId) == null) {
            driverOnline = false;
            Sessions.LOCATIONS.remove(userId);
            try { Db.update("UPDATE drivers SET is_online=0 WHERE user_id=?", userId); }
            catch (RuntimeException e) { Log.error("Không cập nhật được trạng thái offline", e); }
        }
    }
}
