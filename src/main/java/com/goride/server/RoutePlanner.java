package com.goride.server;

import com.goride.common.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lộ trình cho tài xế đang giữ nhiều đơn đồ ăn cùng lúc (tối đa Sessions.MAX_FOOD_BATCH = 3).
 *
 * Bài toán: xuất phát từ chỗ tài xế đang đứng, đi qua mọi điểm LẤY MÓN (quán) và GIAO MÓN (khách) sao cho
 * tổng thời gian chạy xe nhỏ nhất, với ràng buộc đơn nào cũng phải lấy ở quán trước rồi mới giao cho khách.
 * Đây là bài toán Lấy-và-Giao hàng (Pickup and Delivery Problem) — biến thể của bài toán người bán hàng (TSP)
 * có ràng buộc thứ tự. Bài toán tổng quát là NP-khó, nhưng ở đây có tối đa 3 đơn = 6 điểm dừng, số cách đi
 * hợp lệ tối đa là 6! / 2^3 = 90, nên giải CHÍNH XÁC (không phải gần đúng) bằng duyệt nhánh-cận
 * (branch and bound): mở rộng lần lượt từng điểm được phép đi tiếp, cắt nhánh ngay khi thời gian đã cộng
 * vượt nghiệm tốt nhất đang có. Chi phí lấy từ ma trận thời gian chạy xe theo đường thật (Mapbox Matrix);
 * hai cách đi bằng thời gian thì chọn cách có quãng đường ngắn hơn.
 */
public final class RoutePlanner {
    static final String PICKUP = "PICKUP", DROPOFF = "DROPOFF";
    private static final String ACTIVE = "('ACCEPTED','AT_RESTAURANT','PICKED_UP','DELIVERING')";

    record Stop(long orderId, String kind, double lat, double lng, String name, String address) {}

    private RoutePlanner() {}

    static JsonObject plan(long driverId) {
        List<Stop> stops = new ArrayList<>();
        List<Long> orderIds = new ArrayList<>();
        for (JsonElement e : Db.query("""
                SELECT o.id, o.status, r.name AS rname, r.address AS raddr, r.lat AS rlat, r.lng AS rlng,
                       o.delivery_address, o.delivery_lat, o.delivery_lng, u.full_name AS customer
                FROM food_orders o JOIN restaurants r ON r.id = o.restaurant_id JOIN users u ON u.id = o.user_id
                WHERE o.driver_id=? AND o.status IN""" + ACTIVE + " ORDER BY o.id", driverId)) {
            JsonObject o = e.getAsJsonObject();
            long id = o.get("id").getAsLong();
            orderIds.add(id);
            String st = Json.str(o, "status");
            // chưa lấy món thì còn điểm lấy ở quán; lấy rồi thì chỉ còn điểm giao
            if (st.equals("ACCEPTED") || st.equals("AT_RESTAURANT"))
                stops.add(new Stop(id, PICKUP, o.get("rlat").getAsDouble(), o.get("rlng").getAsDouble(),
                        Json.str(o, "rname"), Json.str(o, "raddr")));
            stops.add(new Stop(id, DROPOFF, o.get("delivery_lat").getAsDouble(), o.get("delivery_lng").getAsDouble(),
                    Json.str(o, "customer"), Json.str(o, "delivery_address")));
        }
        double[] start = driverPosition(driverId, stops);
        if (stops.isEmpty())
            return Json.obj("orders", 0, "stops", new JsonArray(), "points", new JsonArray(),
                    "total_distance_m", 0, "total_duration_s", 0);

        List<double[]> pts = new ArrayList<>();
        pts.add(start);
        for (Stop s : stops) pts.add(new double[]{s.lat, s.lng});
        JsonObject m = MapboxClient.matrix(pts);
        // giữ nguyên số thực: Mapbox trả giây lẻ, làm tròn từng chặng rồi cộng có thể chọn sai thứ tự
        double[][] dur = toMatrix(m.getAsJsonArray("dur")), dist = toMatrix(m.getAsJsonArray("dist"));

        Search best = solve(stops, dur, dist);
        int[] naive = naiveOrder(stops, orderIds);
        double[] naiveCost = cost(naive, dur, dist);

        List<double[]> path = new ArrayList<>();
        path.add(start);
        for (int i : best.order) path.add(new double[]{stops.get(i).lat, stops.get(i).lng});
        JsonObject road = MapboxClient.routeVia(path);

        JsonArray out = new JsonArray();
        int prev = 0;
        double eta = 0, km = 0;
        for (int seq = 0; seq < best.order.length; seq++) {
            int i = best.order[seq];
            Stop s = stops.get(i);
            eta += dur[prev][i + 1];
            km += dist[prev][i + 1];
            out.add(Json.obj("seq", seq + 1, "order_id", s.orderId, "kind", s.kind, "name", s.name,
                    "address", s.address, "lat", s.lat, "lng", s.lng,
                    "leg_duration_s", Math.round(dur[prev][i + 1]), "leg_distance_m", Math.round(dist[prev][i + 1]),
                    "eta_s", Math.round(eta), "distance_from_start_m", Math.round(km)));
            prev = i + 1;
        }

        JsonArray labels = new JsonArray();
        labels.add("Tài xế");
        for (Stop s : stops) labels.add((s.kind.equals(PICKUP) ? "Lấy #" : "Giao #") + s.orderId);
        return Json.obj(
                "orders", orderIds.size(), "stops", out, "points", road.get("points"),
                "total_duration_s", Math.round(best.dur), "total_distance_m", Math.round(best.dist),
                "naive_duration_s", Math.round(naiveCost[0]), "naive_distance_m", Math.round(naiveCost[1]),
                "saved_duration_s", Math.round(naiveCost[0] - best.dur), "saved_distance_m", Math.round(naiveCost[1] - best.dist),
                // số thứ tự đi hợp lệ, và số nhánh phải đi tới tận cùng sau khi cắt nhánh
                "candidates", best.valid, "leaves_reached", best.leaves,
                "exact_duration_s", best.dur, "exact_distance_m", best.dist,
                "source", Json.str(m, "source"),
                "algorithm", "Pickup & Delivery TSP — duyệt nhánh-cận (branch and bound), nghiệm chính xác",
                // trả kèm ma trận để kiểm chứng độc lập là nghiệm thật sự tối ưu
                "matrix", Json.obj("labels", labels, "dur", m.get("dur"), "dist", m.get("dist")));
    }

    // ---------- Thuật toán ----------

    private static final class Search {
        int[] order;
        double dur = Double.MAX_VALUE, dist = Double.MAX_VALUE;
        int leaves, valid;
    }

    /** Duyệt nhánh-cận trên mọi thứ tự đi hợp lệ (lấy trước, giao sau). Vị trí 0 trong ma trận là tài xế. */
    static Search solve(List<Stop> stops, double[][] dur, double[][] dist) {
        int n = stops.size();
        int[] pickupOf = new int[n]; // điểm giao i phải chờ điểm lấy pickupOf[i] (-1 nếu món đã lấy rồi)
        for (int i = 0; i < n; i++) {
            pickupOf[i] = -1;
            if (stops.get(i).kind.equals(DROPOFF))
                for (int j = 0; j < n; j++)
                    if (stops.get(j).orderId == stops.get(i).orderId && stops.get(j).kind.equals(PICKUP)) pickupOf[i] = j;
        }
        Search best = new Search();
        dfs(0, 0, new int[n], 0, 0, 0, n, pickupOf, dur, dist, best);
        best.valid = countValid(0, n, pickupOf);
        return best;
    }

    /** Đếm số thứ tự đi hợp lệ (để báo cáo quy mô bài toán). */
    private static int countValid(int visited, int n, int[] pickupOf) {
        if (Integer.bitCount(visited) == n) return 1;
        int c = 0;
        for (int i = 0; i < n; i++) {
            if ((visited & (1 << i)) != 0) continue;
            if (pickupOf[i] >= 0 && (visited & (1 << pickupOf[i])) == 0) continue;
            c += countValid(visited | (1 << i), n, pickupOf);
        }
        return c;
    }

    private static void dfs(int at, int visited, int[] path, int depth, double d, double km, int n,
                            int[] pickupOf, double[][] dur, double[][] dist, Search best) {
        if (depth == n) {
            best.leaves++;
            if (d < best.dur || (d == best.dur && km < best.dist)) {
                best.dur = d;
                best.dist = km;
                best.order = path.clone();
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            if ((visited & (1 << i)) != 0) continue;
            if (pickupOf[i] >= 0 && (visited & (1 << pickupOf[i])) == 0) continue; // chưa lấy món thì chưa được giao
            double nd = d + dur[at][i + 1];
            if (nd > best.dur) continue; // cắt nhánh: đã chậm hơn nghiệm tốt nhất
            path[depth] = i;
            dfs(i + 1, visited | (1 << i), path, depth + 1, nd, km + dist[at][i + 1], n, pickupOf, dur, dist, best);
        }
    }

    /** Cách đi "ngây thơ" để so sánh: làm xong từng đơn một theo mã đơn (lấy rồi giao, rồi mới sang đơn kế). */
    private static int[] naiveOrder(List<Stop> stops, List<Long> orderIds) {
        int[] o = new int[stops.size()];
        int k = 0;
        for (long id : orderIds) {
            for (String kind : new String[]{PICKUP, DROPOFF})
                for (int i = 0; i < stops.size(); i++)
                    if (stops.get(i).orderId == id && stops.get(i).kind.equals(kind)) o[k++] = i;
        }
        return o;
    }

    static double[] cost(int[] order, double[][] dur, double[][] dist) {
        double d = 0, km = 0;
        int at = 0;
        for (int i : order) {
            d += dur[at][i + 1];
            km += dist[at][i + 1];
            at = i + 1;
        }
        return new double[]{d, km};
    }

    private static double[][] toMatrix(JsonArray a) {
        double[][] m = new double[a.size()][];
        for (int i = 0; i < a.size(); i++) {
            JsonArray row = a.get(i).getAsJsonArray();
            m[i] = new double[row.size()];
            for (int j = 0; j < row.size(); j++) m[i][j] = row.get(j).getAsDouble();
        }
        return m;
    }

    private static double[] driverPosition(long driverId, List<Stop> stops) {
        double[] p = Sessions.LOCATIONS.get(driverId);
        if (p != null) return p;
        JsonObject d = Db.one("SELECT last_lat, last_lng FROM drivers WHERE user_id=?", driverId);
        if (d != null && Json.has(d, "last_lat")) return new double[]{d.get("last_lat").getAsDouble(), d.get("last_lng").getAsDouble()};
        return stops.isEmpty() ? new double[]{0, 0} : new double[]{stops.get(0).lat, stops.get(0).lng};
    }
}
