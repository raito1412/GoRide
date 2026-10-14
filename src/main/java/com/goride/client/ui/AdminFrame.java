package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Config;
import com.goride.common.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;

/** Giao diện Admin: dashboard + quản lý người dùng, tài xế, chuyến, nhà hàng/menu, đơn, giá, thanh toán, review. */
public class AdminFrame extends BaseFrame {
    private final JTabbedPane tabs = newTabs();
    private final MapPanel map = new MapPanel(Config.defaultLat(), Config.defaultLng());
    private final Timer autoRefresh;

    // dashboard
    private final JLabel sUsers = new JLabel("—"), sDrivers = new JLabel("—"), sOnline = new JLabel("—"),
            sRides = new JLabel("—"), sOrders = new JLabel("—"), sActive = new JLabel("—"),
            sRevenueToday = new JLabel("—"), sRevenue = new JLabel("—"), sCommission = new JLabel("—"),
            sShops = new JLabel("—");
    private final RevenueChart chart = new RevenueChart();
    private final DataTable online = new DataTable("id|#", "full_name|Tài xế", "vehicle_type|Xe|status", "job|Đang làm");

    // danh sách
    private final DataTable users = new DataTable("id|#", "full_name|Họ tên", "phone|SĐT", "email|Email",
            "address|Địa chỉ", "status|Trạng thái|status", "created_at|Ngày tạo|time");
    private final DataTable drivers = new DataTable("id|#", "full_name|Họ tên", "phone|SĐT", "vehicle_type|Xe|status",
            "plate_number|Biển số", "vehicle_brand|Hãng", "vehicle_color|Màu", "license_number|GPLX",
            "approval|Duyệt|status", "status|Tài khoản|status", "is_online|Online|bool", "rating_avg|Đánh giá|rating");
    private final DataTable rides = new DataTable("id|#", "created_at|Thời gian|time", "user_name|Khách", "driver_name|Tài xế",
            "vehicle_type|Xe|status", "pickup_address|Điểm đón", "drop_address|Điểm đến", "distance_m|Quãng đường|km",
            "price|Giá|money", "payment_method|Thanh toán|status", "status|Trạng thái|status");
    private final JComboBox<String> rideFilter = new JComboBox<>(new String[]{"Đang chạy", "Tất cả"});
    private final DataTable applications = new DataTable("id|#", "created_at|Gửi lúc|time", "name|Tên quán",
            "category|Loại", "address|Địa chỉ", "owner_name|Chủ quán", "owner_phone|SĐT", "item_count|Số món",
            "status|Trạng thái|status", "admin_note|Ghi chú");
    private final JComboBox<String> appFilter = new JComboBox<>(new String[]{"Chờ duyệt", "Tất cả"});
    private final DataTable restaurants = new DataTable("id|#", "name|Tên", "category|Loại", "address|Địa chỉ",
            "phone|SĐT", "owner_name|Chủ quán", "lat|Lat", "lng|Lng", "is_open|Mở cửa|bool");
    private final DataTable menu = new DataTable("id|#", "name|Món", "description|Mô tả", "price|Giá|money", "is_available|Còn bán|bool");
    private final JLabel menuTitle = Ui.label("Thực đơn (chọn nhà hàng)", 13f, Font.BOLD);
    private final DataTable orders = new DataTable("id|#", "created_at|Thời gian|time", "user_name|Khách",
            "restaurant_name|Nhà hàng", "driver_name|Tài xế", "delivery_address|Giao tới", "items_total|Tiền món|money",
            "delivery_fee|Phí giao|money", "total|Tổng|money", "payment_method|Thanh toán|status", "status|Trạng thái|status");
    private final DataTable pricing = new DataTable("service|Dịch vụ|status", "base_fare|Giá mở cửa|money",
            "per_km|Mỗi km|money", "per_min|Mỗi phút|money", "min_fare|Tối thiểu|money", "updated_at|Cập nhật|time");
    private final DataTable payments = new DataTable("id|#", "created_at|Thời gian|time", "user_name|Khách",
            "ref_type|Loại|status", "ref_id|Mã", "amount|Số tiền|money", "method|Hình thức|status", "status|Trạng thái|status");
    private final DataTable reviews = new DataTable("id|#", "created_at|Thời gian|time", "user_name|Khách",
            "target|Đánh giá cho|status", "driver_name|Tài xế / Quán", "ref_type|Loại|status", "ref_id|Mã",
            "stars|Sao|stars", "comment|Nhận xét", "is_hidden|Đã ẩn|bool");

    public AdminFrame(Net net) {
        super(net, "Quản trị", Theme.PEACH, Theme.PEACH_DEEP);
        tabs.addTab("📊 Tổng quan", buildDashboard());
        tabs.addTab("👥 Khách hàng", page("Khách hàng", users, this::loadUsers,
                Ui.button("Khóa", Theme.ROSE, () -> userStatus(users, "LOCKED")),
                Ui.button("Mở khóa", Theme.MINT, () -> userStatus(users, "ACTIVE"))));
        tabs.addTab("🛵 Tài xế", page("Tài xế & phương tiện", drivers, this::loadDrivers,
                Ui.button("Duyệt", Theme.MINT, () -> approval("APPROVED")),
                Ui.button("Từ chối", Theme.BUTTER, () -> approval("REJECTED")),
                Ui.button("Sửa xe", Theme.SKY, this::editVehicle),
                Ui.button("Khóa", Theme.ROSE, () -> userStatus(drivers, "LOCKED")),
                Ui.button("Mở khóa", Theme.SURFACE, () -> userStatus(drivers, "ACTIVE"))));
        rideFilter.addActionListener(e -> loadRides());
        tabs.addTab("🚗 Chuyến xe", page("Chuyến xe", rides, this::loadRides, rideFilter,
                Ui.button("Hủy chuyến", Theme.ROSE, this::cancelRide)));
        appFilter.addActionListener(e -> loadApplications());
        tabs.addTab("🏪 Duyệt quán", page("Hồ sơ đăng ký mở quán ăn", applications, this::loadApplications, appFilter,
                Ui.button("Xem chi tiết", Theme.SKY, this::applicationDetail),
                Ui.button("Duyệt", Theme.MINT, this::approveApplication),
                Ui.button("Từ chối", Theme.ROSE, this::rejectApplication)));
        tabs.addTab("🍽 Nhà hàng & menu", buildRestaurants());
        tabs.addTab("🧾 Đơn đồ ăn", page("Đơn đồ ăn", orders, this::loadOrders,
                Ui.button("Xem chi tiết", Theme.SKY, this::orderDetail),
                Ui.button("Hủy đơn", Theme.ROSE, this::cancelOrder)));
        tabs.addTab("🏷 Bảng giá", page("Giá cước Bike / Car và phí giao đồ ăn", pricing, this::loadPricing,
                Ui.button("Sửa giá", Theme.PEACH, this::editPricing)));
        tabs.addTab("💳 Thanh toán", page("Thanh toán", payments, this::loadPayments,
                Ui.button("Đã thanh toán", Theme.MINT, () -> paymentStatus("PAID")),
                Ui.button("Chờ thanh toán", Theme.BUTTER, () -> paymentStatus("PENDING")),
                Ui.button("Hoàn tiền", Theme.ROSE, () -> paymentStatus("REFUNDED"))));
        tabs.addTab("⭐ Đánh giá", page("Đánh giá của khách", reviews, this::loadReviews,
                Ui.button("Ẩn / hiện", Theme.BUTTER, this::toggleReview)));
        tabs.addChangeListener(e -> refreshCurrent());
        setBody(tabs);

        orders.onDoubleClick(o -> orderDetail());
        applications.onDoubleClick(a -> applicationDetail());
        drivers.onDoubleClick(d -> editVehicle());
        restaurants.onDoubleClick(r -> editRestaurant(r));
        menu.onDoubleClick(m -> editMenuItem(m));
        pricing.onDoubleClick(p -> editPricing());

        autoRefresh = new Timer(5000, e -> {
            int i = tabs.getSelectedIndex();
            if (i == 0) loadDashboard();
            else if (i == 3 && rideFilter.getSelectedIndex() == 0) loadRides();
        });
        autoRefresh.start();
        loadDashboard();
    }

    @Override
    protected void beforeClose() {
        autoRefresh.stop();
    }

    private void refreshCurrent() {
        switch (tabs.getSelectedIndex()) {
            case 0 -> loadDashboard();
            case 1 -> loadUsers();
            case 2 -> loadDrivers();
            case 3 -> loadRides();
            case 4 -> loadApplications();
            case 5 -> loadRestaurants();
            case 6 -> loadOrders();
            case 7 -> loadPricing();
            case 8 -> loadPayments();
            case 9 -> loadReviews();
            default -> { }
        }
    }

    // ---------- Dashboard ----------

    private JComponent buildDashboard() {
        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel grid = new JPanel(new GridLayout(0, 3, 10, 10));
        grid.add(Ui.stat("Khách hàng", sUsers, Theme.LAVENDER));
        grid.add(Ui.stat("Tài xế (chờ duyệt)", sDrivers, Theme.MINT));
        grid.add(Ui.stat("Tài xế đang online", sOnline, Theme.SKY));
        grid.add(Ui.stat("Chuyến: hôm nay / xong", sRides, Theme.LAVENDER));
        grid.add(Ui.stat("Đơn: hôm nay / đã giao", sOrders, Theme.PEACH));
        grid.add(Ui.stat("Đang chạy: chuyến · đơn", sActive, Theme.BUTTER));
        grid.add(Ui.stat("Doanh thu hôm nay", sRevenueToday, Theme.MINT));
        grid.add(Ui.stat("Tổng doanh thu", sRevenue, Theme.ROSE));
        grid.add(Ui.stat("Phí nền tảng thu được", sCommission, Theme.PEACH));
        grid.add(Ui.stat("Quán ăn (hồ sơ chờ duyệt)", sShops, Theme.SKY));
        left.add(grid, BorderLayout.NORTH);

        JPanel chartBox = new JPanel(new BorderLayout(0, 6));
        chartBox.add(Ui.label("Doanh thu 7 ngày gần nhất", 13f, Font.BOLD), BorderLayout.NORTH);
        Ui.Card card = new Ui.Card(new BorderLayout(), Theme.SURFACE);
        card.add(chart);
        chartBox.add(card);
        left.add(chartBox);

        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 12));
        JPanel onlineBox = new JPanel(new BorderLayout(0, 4));
        onlineBox.add(Ui.label("Tài xế đang online (tự làm mới 5 giây)", 13f, Font.BOLD), BorderLayout.NORTH);
        JScrollPane os = online.scroll();
        os.setPreferredSize(new Dimension(300, 170));
        onlineBox.add(os);
        right.add(onlineBox, BorderLayout.NORTH);
        right.add(map);
        online.onSelect(d -> {
            if (d != null && Json.has(d, "lat")) map.center(Json.dbl(d, "lat", 0), Json.dbl(d, "lng", 0));
        });
        return split(left, right, 700);
    }

    private void loadDashboard() {
        net.quiet("ADMIN_DASHBOARD", null, r -> {
            JsonObject o = r.getAsJsonObject();
            sUsers.setText(num(o, "users"));
            sDrivers.setText(num(o, "drivers") + " (" + num(o, "drivers_pending") + ")");
            sOnline.setText(num(o, "drivers_online"));
            sRides.setText(num(o, "rides_today") + " / " + num(o, "rides_total"));
            sOrders.setText(num(o, "orders_today") + " / " + num(o, "orders_total"));
            sActive.setText(num(o, "rides_active") + " · " + num(o, "orders_active"));
            sShops.setText(num(o, "restaurants") + " (" + num(o, "applications_pending") + ")");
            sRevenueToday.setText(Ui.money(o, "revenue_today"));
            sRevenue.setText(Ui.money(o, "revenue_total"));
            sCommission.setText(Ui.money(o, "commission_total"));
            chart.setData(o.getAsJsonArray("last7"));
        });
        net.quiet("ADMIN_LIST", Json.obj("entity", "online_drivers"), r -> {
            online.setRows(r);
            map.clearAll();
            for (JsonElement e : r.getAsJsonArray()) {
                JsonObject d = e.getAsJsonObject();
                if (!Json.has(d, "lat")) continue;
                boolean busy = !"Rảnh".equals(Json.str(d, "job"));
                map.setMarker("d" + Json.lng(d, "id", 0), Json.dbl(d, "lat", 0), Json.dbl(d, "lng", 0),
                        busy ? Theme.PEACH_DEEP : Theme.MINT_DEEP, Json.str(d, "full_name"));
            }
        });
    }

    private static String num(JsonObject o, String k) {
        return String.valueOf(Json.lng(o, k, 0));
    }

    // ---------- Khung trang danh sách ----------

    private JPanel page(String title, DataTable table, Runnable refresh, JComponent... actions) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel head = new JPanel(new BorderLayout());
        head.add(Ui.label(title, 15f, Font.BOLD), BorderLayout.WEST);
        JPanel act = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        for (JComponent a : actions) act.add(a);
        act.add(Ui.button("↻ Làm mới", Theme.SURFACE, refresh));
        head.add(act, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);
        p.add(table.scroll());
        return p;
    }

    private void list(String entity, DataTable table) {
        net.quiet("ADMIN_LIST", Json.obj("entity", entity), table::setRows);
    }

    private JsonObject pick(DataTable table) {
        JsonObject r = table.selected();
        if (r == null) Ui.info(this, "Chọn một dòng trong bảng trước");
        return r;
    }

    /** Gọi lệnh rồi làm mới trang hiện tại. */
    private void act(String type, JsonObject data, String done) {
        net.run(this, type, data, r -> {
            refreshCurrent();
            if (done != null) Ui.info(this, done);
        });
    }

    private static long id(JsonObject o) { return Json.lng(o, "id", -1); }

    // ---------- Người dùng / tài xế ----------

    private void loadUsers() { list("users", users); }

    private void loadDrivers() { list("drivers", drivers); }

    private void userStatus(DataTable table, String status) {
        JsonObject r = pick(table);
        if (r == null) return;
        if (status.equals("LOCKED") && !Ui.confirm(this, "Khóa tài khoản " + Json.str(r, "full_name") + "?")) return;
        act("ADMIN_USER_STATUS", Json.obj("id", id(r), "status", status), null);
    }

    private void approval(String approval) {
        JsonObject r = pick(drivers);
        if (r == null) return;
        act("ADMIN_DRIVER_APPROVAL", Json.obj("id", id(r), "approval", approval), null);
    }

    private void editVehicle() {
        JsonObject r = pick(drivers);
        if (r == null) return;
        JsonObject d = Ui.form(this, "Xe của " + Json.str(r, "full_name"), r,
                "vehicle_type|Loại xe|enum:BIKE,CAR", "plate_number|Biển số", "vehicle_brand|Hãng xe",
                "vehicle_color|Màu xe", "license_number|Số GPLX");
        if (d == null) return;
        d.addProperty("id", id(r));
        act("ADMIN_VEHICLE_UPDATE", d, "Đã lưu thông tin xe");
    }

    // ---------- Chuyến / đơn ----------

    private void loadRides() { list(rideFilter.getSelectedIndex() == 0 ? "rides_active" : "rides", rides); }

    private void cancelRide() {
        JsonObject r = pick(rides);
        if (r == null || !Ui.confirm(this, "Hủy chuyến #" + id(r) + "?")) return;
        act("ADMIN_RIDE_CANCEL", Json.obj("id", id(r)), "Đã hủy chuyến");
    }

    private void loadOrders() { list("food_orders", orders); }

    private void cancelOrder() {
        JsonObject r = pick(orders);
        if (r == null || !Ui.confirm(this, "Hủy đơn #" + id(r) + "?")) return;
        act("ADMIN_FOOD_CANCEL", Json.obj("id", id(r)), "Đã hủy đơn");
    }

    private void orderDetail() {
        JsonObject r = pick(orders);
        if (r == null) return;
        net.run(this, "ADMIN_FOOD_DETAIL", Json.obj("id", id(r)), res -> {
            JsonObject o = res.getAsJsonObject();
            StringBuilder s = new StringBuilder("<html><b>Đơn #" + id(o) + "</b> · " + Ui.vi(Json.str(o, "status")) + "<br><br>");
            s.append("Khách: ").append(Ui.esc(Json.str(o, "user_name"))).append(" · ").append(Json.str(o, "user_phone", "")).append("<br>");
            s.append("Nhà hàng: ").append(Ui.esc(Json.str(o, "restaurant_name"))).append("<br>");
            s.append("Tài xế: ").append(Ui.esc(Json.str(o, "driver_name", "—"))).append("<br>");
            s.append("Giao tới: ").append(Ui.esc(Json.str(o, "delivery_address"))).append("<br><br>");
            for (JsonElement e : o.getAsJsonArray("items")) {
                JsonObject it = e.getAsJsonObject();
                s.append(Json.integer(it, "quantity", 1)).append(" × ").append(Ui.esc(Json.str(it, "item_name")))
                        .append(" — ").append(Ui.money(Json.dbl(it, "unit_price", 0) * Json.integer(it, "quantity", 1))).append("<br>");
            }
            s.append("<br>Phí giao ").append(Ui.money(o, "delivery_fee")).append(" · <b>Tổng ").append(Ui.money(o, "total")).append("</b>");
            if (Json.has(o, "cancel_reason")) s.append("<br>Lý do hủy: ").append(Ui.esc(Json.str(o, "cancel_reason")));
            Ui.info(this, s.append("</html>").toString());
        });
    }

    // ---------- Duyệt hồ sơ mở quán ----------

    private void loadApplications() {
        JsonObject req = Json.obj("entity", "applications");
        if (appFilter.getSelectedIndex() == 0) req.addProperty("status", "PENDING");
        net.quiet("ADMIN_LIST", req, applications::setRows);
    }

    private void approveApplication() {
        JsonObject r = pick(applications);
        if (r == null) return;
        if (!"PENDING".equals(Json.str(r, "status"))) {
            Ui.info(this, "Hồ sơ này đã được xử lý rồi");
            return;
        }
        if (!Ui.confirm(this, "Duyệt quán \"" + Json.str(r, "name") + "\"?\n"
                + "Hệ thống sẽ tạo quán cùng toàn bộ thực đơn trong hồ sơ.")) return;
        act("ADMIN_APPLICATION_REVIEW", Json.obj("id", id(r), "status", "APPROVED"), "Đã duyệt và tạo quán");
    }

    private void rejectApplication() {
        JsonObject r = pick(applications);
        if (r == null) return;
        if (!"PENDING".equals(Json.str(r, "status"))) {
            Ui.info(this, "Hồ sơ này đã được xử lý rồi");
            return;
        }
        String note = JOptionPane.showInputDialog(this,
                "Lý do từ chối \"" + Json.str(r, "name") + "\" (chủ quán sẽ đọc được):", "GoRide", JOptionPane.QUESTION_MESSAGE);
        if (note == null || note.isBlank()) return;
        act("ADMIN_APPLICATION_REVIEW", Json.obj("id", id(r), "status", "REJECTED", "admin_note", note.trim()), "Đã từ chối hồ sơ");
    }

    /** Xem toàn bộ hồ sơ kèm ảnh quán và ảnh từng món. */
    private void applicationDetail() {
        JsonObject r = pick(applications);
        if (r == null) return;
        net.run(this, "ADMIN_APPLICATION_DETAIL", Json.obj("id", id(r)), res -> {
            JsonObject o = res.getAsJsonObject();
            JPanel body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBackground(Theme.SURFACE);

            JPanel head = new JPanel(new BorderLayout(12, 0));
            head.setOpaque(false);
            head.setAlignmentX(Component.LEFT_ALIGNMENT);
            head.add(thumb(o, 170, 120), BorderLayout.WEST);
            head.add(new JLabel("<html><div style='width:360px'><b style='font-size:13px'>"
                    + Ui.esc(Json.str(o, "name")) + "</b> · " + Ui.esc(Json.str(o, "category", "—")) + "<br>"
                    + Ui.esc(Json.str(o, "address")) + "<br>"
                    + "<span style='color:#5A6472'>Chủ quán: " + Ui.esc(Json.str(o, "owner_name")) + " · "
                    + Ui.esc(Json.str(o, "owner_phone")) + " · " + Ui.esc(Json.str(o, "owner_email", "—")) + "</span><br>"
                    + "<span style='color:#5A6472'>SĐT quán: " + Ui.esc(Json.str(o, "phone", "—"))
                    + " · Gửi lúc " + Json.str(o, "created_at", "") + "</span><br><br>"
                    + Ui.esc(Json.str(o, "description", "")) + "</div></html>"));
            body.add(head);
            body.add(Box.createVerticalStrut(10));
            JsonArray items = o.getAsJsonArray("items");
            body.add(Ui.label("Thực đơn (" + items.size() + " món)", 13f, Font.BOLD));
            for (JsonElement e : items) {
                JsonObject it = e.getAsJsonObject();
                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setOpaque(false);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
                row.add(thumb(it, 90, 70), BorderLayout.WEST);
                row.add(new JLabel("<html><div style='width:390px'><b>" + Ui.esc(Json.str(it, "name")) + "</b> — "
                        + Ui.money(it.get("price").getAsDouble()) + "<br><span style='color:#5A6472'>"
                        + Ui.esc(Json.str(it, "description", "")) + "</span></div></html>"));
                body.add(row);
            }
            if (Json.has(o, "admin_note"))
                body.add(Ui.muted("<html><br>Ghi chú của Admin: " + Ui.esc(Json.str(o, "admin_note")) + "</html>"));

            JScrollPane sp = new JScrollPane(body);
            sp.setBorder(null);
            sp.getVerticalScrollBar().setUnitIncrement(18);
            sp.setPreferredSize(new Dimension(640, 480));
            JOptionPane.showMessageDialog(this, sp,
                    "Hồ sơ #" + id(o) + " · " + Ui.vi(Json.str(o, "status")), JOptionPane.PLAIN_MESSAGE);
        });
    }

    /** Ô ảnh cho quán/món; chưa có ảnh thì hiện khung xám. */
    private JLabel thumb(JsonObject o, int w, int h) {
        JLabel l = new JLabel("Không có ảnh", SwingConstants.CENTER);
        l.setPreferredSize(new Dimension(w, h));
        l.setOpaque(true);
        l.setBackground(Theme.ROW_ALT);
        l.setForeground(Theme.MUTED);
        l.setFont(l.getFont().deriveFont(11f));
        l.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        if (Json.has(o, "image_id")) {
            l.setText("...");
            ImageStore.load(net, o.get("image_id").getAsLong(), w - 2, h - 2, icon -> {
                l.setText("");
                l.setIcon(icon);
            });
        }
        return l;
    }

    // ---------- Nhà hàng & menu ----------

    private JComponent buildRestaurants() {
        JPanel top = page("Nhà hàng (hệ thống giả lập nhà hàng tự xác nhận đơn)", restaurants, this::loadRestaurants,
                Ui.button("Thêm", Theme.MINT, () -> editRestaurant(null)),
                Ui.button("Sửa", Theme.SKY, () -> {
                    JsonObject r = pick(restaurants);
                    if (r != null) editRestaurant(r);
                }),
                Ui.button("Xóa", Theme.ROSE, this::deleteRestaurant));
        restaurants.onSelect(r -> {
            menuTitle.setText(r == null ? "Thực đơn (chọn nhà hàng)" : "Thực đơn · " + Json.str(r, "name"));
            loadMenu();
        });

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        JPanel head = new JPanel(new BorderLayout());
        head.add(menuTitle, BorderLayout.WEST);
        JPanel act = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        act.add(Ui.button("Thêm món", Theme.MINT, () -> editMenuItem(null)));
        act.add(Ui.button("Sửa món", Theme.SKY, () -> {
            JsonObject m = pick(menu);
            if (m != null) editMenuItem(m);
        }));
        act.add(Ui.button("Xóa món", Theme.ROSE, this::deleteMenuItem));
        head.add(act, BorderLayout.EAST);
        bottom.add(head, BorderLayout.NORTH);
        bottom.add(menu.scroll());

        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        sp.setResizeWeight(0.5);
        sp.setBorder(null);
        return sp;
    }

    private void loadRestaurants() {
        list("restaurants", restaurants);
        loadMenu();
    }

    private void loadMenu() {
        JsonObject r = restaurants.selected();
        if (r == null) menu.setRows(new JsonArray());
        else net.quiet("ADMIN_LIST", Json.obj("entity", "menu", "restaurant_id", id(r)), menu::setRows);
    }

    private void editRestaurant(JsonObject r) {
        JsonObject d = Ui.form(this, r == null ? "Thêm nhà hàng" : "Sửa nhà hàng", r,
                "name|Tên nhà hàng", "category|Loại món", "address|Địa chỉ|area", "phone|Số điện thoại",
                "lat|Vĩ độ (0 = tự tìm theo địa chỉ)|num", "lng|Kinh độ (0 = tự tìm)|num", "is_open|Đang mở cửa|bool");
        if (d == null) return;
        if (r != null) {
            d.addProperty("id", id(r));
            // đổi địa chỉ mà giữ toạ độ cũ -> tìm lại toạ độ
            if (!Json.str(d, "address", "").equals(Json.str(r, "address", ""))
                    && Json.dbl(d, "lat", 0) == Json.dbl(r, "lat", 0) && Json.dbl(d, "lng", 0) == Json.dbl(r, "lng", 0)
                    && Ui.confirm(this, "Địa chỉ đã đổi. Tìm lại toạ độ theo địa chỉ mới (cần Mapbox token)?")) {
                d.addProperty("lat", 0);
                d.addProperty("lng", 0);
            }
        }
        act("ADMIN_RESTAURANT_SAVE", d, "Đã lưu nhà hàng");
    }

    private void deleteRestaurant() {
        JsonObject r = pick(restaurants);
        if (r == null || !Ui.confirm(this, "Xóa nhà hàng \"" + Json.str(r, "name") + "\" và toàn bộ menu?")) return;
        act("ADMIN_RESTAURANT_DELETE", Json.obj("id", id(r)), null);
    }

    private void editMenuItem(JsonObject m) {
        JsonObject r = restaurants.selected();
        if (r == null) {
            Ui.info(this, "Chọn nhà hàng trước");
            return;
        }
        JsonObject d = Ui.form(this, m == null ? "Thêm món · " + Json.str(r, "name") : "Sửa món", m,
                "name|Tên món", "description|Mô tả|area", "price|Giá (đ)|int", "is_available|Còn bán|bool");
        if (d == null) return;
        if (m != null) d.addProperty("id", id(m));
        d.addProperty("restaurant_id", id(r));
        net.run(this, "ADMIN_MENU_SAVE", d, res -> loadMenu());
    }

    private void deleteMenuItem() {
        JsonObject m = pick(menu);
        if (m == null || !Ui.confirm(this, "Xóa món \"" + Json.str(m, "name") + "\"?")) return;
        net.run(this, "ADMIN_MENU_DELETE", Json.obj("id", id(m)), res -> loadMenu());
    }

    // ---------- Giá / thanh toán / review ----------

    private void loadPricing() { list("pricing", pricing); }

    private void editPricing() {
        JsonObject p = pick(pricing);
        if (p == null) return;
        String service = Json.str(p, "service");
        JsonObject d = Ui.form(this, "Giá " + Ui.vi(service), p,
                "base_fare|Giá mở cửa (đ)|int", "per_km|Giá mỗi km (đ)|int", "per_min|Giá mỗi phút (đ)|int", "min_fare|Giá tối thiểu (đ)|int");
        if (d == null) return;
        d.addProperty("service", service);
        act("ADMIN_PRICING_SAVE", d, "Đã lưu bảng giá. Áp dụng cho các chuyến/đơn mới.");
    }

    private void loadPayments() { list("payments", payments); }

    private void paymentStatus(String status) {
        JsonObject p = pick(payments);
        if (p == null) return;
        act("ADMIN_PAYMENT_STATUS", Json.obj("id", id(p), "status", status), null);
    }

    private void loadReviews() { list("reviews", reviews); }

    private void toggleReview() {
        JsonObject r = pick(reviews);
        if (r == null) return;
        act("ADMIN_REVIEW_TOGGLE", Json.obj("id", id(r)), null);
    }

    // ---------- Biểu đồ ----------

    /** Cột chồng: chuyến xe (oải hương) + đồ ăn (đào) theo ngày. */
    private static class RevenueChart extends JPanel {
        private JsonArray data = new JsonArray();

        RevenueChart() {
            setOpaque(false);
            setPreferredSize(new Dimension(400, 220));
        }

        void setData(JsonArray d) {
            data = d == null ? new JsonArray() : d;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), left = 10, bottom = 40, top = 34;
            double max = 1;
            for (JsonElement e : data) {
                JsonObject o = e.getAsJsonObject();
                max = Math.max(max, Json.dbl(o, "RIDE", 0) + Json.dbl(o, "FOOD", 0));
            }
            legend(g, 10, 14, Theme.LAVENDER_DEEP, "Chuyến xe");
            legend(g, 110, 14, Theme.PEACH_DEEP, "Đồ ăn");
            int n = Math.max(1, data.size());
            int slot = (w - left * 2) / n;
            int barW = Math.max(12, Math.min(48, slot - 18));
            int plotH = h - bottom - top;
            g.setColor(Theme.LINE);
            g.drawLine(left, h - bottom, w - left, h - bottom);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < data.size(); i++) {
                JsonObject o = data.get(i).getAsJsonObject();
                double ride = Json.dbl(o, "RIDE", 0), food = Json.dbl(o, "FOOD", 0);
                int x = left + i * slot + (slot - barW) / 2;
                int hr = (int) Math.round(ride / max * plotH), hf = (int) Math.round(food / max * plotH);
                int y = h - bottom;
                g.setColor(Theme.LAVENDER_DEEP);
                g.fillRect(x, y - hr, barW, hr);
                g.setColor(Theme.PEACH_DEEP);
                g.fillRect(x, y - hr - hf, barW, hf);
                String day = Json.str(o, "day", "");
                String label = day.length() >= 10 ? day.substring(8, 10) + "/" + day.substring(5, 7) : day;
                g.setColor(Theme.MUTED);
                g.drawString(label, x + (barW - fm.stringWidth(label)) / 2, h - bottom + 16);
                double total = ride + food;
                if (total > 0) {
                    String v = total >= 1_000_000 ? String.format("%.1ftr", total / 1_000_000) : Math.round(total / 1000) + "k";
                    g.setColor(Theme.INK);
                    g.drawString(v, x + (barW - fm.stringWidth(v)) / 2, y - hr - hf - 4);
                }
            }
            g.dispose();
        }

        private static void legend(Graphics2D g, int x, int y, Color c, String text) {
            g.setColor(c);
            g.fillRect(x, y - 9, 12, 12);
            g.setColor(Theme.INK);
            g.drawString(text, x + 18, y + 1);
        }
    }
}
