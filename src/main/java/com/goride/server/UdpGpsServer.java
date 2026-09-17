package com.goride.server;

import com.goride.common.Json;
import com.goride.common.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nhận GPS của tài xế qua UDP: "GPS;token;lat;lng;seq".
 * UDP phù hợp vì gói mất/đến trễ không sao — gói sau sẽ ghi đè; gói cũ (seq nhỏ hơn) bị bỏ.
 */
public class UdpGpsServer implements Runnable {
    private static final long PERSIST_EVERY_MS = 15_000;
    private final int port;
    private final Map<String, Long> lastSeq = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastPersist = new ConcurrentHashMap<>();

    public UdpGpsServer(int port) { this.port = port; }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            Log.info("UDP GPS lắng nghe cổng " + port);
            byte[] buf = new byte[512];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                handle(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.error("UDP server dừng", e);
        }
    }

    private void handle(String msg) {
        String[] p = msg.trim().split(";");
        if (p.length != 5 || !p[0].equals("GPS")) return;
        Long driverId = Sessions.UDP_TOKENS.get(p[1]);
        if (driverId == null) return; // token lạ -> bỏ qua
        double lat, lng;
        long seq;
        try {
            lat = Double.parseDouble(p[2]);
            lng = Double.parseDouble(p[3]);
            seq = Long.parseLong(p[4]);
        } catch (NumberFormatException e) {
            return;
        }
        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return;
        Long prev = lastSeq.put(p[1], seq);
        if (prev != null && seq <= prev) {
            lastSeq.put(p[1], prev);
            return; // gói đến trễ
        }
        Sessions.LOCATIONS.put(driverId, new double[]{lat, lng});

        long now = System.currentTimeMillis();
        if (now - lastPersist.getOrDefault(driverId, 0L) > PERSIST_EVERY_MS) {
            lastPersist.put(driverId, now);
            RideService.safe(() -> Db.update("UPDATE drivers SET last_lat=?, last_lng=? WHERE user_id=?", lat, lng, driverId));
        }

        Sessions.Job job = Sessions.ACTIVE_JOBS.get(driverId);
        if (job != null && job.customerId() > 0) {
            Sessions.push(job.customerId(), "DRIVER_LOCATION", Json.obj(
                    "driver_id", driverId, "lat", lat, "lng", lng, "ref_type", job.refType(), "ref_id", job.refId()));
        }
    }
}
