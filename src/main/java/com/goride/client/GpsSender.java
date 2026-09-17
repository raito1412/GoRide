package com.goride.client;

import com.goride.common.Config;
import com.goride.common.Protocol;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Tài xế gửi vị trí lên server qua UDP mỗi 2 giây khi đang Online. */
public class GpsSender implements AutoCloseable {
    private final String token;
    private final int port;
    private final AtomicLong seq = new AtomicLong();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gps-sender");
        t.setDaemon(true);
        return t;
    });
    private DatagramSocket socket;
    private InetAddress host;
    private volatile double lat, lng;
    private volatile boolean enabled;

    public GpsSender(String token, int port, double lat, double lng) {
        this.token = token;
        this.port = port;
        this.lat = lat;
        this.lng = lng;
        try {
            socket = new DatagramSocket();
            host = InetAddress.getByName(Config.serverHost());
        } catch (Exception e) {
            throw new IllegalStateException("Không mở được UDP: " + e.getMessage());
        }
        timer.scheduleAtFixedRate(this::tick, 0, 2, TimeUnit.SECONDS);
    }

    public void setPosition(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public double lat() { return lat; }
    public double lng() { return lng; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) timer.execute(this::tick);
    }

    private void tick() {
        if (!enabled) return;
        try {
            byte[] b = Protocol.gpsPacket(token, lat, lng, seq.incrementAndGet()).getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(b, b.length, host, port));
        } catch (Exception ignored) {
            // UDP: mất gói thì gói sau bù
        }
    }

    @Override
    public void close() {
        timer.shutdownNow();
        socket.close();
    }
}
