package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Config;
import com.goride.common.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.function.IntConsumer;

/** Giao diện Khách hàng: đặt xe, đặt đồ ăn, lịch sử, thanh toán, hồ sơ. */
public class UserFrame extends BaseFrame {
    private final MapPanel map = new MapPanel(Config.defaultLat(), Config.defaultLng());
    private final JTabbedPane tabs = newTabs();
    private final RidePanel ride;
    private final FoodPanel food;
    private final DataTable rides = new DataTable("id|#", "created_at|Thời gian|time", "vehicle_type|Xe|status",
            "drop_address|Điểm đến", "price|Giá|money", "status|Trạng thái|status", "my_stars|Đánh giá|stars");
    private final DataTable orders = new DataTable("id|#", "created_at|Thời gian|time", "restaurant_name|Nhà hàng",
            "total|Tổng|money", "status|Trạng thái|status", "my_stars|Đánh giá|stars");
    private final DataTable payments = new DataTable("id|#", "created_at|Thời gian|time", "ref_type|Loại|status",
            "ref_id|Mã", "amount|Số tiền|money", "method|Hình thức|status", "status|Trạng thái|status");
    private final JLabel paySummary = Ui.muted(" ");

    public UserFrame(Net net) {
        super(net, "Khách hàng", Theme.LAVENDER, Theme.LAVENDER_DEEP);
        ride = new RidePanel(net, map, () -> tabs.getSelectedIndex() == 0);
        food = new FoodPanel(net, map, () -> tabs.getSelectedIndex() == 1);
        ProfilePanel profile = new ProfilePanel(net);
        profile.onUpdated(p -> nameLabel.setText(Json.str(p, "full_name", "")));

        tabs.addTab("🛵 Đặt xe", ride);
        tabs.addTab("🍜 Đồ ăn", food);
        tabs.addTab("🕘 Lịch sử", buildHistory());
        tabs.addTab("💳 Thanh toán", buildPayments());
        tabs.addTab("👤 Tài khoản", profile);
        tabs.addChangeListener(e -> tabChanged());
        setBody(split(tabs, map, 470));

        // Chuyến/đơn thay đổi -> làm mới lịch sử
        net.on("RIDE_UPDATE", d -> loadHistory());
        net.on("FOOD_UPDATE", d -> loadHistory());
        loadHistory();
        SwingUtilities.invokeLater(() -> ride.drawMap(true));
    }

    private void tabChanged() {
        switch (tabs.getSelectedIndex()) {
            case 0 -> ride.drawMap(true);
            case 1 -> {
                food.loadRestaurants();
                food.drawMap(true);
            }
            case 2 -> {
                loadHistory();
                clearMap();
            }
            case 3 -> {
                loadPayments();
                clearMap();
            }
            default -> clearMap();
        }
    }

    private void clearMap() {
        map.clearAll();
        map.onPick(null, null);
    }

    // ---------- Lịch sử ----------

    private JPanel buildHistory() {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        rides.widths(40, 110, 60, 200, 80, 110, 80);
        orders.widths(40, 110, 170, 80, 110, 80);
        rides.onSelect(r -> {
            if (r == null || tabs.getSelectedIndex() != 2) return;
            // bảng lịch sử chỉ có dữ liệu cơ bản -> hiện trên bản đồ theo toạ độ có sẵn
            map.clearAll();
            map.setMarker("pickup", Json.dbl(r, "pickup_lat", 0), Json.dbl(r, "pickup_lng", 0), Theme.MINT_DEEP, "Đón");
            map.setMarker("drop", Json.dbl(r, "drop_lat", 0), Json.dbl(r, "drop_lng", 0), Theme.ROSE_DEEP, "Đến");
            map.fit();
        });
        orders.onSelect(o -> {
            if (o == null || tabs.getSelectedIndex() != 2) return;
            map.clearAll();
            map.setMarker("restaurant", Json.dbl(o, "restaurant_lat", 0), Json.dbl(o, "restaurant_lng", 0), Theme.PEACH_DEEP, Json.str(o, "restaurant_name"));
            map.setMarker("delivery", Json.dbl(o, "delivery_lat", 0), Json.dbl(o, "delivery_lng", 0), Theme.ROSE_DEEP, "Giao tới");
            map.fit();
        });
        orders.onDoubleClick(this::showOrderDetail);

        p.add(block("Chuyến xe", rides, Ui.button("Đánh giá chuyến", Theme.BUTTER, () -> reviewSelected(rides, "RIDE", "COMPLETED"))));
        p.add(block("Đơn đồ ăn", orders,
                Ui.button("Xem chi tiết", Theme.SURFACE, () -> showOrderDetail(orders.selected())),
                Ui.button("Đánh giá đơn", Theme.BUTTER, () -> reviewSelected(orders, "FOOD", "DELIVERED"))));
        return p;
    }

    private JPanel block(String title, DataTable table, JButton... actions) {
        JPanel b = new JPanel(new BorderLayout(0, 4));
        JPanel head = new JPanel(new BorderLayout());
        head.add(Ui.label(title, 13f, Font.BOLD), BorderLayout.WEST);
        JPanel act = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        for (JButton a : actions) act.add(a);
        head.add(act, BorderLayout.EAST);
        b.add(head, BorderLayout.NORTH);
        b.add(table.scroll());
        return b;
    }

    private void loadHistory() {
        net.quiet("RIDE_HISTORY", null, rides::setRows);
        net.quiet("FOOD_HISTORY", null, orders::setRows);
    }

    private void reviewSelected(DataTable table, String type, String doneStatus) {
        JsonObject r = table.selected();
        if (r == null) {
            Ui.info(this, "Chọn một dòng trong bảng");
            return;
        }
        if (!doneStatus.equals(Json.str(r, "status"))) {
            Ui.info(this, "Chỉ đánh giá được khi đã hoàn thành");
            return;
        }
        if (Json.has(r, "my_stars")) {
            Ui.info(this, "Bạn đã đánh giá rồi");
            return;
        }
        review(this, net, type, Json.lng(r, "id", -1), s -> loadHistory());
    }

    private void showOrderDetail(JsonObject row) {
        if (row == null) return;
        net.run(this, "FOOD_DETAIL", Json.obj("id", Json.lng(row, "id", -1)), r -> {
            JsonObject o = r.getAsJsonObject();
            StringBuilder s = new StringBuilder("<html><b>Đơn #" + Json.lng(o, "id", 0) + " · "
                    + Ui.esc(Json.str(o, "restaurant_name")) + "</b><br><br>");
            for (JsonElement e : o.getAsJsonArray("items")) {
                JsonObject it = e.getAsJsonObject();
                s.append(Json.integer(it, "quantity", 1)).append(" × ").append(Ui.esc(Json.str(it, "item_name")))
                        .append(" — ").append(Ui.money(Json.dbl(it, "unit_price", 0) * Json.integer(it, "quantity", 1))).append("<br>");
            }
            s.append("<br>Tiền món: ").append(Ui.money(o, "items_total"))
                    .append("<br>Phí giao: ").append(Ui.money(o, "delivery_fee"))
                    .append("<br><b>Tổng: ").append(Ui.money(o, "total")).append("</b> (").append(Ui.vi(Json.str(o, "payment_method"))).append(")")
                    .append("<br>Giao tới: ").append(Ui.esc(Json.str(o, "delivery_address")))
                    .append("<br>Tài xế: ").append(Ui.esc(Json.str(o, "driver_name", "—")))
                    .append("<br>Trạng thái: ").append(Ui.vi(Json.str(o, "status"))).append("</html>");
            Ui.info(this, s.toString());
        });
    }

    // ---------- Thanh toán ----------

    private JPanel buildPayments() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        JPanel head = new JPanel(new BorderLayout());
        head.add(Ui.label("Lịch sử thanh toán", 13f, Font.BOLD), BorderLayout.WEST);
        head.add(Ui.button("Làm mới", Theme.SURFACE, this::loadPayments), BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);
        p.add(payments.scroll());
        p.add(paySummary, BorderLayout.SOUTH);
        return p;
    }

    private void loadPayments() {
        net.quiet("PAYMENT_LIST", null, r -> {
            payments.setRows(r);
            double paid = 0;
            for (JsonElement e : r.getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                if ("PAID".equals(Json.str(o, "status"))) paid += Json.dbl(o, "amount", 0);
            }
            paySummary.setText("<html>Tổng đã thanh toán: <b>" + Ui.money(paid)
                    + "</b><br>Chọn Tiền mặt / Ví GoRide khi đặt; hệ thống ghi nhận khi chuyến/đơn hoàn thành.</html>");
        });
    }

    // ---------- Đánh giá (dùng chung) ----------

    static void review(Component parent, Net net, String refType, long refId, IntConsumer done) {
        JComboBox<String> stars = new JComboBox<>(new String[]{"★★★★★  Tuyệt vời", "★★★★☆  Tốt", "★★★☆☆  Bình thường", "★★☆☆☆  Chưa tốt", "★☆☆☆☆  Tệ"});
        JTextArea comment = new JTextArea(3, 26);
        comment.setLineWrap(true);
        comment.setWrapStyleWord(true);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(stars, BorderLayout.NORTH);
        p.add(new JScrollPane(comment));
        String title = "Đánh giá " + ("RIDE".equals(refType) ? "chuyến #" : "đơn #") + refId;
        if (JOptionPane.showConfirmDialog(parent, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        int n = 5 - stars.getSelectedIndex();
        net.run(parent, "REVIEW_CREATE", Json.obj("ref_type", refType, "ref_id", refId, "stars", n, "comment", comment.getText().trim()), r -> {
            Ui.info(parent, Json.str(r.getAsJsonObject(), "message", "Đã gửi đánh giá"));
            done.accept(n);
        });
    }
}
