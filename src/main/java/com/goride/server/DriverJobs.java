package com.goride.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Phần chung cho việc (chuyến xe / đơn đồ ăn) của tài xế.
 * Quy tắc: tối đa 1 chuyến xe, hoặc tối đa Sessions.MAX_FOOD_BATCH đơn đồ ăn giao gộp; không trộn hai loại.
 */
final class DriverJobs {
    private DriverJobs() {}

    static boolean busy(long driverId) { return !Sessions.jobs(driverId).isEmpty(); }

    /**
     * Giữ chỗ trước khi UPDATE DB, làm nguyên tử trong compute() nên hai lệnh nhận đơn đến cùng lúc
     * cũng không thể vượt giới hạn. Việc nhiều tài xế tranh cùng 1 đơn vẫn chặn bằng UPDATE ... WHERE status=...
     */
    static void claim(ClientHandler c, String refType, long refId) {
        if (!c.approved) throw new AppException("Tài khoản chưa được Admin duyệt");
        if (!c.driverOnline) throw new AppException("Hãy bật Online trước khi nhận việc");
        Sessions.ACTIVE_JOBS.compute(c.userId, (k, cur) -> {
            List<Sessions.Job> list = cur == null ? List.of() : cur;
            boolean ride = list.stream().anyMatch(j -> j.refType().equals("RIDE"));
            long food = list.stream().filter(j -> j.refType().equals("FOOD")).count();
            if (refType.equals("RIDE") && !list.isEmpty())
                throw new AppException(ride ? "Bạn đang chở khách, hoàn tất chuyến trước đã"
                        : "Bạn đang giao đồ ăn, giao xong mới nhận chở khách được");
            if (refType.equals("FOOD") && ride) throw new AppException("Bạn đang chở khách, không nhận thêm đơn đồ ăn được");
            if (refType.equals("FOOD") && food >= Sessions.MAX_FOOD_BATCH)
                throw new AppException("Bạn đang giữ đủ " + Sessions.MAX_FOOD_BATCH + " đơn, giao bớt rồi nhận thêm");
            List<Sessions.Job> next = new ArrayList<>(list);
            next.add(new Sessions.Job(refType, refId, 0));
            return List.copyOf(next);
        });
    }

    /** Ghi thêm người nhận vào việc vừa giữ chỗ (để đẩy vị trí tài xế cho đúng khách). */
    static void attachCustomer(long driverId, String refType, long refId, long customerId) {
        Sessions.ACTIVE_JOBS.computeIfPresent(driverId, (k, list) -> list.stream()
                .map(j -> j.refType().equals(refType) && j.refId() == refId ? new Sessions.Job(refType, refId, customerId) : j)
                .toList());
    }

    /** Bỏ một việc khỏi danh sách (giao xong / bị hủy / nhận hụt). */
    static void release(long driverId, String refType, long refId) {
        Sessions.ACTIVE_JOBS.computeIfPresent(driverId, (k, list) -> {
            List<Sessions.Job> next = list.stream().filter(j -> !(j.refType().equals(refType) && j.refId() == refId)).toList();
            return next.isEmpty() ? null : next;
        });
    }

    static void releaseAll(long driverId) { Sessions.ACTIVE_JOBS.remove(driverId); }

    /** Yêu cầu đang chờ quanh tài xế. Đang giao đồ ăn chưa đủ 3 đơn thì chỉ còn thấy đơn đồ ăn. */
    static JsonArray pending(ClientHandler c) {
        JsonArray out = new JsonArray();
        if (!c.driverOnline) return out;
        if (Sessions.freeForRide(c.userId)) for (JsonElement e : RideService.pendingFor(c)) out.add(e);
        if (Sessions.freeForFood(c.userId)) for (JsonElement e : FoodService.pendingFor(c)) out.add(e);
        return out;
    }

    /** {kind: RIDE, job} hoặc {kind: FOOD, jobs:[...], job: đơn đầu tiên}, hoặc null. */
    static JsonElement current(long driverId) {
        JsonObject ride = RideService.currentForDriver(driverId);
        if (ride != null) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", "RIDE");
            o.add("job", ride);
            return o;
        }
        JsonArray orders = FoodService.activeForDriver(driverId);
        if (orders.isEmpty()) return JsonNull.INSTANCE;
        JsonObject o = new JsonObject();
        o.addProperty("kind", "FOOD");
        o.add("jobs", orders);
        o.add("job", orders.get(0));
        return o;
    }

    /** Khi tài xế đăng nhập lại: dựng lại danh sách việc từ DB, kể cả khi không còn việc nào. */
    static void restore(long driverId) {
        List<Sessions.Job> list = new ArrayList<>();
        JsonObject ride = RideService.currentForDriver(driverId);
        if (ride != null) list.add(new Sessions.Job("RIDE", ride.get("id").getAsLong(), ride.get("user_id").getAsLong()));
        for (JsonElement e : FoodService.activeForDriver(driverId)) {
            JsonObject o = e.getAsJsonObject();
            list.add(new Sessions.Job("FOOD", o.get("id").getAsLong(), o.get("user_id").getAsLong()));
        }
        // DB không còn việc nào -> xoá dấu vết phiên trước, nếu không tài xế bị coi là "đang bận" mãi
        if (list.isEmpty()) Sessions.ACTIVE_JOBS.remove(driverId);
        else Sessions.ACTIVE_JOBS.put(driverId, List.copyOf(list));
    }
}
