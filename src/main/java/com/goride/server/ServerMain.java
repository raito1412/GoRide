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
            // bộ đếm hết giờ chỉ nằm trong bộ nhớ: server tắt giữa chừng thì các đơn đang chờ sẽ kẹt mãi
            int food = Db.update("UPDATE food_orders SET status='CANCELLED', cancel_reason='Hệ thống khởi động lại' "
                    + "WHERE status IN ('PLACED','CONFIRMED') AND driver_id IS NULL");
            int rides = Db.update("UPDATE rides SET status='CANCELLED', cancel_reason='Hệ thống khởi động lại' "
                    + "WHERE status='SEARCHING'");
            // trả lại thẻ giảm giá của những đơn/chuyến vừa bị huỷ do khởi động lại
            Db.update("""
                    UPDATE vouchers v SET used_at=NULL, used_ref=NULL WHERE used_ref IS NOT NULL AND (
                      (used_ref LIKE 'FOOD#%' AND EXISTS (SELECT 1 FROM food_orders o WHERE CONCAT('FOOD#', o.id)=v.used_ref
                                                           AND o.status='CANCELLED'))
                      OR (used_ref LIKE 'RIDE#%' AND EXISTS (SELECT 1 FROM rides r WHERE CONCAT('RIDE#', r.id)=v.used_ref
                                                           AND r.status='CANCELLED')))""");
            if (food + rides > 0) Log.info("Dọn " + food + " đơn và " + rides + " chuyến đang chờ từ lần chạy trước");
        } catch (RuntimeException e) {
            Log.error("Không kết nối được MySQL. Kiểm tra `docker compose up -d` và các biến DB_* trong .env", e);
            System.exit(1);
        }
        MapboxClient.status().forEach(Log::info);
        Log.info(Mailer.status());

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
