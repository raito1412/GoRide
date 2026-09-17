package com.goride.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** Phần chung cho việc (chuyến xe / đơn đồ ăn) của tài xế. */
final class DriverJobs {
    private DriverJobs() {}

    static boolean busy(long driverId) { return Sessions.ACTIVE_JOBS.containsKey(driverId); }

    /**
     * Giữ chỗ trước khi UPDATE DB để 1 tài xế không nhận 2 việc cùng lúc.
     * Còn việc nhiều tài xế nhận cùng 1 chuyến được chặn bằng UPDATE ... WHERE status=... (chỉ 1 người thắng).
     */
    static void claim(ClientHandler c, String refType, long refId) {
        if (!c.approved) throw new AppException("Tài khoản chưa được Admin duyệt");
        if (!c.driverOnline) throw new AppException("Hãy bật Online trước khi nhận việc");
        if (Sessions.ACTIVE_JOBS.putIfAbsent(c.userId, new Sessions.Job(refType, refId, 0)) != null)
            throw new AppException("Bạn đang có một việc chưa hoàn tất");
    }

    static void release(long driverId) { Sessions.ACTIVE_JOBS.remove(driverId); }

    /** Yêu cầu đang chờ quanh tài xế (gọi khi vừa bật Online). */
    static JsonArray pending(ClientHandler c) {
        JsonArray out = new JsonArray();
        if (!c.driverOnline || busy(c.userId)) return out;
        for (JsonElement e : RideService.pendingFor(c)) out.add(e);
        for (JsonElement e : FoodService.pendingFor(c)) out.add(e);
        return out;
    }

    /** {kind: RIDE|FOOD, job: {...}} hoặc null. */
    static JsonElement current(long driverId) {
        JsonObject ride = RideService.currentForDriver(driverId);
        if (ride != null) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", "RIDE");
            o.add("job", ride);
            return o;
        }
        JsonObject order = FoodService.currentForDriver(driverId);
        if (order != null) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", "FOOD");
            o.add("job", order);
            return o;
        }
        return JsonNull.INSTANCE;
    }

    /** Khi tài xế đăng nhập lại mà vẫn còn việc dở. */
    static void restore(long driverId) {
        JsonObject ride = RideService.currentForDriver(driverId);
        if (ride != null) {
            Sessions.ACTIVE_JOBS.put(driverId, new Sessions.Job("RIDE", ride.get("id").getAsLong(), ride.get("user_id").getAsLong()));
            return;
        }
        JsonObject order = FoodService.currentForDriver(driverId);
        if (order != null) {
            Sessions.ACTIVE_JOBS.put(driverId, new Sessions.Job("FOOD", order.get("id").getAsLong(), order.get("user_id").getAsLong()));
        }
    }
}
