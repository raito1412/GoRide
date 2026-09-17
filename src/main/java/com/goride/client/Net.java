package com.goride.client;

import com.goride.common.Config;
import com.goride.common.Json;
import com.goride.common.Protocol;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.Component;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Kết nối TCP phía client: gọi request/response (có rid) và nhận PUSH từ server. */
public class Net {
    public static class RemoteError extends RuntimeException {
        public RemoteError(String msg) { super(msg); }
    }

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "net-call");
        t.setDaemon(true);
        return t;
    });

    private final Socket socket = new Socket();
    private DataOutputStream out;
    private final AtomicInteger seq = new AtomicInteger();
    private final Map<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<JsonObject>>> handlers = new ConcurrentHashMap<>();
    private volatile Runnable onDisconnect = () -> { };
    private volatile boolean closing;
    private volatile String disconnectReason = "Mất kết nối tới server";

    /** Thông tin tài khoản sau khi LOGIN (kèm udp_token nếu là tài xế). */
    public JsonObject me;

    public static Net connect() throws IOException {
        Net net = new Net();
        net.socket.connect(new InetSocketAddress(Config.serverHost(), Config.tcpPort()), 4000);
        net.socket.setTcpNoDelay(true);
        net.out = new DataOutputStream(new BufferedOutputStream(net.socket.getOutputStream()));
        DataInputStream in = new DataInputStream(new BufferedInputStream(net.socket.getInputStream()));
        Thread reader = new Thread(() -> net.readLoop(in), "net-reader");
        reader.setDaemon(true);
        reader.start();
        return net;
    }

    private void readLoop(DataInputStream in) {
        try {
            while (true) {
                JsonObject msg = Protocol.read(in);
                String type = Json.str(msg, "type", "");
                if (Protocol.RESPONSE.equals(type)) {
                    CompletableFuture<JsonObject> f = pending.remove(Json.lng(msg, "rid", -1));
                    if (f != null) f.complete(msg);
                } else if (Protocol.PUSH.equals(type)) {
                    String event = Json.str(msg, "event", "");
                    JsonObject data = msg.has("data") && msg.get("data").isJsonObject() ? msg.getAsJsonObject("data") : new JsonObject();
                    for (Consumer<JsonObject> h : handlers.getOrDefault(event, List.of()))
                        SwingUtilities.invokeLater(() -> h.accept(data));
                }
            }
        } catch (Exception e) { // IOException hoặc gói JSON hỏng
            boolean badProtocol = !(e instanceof IOException)
                    || String.valueOf(e.getMessage()).startsWith("Kích thước gói");
            disconnectReason = badProtocol
                    ? "Cổng " + Config.tcpPort() + " trả dữ liệu không đúng giao thức GoRide — có thể đang chạy chương trình khác ở cổng này"
                    : e instanceof EOFException
                    ? "Server đã đóng kết nối — xem log ở terminal server"
                    : "Mất kết nối tới server (" + e.getClass().getSimpleName() + ")";
            pending.values().forEach(f -> f.completeExceptionally(e));
            pending.clear();
            if (!closing) SwingUtilities.invokeLater(onDisconnect);
        }
    }

    /** Gọi đồng bộ (KHÔNG gọi trên luồng giao diện). */
    public JsonElement call(String type, JsonObject data) {
        long rid = seq.incrementAndGet();
        CompletableFuture<JsonObject> f = new CompletableFuture<>();
        pending.put(rid, f);
        try {
            Protocol.write(out, Json.obj("type", type, "rid", rid, "data", data == null ? new JsonObject() : data));
            JsonObject res = f.get(20, TimeUnit.SECONDS);
            if (!Json.bool(res, "ok", false)) throw new RemoteError(Json.str(res, "message", "Có lỗi xảy ra"));
            return res.has("data") ? res.get("data") : JsonNull.INSTANCE;
        } catch (TimeoutException e) {
            throw new RemoteError("Server không phản hồi");
        } catch (IOException | ExecutionException e) {
            throw new RemoteError(disconnectReason);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteError("Đã hủy");
        } finally {
            pending.remove(rid);
        }
    }

    /** Gọi nền, kết quả trả về trên luồng giao diện; lỗi hiện hộp thoại. */
    public void run(Component owner, String type, JsonObject data, Consumer<JsonElement> onOk) {
        POOL.submit(() -> {
            try {
                JsonElement result = call(type, data);
                if (onOk != null) SwingUtilities.invokeLater(() -> onOk.accept(result));
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(owner, e.getMessage(), "GoRide", JOptionPane.WARNING_MESSAGE));
            }
        });
    }

    /** Như run() nhưng bỏ qua lỗi (dùng cho làm mới định kỳ). */
    public void quiet(String type, JsonObject data, Consumer<JsonElement> onOk) {
        POOL.submit(() -> {
            try {
                JsonElement result = call(type, data);
                SwingUtilities.invokeLater(() -> onOk.accept(result));
            } catch (RuntimeException ignored) { }
        });
    }

    public void on(String event, Consumer<JsonObject> handler) {
        handlers.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void onDisconnect(Runnable r) { onDisconnect = r; }

    public long myId() { return me.get("id").getAsLong(); }

    public void close() {
        closing = true;
        try { socket.close(); } catch (IOException ignored) { }
    }
}
