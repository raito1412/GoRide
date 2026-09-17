package com.goride.server;

import com.goride.common.Config;
import com.goride.common.Log;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServerMain {
    public static void main(String[] args) throws Exception {
        Log.info("GoRide server đang khởi động...");
        try {
            Db.one("SELECT 1 AS ok");
            Db.update("UPDATE drivers SET is_online=0");
        } catch (RuntimeException e) {
            Log.error("Không kết nối được MySQL. Kiểm tra `docker compose up -d` và các biến DB_* trong .env", e);
            System.exit(1);
        }
        MapboxClient.status().forEach(Log::info);

        Thread udp = new Thread(new UdpGpsServer(Config.udpPort()), "udp-gps");
        udp.setDaemon(true);
        udp.start();

        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket server = new ServerSocket(Config.tcpPort())) {
            Log.info("TCP lắng nghe cổng " + Config.tcpPort() + " — sẵn sàng nhận client");
            while (true) {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true);
                pool.submit(new ClientHandler(socket));
            }
        }
    }
}
