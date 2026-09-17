package com.goride.client.ui;

import com.goride.client.GpsSender;
import com.goride.client.MyLocation;
import com.goride.client.Net;
import com.goride.common.Config;
import com.goride.common.Geo;
import com.goride.common.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

/** Giao diện Tài xế: Online/GPS, nhận chuyến & đơn, cập nhật trạng thái, lịch sử, thu nhập, đánh giá. */
public class DriverFrame extends BaseFrame {
    /** trạng thái hiện tại -> {trạng thái kế tiếp, chữ trên nút} */
    private static final Map<String, String[]> RIDE_NEXT = Map.of(
            "ACCEPTED", new String[]{"ARRIVED", "Đã tới điểm đón"},
            "ARRIVED", new String[]{"PICKED_UP", "Đã đón khách"},
            "PICKED_UP", new String[]{"COMPLETED", "Hoàn thành chuyến"});
    private static final Map<String, String[]> FOOD_NEXT = Map.of(
            "ACCEPTED", new String[]{"AT_RESTAURANT", "Đã tới nhà hàng"},
            "AT_RESTAURANT", new String[]{"PICKED_UP", "Đã lấy món"},
            "PICKED_UP", new String[]{"DELIVERING", "Bắt đầu giao"},
            "DELIVERING", new String[]{"DELIVERED", "Đã giao hàng"});
    private static final double SIM_METERS_PER_TICK = 35;

    private final MapPanel map = new MapPanel(Config.defaultLat(), Config.defaultLng());
    private final JTabbedPane tabs = newTabs();
    private final GpsSender gps;

    // trạng thái làm việc
    private final JToggleButton onlineBtn = new JToggleButton("Đang Offline");
    private final Ui.Pill approvalPill = new Ui.Pill();
    private final JLabel vehicleLabel = Ui.muted("");
    private final JLabel posLabel = Ui.muted("");
    private final DefaultListModel<JsonObject> offerModel = new DefaultListModel<>();
    private final JList<JsonObject> offers = new JList<>(offerModel);
    private final JPanel offerBox = Ui.column();

    private final JPanel jobBox = Ui.column();
    private final JLabel jobTitle = Ui.label("", 15f, Font.BOLD);
    private final Ui.Pill jobPill = new Ui.Pill();
    private final JLabel jobInfo = new JLabel();
    private final JButton nextBtn;
    private final JCheckBox simulate = new JCheckBox("Mô phỏng xe chạy theo tuyến (demo GPS)");
    private JsonObject job;
    private String jobKind;
    private JsonElement route;
    private int simIndex;
    private final Timer simTimer;

    // các tab khác
    private final DataTable histRides = new DataTable("id|#", "created_at|Thời gian|time", "user_name|Khách",
            "drop_address|Điểm đến", "price|Giá|money", "status|Trạng thái|status", "my_stars|Sao|stars");
    private final DataTable histOrders = new DataTable("id|#", "created_at|Thời gian|time", "restaurant_name|Nhà hàng",
            "user_name|Khách", "delivery_fee|Phí giao|money", "status|Trạng thái|status", "my_stars|Sao|stars");
    private final JLabel eToday = new JLabel("—"), eTotal = new JLabel("—"), eRides = new JLabel("—"),
            eFood = new JLabel("—"), eRating = new JLabel("—");
    private final JLabel eNote = Ui.muted(" ");
    private final DataTable reviews = new DataTable("created_at|Thời gian|time", "user_name|Khách",
            "ref_type|Loại|status", "ref_id|Mã", "stars|Sao|stars", "comment|Nhận xét");

    public DriverFrame(Net net) {
        super(net, "Tài xế", Theme.MINT, Theme.MINT_DEEP);
        double lat = Json.dbl(net.me, "last_lat", Double.NaN), lng = Json.dbl(net.me, "last_lng", Double.NaN);
        boolean known = !Double.isNaN(lat);
        gps = new GpsSender(Json.str(net.me, "udp_token", ""), Json.integer(net.me, "udp_port", Config.udpPort()),
                known ? lat : Config.defaultLat(), known ? lng : Config.defaultLng());
        simTimer = new Timer(1000, e -> simulateStep());

        nextBtn = Ui.button("", Theme.MINT, this::nextStatus);
        ProfilePanel profile = new ProfilePanel(net);
        profile.onUpdated(p -> {
            nameLabel.setText(Json.str(p, "full_name", ""));
            renderDriverInfo();
        });

        tabs.addTab("🛵 Làm việc", buildWork());
        tabs.addTab("🕘 Lịch sử", buildHistory());
        tabs.addTab("💰 Thu nhập", buildEarnings());
        tabs.addTab("👤 Tài khoản", profile);
        tabs.addChangeListener(e -> {
            switch (tabs.getSelectedIndex()) {
                case 0 -> drawMap(true);
                case 1 -> loadHistory();
                case 2 -> loadEarnings();
                case 3 -> profile.reload();
                default -> { }
            }
        });
        setBody(split(tabs, map, 470));

        registerPushes();
        renderDriverInfo();
        renderOnline(false);
        renderJob();
        updatePosLabel();
        if (!known) {
            new Thread(() -> {
                double[] p = MyLocation.approximate();
                SwingUtilities.invokeLater(() -> moveTo(p[0], p[1], true));
            }, "my-location").start();
        }
        // khôi phục việc dở dang (đăng nhập lại khi đang chạy)
        net.quiet("DRIVER_CURRENT", null, r -> {
            if (r.isJsonObject()) {
                JsonObject o = r.getAsJsonObject();
                setJob(Json.str(o, "kind"), o.getAsJsonObject("job"));
                setOnline(true);
            }
        });
        SwingUtilities.invokeLater(() -> drawMap(true));
    }

    @Override
    protected void beforeClose() {
        simTimer.stop();
        gps.close();
    }

    // ---------- Bố cục tab Làm việc ----------

    private JComponent buildWork() {
        JPanel col = Ui.column();
        col.setOpaque(false);

        col.add(Ui.section("Trạng thái"));
        Ui.Card status = new Ui.Card(new BorderLayout(0, 8), Theme.SURFACE);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        onlineBtn.setFont(onlineBtn.getFont().deriveFont(Font.BOLD, 14f));
        onlineBtn.setPreferredSize(new Dimension(170, 40));
        onlineBtn.addActionListener(e -> setOnline(onlineBtn.isSelected()));
        top.add(onlineBtn, BorderLayout.WEST);
        top.add(approvalPill, BorderLayout.EAST);
        status.add(top, BorderLayout.NORTH);
        JPanel lines = new JPanel(new GridLayout(0, 1, 0, 2));
        lines.setOpaque(false);
        lines.add(vehicleLabel);
        lines.add(posLabel);
        lines.add(Ui.muted("Khi Online, GPS gửi qua UDP mỗi 2 giây."));
        lines.add(Ui.muted("Máy tính không có GPS: bấm bản đồ để đặt vị trí xe."));
        status.add(lines);
        col.add(status);
        col.add(Ui.row(Ui.button("📍 Vị trí gần đúng (IP)", Theme.SURFACE, () -> new Thread(() -> {
            double[] p = MyLocation.approximate();
            SwingUtilities.invokeLater(() -> moveTo(p[0], p[1], true));
        }).start())));

        // việc đang làm
        jobBox.setOpaque(false);
        jobBox.setBorder(null);
        jobBox.add(Ui.section("Việc đang làm"));
        Ui.Card card = new Ui.Card(new BorderLayout(0, 8), Theme.SURFACE);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(jobTitle, BorderLayout.WEST);
        head.add(jobPill, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(jobInfo);
        JPanel act = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        act.setOpaque(false);
        act.add(nextBtn);
        act.add(Ui.button("Chỉ đường", Theme.SKY, this::computeRoute));
        card.add(act, BorderLayout.SOUTH);
        jobBox.add(card);
        simulate.setOpaque(false);
        simulate.addActionListener(e -> {
            if (simulate.isSelected()) simTimer.start();
            else simTimer.stop();
        });
        jobBox.add(simulate);
        col.add(jobBox);

        // yêu cầu đang chờ
        offerBox.setOpaque(false);
        offerBox.setBorder(null);
        offerBox.add(Ui.section("Yêu cầu gần bạn"));
        offers.setCellRenderer(new OfferRenderer());
        offers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        offers.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) drawMap(offers.getSelectedValue() != null);
        });
        JScrollPane os = Ui.scroll(offers);
        os.setPreferredSize(new Dimension(300, 280));
        os.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        offerBox.add(os);
        offerBox.add(Ui.row(Ui.button("Nhận việc", Theme.MINT, this::acceptOffer),
                Ui.button("Bỏ qua", Theme.ROSE, this::rejectOffer),
                Ui.button("Làm mới", Theme.SURFACE, this::loadPending)));
        offerBox.add(Ui.muted("Chỉ hiện yêu cầu cùng loại xe, trong bán kính " + Config.getDouble("MATCH_RADIUS_KM", 5) + " km."));
        col.add(offerBox);
        return vscroll(col);
    }

    private void renderDriverInfo() {
        approvalPill.setStatus(Json.str(net.me, "approval"));
        vehicleLabel.setText(Ui.vi(Json.str(net.me, "vehicle_type")) + " · " + Json.str(net.me, "plate_number", "")
                + " · " + Json.str(net.me, "vehicle_brand", "") + " " + Json.str(net.me, "vehicle_color", "")
                + String.format(" · %.1f ★", Json.dbl(net.me, "rating_avg", 0)));
    }

    private void renderOnline(boolean on) {
        onlineBtn.setSelected(on);
        onlineBtn.setText(on ? "● Đang Online" : "○ Đang Offline");
        onlineBtn.setBackground(on ? Theme.MINT : Theme.LINE);
        onlineBtn.setForeground(on ? Theme.MINT_DEEP : Theme.INK);
        offerBox.setVisible(on && job == null);
    }

    // ---------- Online / vị trí ----------

    private void setOnline(boolean on) {
        onlineBtn.setEnabled(false);
        call("DRIVER_ONLINE", Json.obj("online", on, "lat", gps.lat(), "lng", gps.lng()), r -> {
            onlineBtn.setEnabled(true);
            gps.setEnabled(on);
            renderOnline(on);
            offerModel.clear();
            if (on) loadPending();
            drawMap(false);
        }, () -> {
            onlineBtn.setEnabled(true);
            renderOnline(!on);
        });
    }

    private void loadPending() {
        if (!onlineBtn.isSelected() || job != null) return;
        net.quiet("DRIVER_PENDING", null, r -> {
            offerModel.clear();
            for (JsonElement e : r.getAsJsonArray()) offerModel.addElement(e.getAsJsonObject());
        });
    }

    /** Đổi vị trí xe (GPS gửi đi ở lượt kế tiếp). */
    private void moveTo(double lat, double lng, boolean center) {
        gps.setPosition(lat, lng);
        updatePosLabel();
        if (tabs.getSelectedIndex() == 0) {
            map.setMarker("me", lat, lng, Theme.MINT_DEEP, "Bạn");
            if (center) map.center(lat, lng);
        }
        if (job != null) renderJob();
    }

    private void updatePosLabel() {
        posLabel.setText(String.format("Vị trí xe: %.5f, %.5f", gps.lat(), gps.lng()));
    }

    // ---------- Nhận việc ----------

    private void registerPushes() {
        net.on("RIDE_REQUEST", this::addOffer);
        net.on("FOOD_REQUEST", this::addOffer);
        net.on("RIDE_TAKEN", d -> removeOffer("RIDE", Json.lng(d, "id", -1)));
        net.on("FOOD_TAKEN", d -> removeOffer("FOOD", Json.lng(d, "id", -1)));
        net.on("RIDE_CANCELLED", d -> jobCancelled("RIDE", d));
        net.on("FOOD_CANCELLED", d -> jobCancelled("FOOD", d));
        net.on("DRIVER_APPROVAL", d -> {
            String a = Json.str(d, "approval");
            net.me.addProperty("approval", a);
            renderDriverInfo();
            if (!"APPROVED".equals(a)) {
                gps.setEnabled(false);
                renderOnline(false);
                offerModel.clear();
                Ui.info(this, "Tài khoản của bạn chuyển sang: " + Ui.vi(a));
            } else {
                Ui.info(this, "Tài khoản đã được Admin duyệt. Bạn có thể bật Online để nhận việc.");
            }
        });
    }

    private void addOffer(JsonObject o) {
        if (!onlineBtn.isSelected() || job != null) return;
        for (int i = 0; i < offerModel.size(); i++) if (sameOffer(offerModel.get(i), Json.str(o, "kind"), id(o))) return;
        offerModel.add(0, o);
        Toolkit.getDefaultToolkit().beep();
    }

    private void removeOffer(String kind, long id) {
        for (int i = offerModel.size() - 1; i >= 0; i--) if (sameOffer(offerModel.get(i), kind, id)) offerModel.remove(i);
    }

    private static boolean sameOffer(JsonObject o, String kind, long id) {
        return kind.equals(Json.str(o, "kind")) && id(o) == id;
    }

    private void acceptOffer() {
        JsonObject o = offers.getSelectedValue();
        if (o == null) {
            Ui.info(this, "Chọn một yêu cầu trong danh sách");
            return;
        }
        String kind = Json.str(o, "kind");
        net.run(this, "RIDE".equals(kind) ? "DRIVER_RIDE_ACCEPT" : "DRIVER_FOOD_ACCEPT", Json.obj("id", id(o)), r -> {
            offerModel.clear();
            setJob(kind, r.getAsJsonObject());
        });
        // nếu người khác đã nhận, server báo lỗi và gửi *_TAKEN để xóa khỏi danh sách
    }

    private void rejectOffer() {
        JsonObject o = offers.getSelectedValue();
        if (o == null) return;
        String kind = Json.str(o, "kind");
        net.run(this, "RIDE".equals(kind) ? "DRIVER_RIDE_REJECT" : "DRIVER_FOOD_REJECT", Json.obj("id", id(o)),
                r -> removeOffer(kind, id(o)));
    }

    // ---------- Việc đang làm ----------

    private void setJob(String kind, JsonObject o) {
        job = o;
        jobKind = kind;
        route = null;
        renderJob();
        renderOnline(onlineBtn.isSelected());
        if (o != null) computeRoute();
        else drawMap(true);
    }

    private void jobCancelled(String kind, JsonObject d) {
        if (job == null || !kind.equals(jobKind) || id(job) != Json.lng(d, "id", -1)) return;
        simulate.setSelected(false);
        simTimer.stop();
        setJob(null, null);
        Ui.info(this, Json.str(d, "reason", "Việc đã bị hủy"));
        loadPending();
    }

    private String status() { return job == null ? "" : Json.str(job, "status", ""); }

    private boolean isRide() { return "RIDE".equals(jobKind); }

    private String[] next() {
        return job == null ? null : (isRide() ? RIDE_NEXT : FOOD_NEXT).get(status());
    }

    private void nextStatus() {
        String[] n = next();
        if (n == null) return;
        String kind = jobKind;
        net.run(this, isRide() ? "DRIVER_RIDE_STATUS" : "DRIVER_FOOD_STATUS", Json.obj("id", id(job), "status", n[0]), r -> {
            JsonObject o = r.getAsJsonObject();
            String st = Json.str(o, "status");
            if ("COMPLETED".equals(st) || "DELIVERED".equals(st)) {
                simulate.setSelected(false);
                simTimer.stop();
                double amount = "RIDE".equals(kind) ? Json.dbl(o, "price", 0) : Json.dbl(o, "delivery_fee", 0);
                setJob(null, null);
                Ui.info(this, "Hoàn thành! Thu nhập dự kiến: " + Ui.money(amount * Config.getDouble("DRIVER_SHARE", 0.8))
                        + "\nKhách " + ("RIDE".equals(kind) ? "trả " + Ui.money(o, "price") : "trả " + Ui.money(o, "total"))
                        + " (" + Ui.vi(Json.str(o, "payment_method")) + ")");
                loadPending();
            } else {
                job = o;
                renderJob();
                computeRoute();
            }
        });
    }

    /** Điểm cần tới ở trạng thái hiện tại: {lat, lng}. */
    private double[] target() {
        if (job == null) return null;
        String st = status();
        if (isRide()) {
            return st.equals("ACCEPTED")
                    ? new double[]{Json.dbl(job, "pickup_lat", 0), Json.dbl(job, "pickup_lng", 0)}
                    : new double[]{Json.dbl(job, "drop_lat", 0), Json.dbl(job, "drop_lng", 0)};
        }
        return st.equals("ACCEPTED")
                ? new double[]{Json.dbl(job, "restaurant_lat", 0), Json.dbl(job, "restaurant_lng", 0)}
                : new double[]{Json.dbl(job, "delivery_lat", 0), Json.dbl(job, "delivery_lng", 0)};
    }

    private void computeRoute() {
        double[] t = target();
        if (t == null) return;
        JsonObject o = job;
        String vehicle = "CAR".equals(Json.str(net.me, "vehicle_type")) ? "car" : "bike";
        net.run(this, "GEO_ROUTE", Json.obj("from_lat", gps.lat(), "from_lng", gps.lng(), "to_lat", t[0], "to_lng", t[1],
                "vehicle", vehicle), r -> {
            if (job != o) return;
            route = r.getAsJsonObject().get("points");
            simIndex = 0;
            JsonObject info = r.getAsJsonObject();
            jobTitle.setToolTipText("Tuyến: " + Ui.km(Json.dbl(info, "distance_m", 0)) + " · " + Ui.minutes(Json.dbl(info, "duration_s", 0)));
            renderJob();
            drawMap(true);
        });
    }

    private void renderJob() {
        jobBox.setVisible(job != null);
        if (job == null) return;
        String st = status();
        jobTitle.setText((isRide() ? "🛵 Chuyến #" : "🍜 Đơn #") + id(job));
        jobPill.setStatus(st);
        double[] t = target();
        StringBuilder h = new StringBuilder("<html><div style='width:300px'>");
        h.append("<b>Khách:</b> ").append(Ui.esc(Json.str(job, "user_name"))).append(" · ").append(Json.str(job, "user_phone", "")).append("<br>");
        if (isRide()) {
            h.append("<b>Đón:</b> ").append(Ui.esc(Json.str(job, "pickup_address"))).append("<br>");
            h.append("<b>Đến:</b> ").append(Ui.esc(Json.str(job, "drop_address"))).append("<br>");
            h.append(Ui.km(Json.dbl(job, "distance_m", 0))).append(" · <b>").append(Ui.money(job, "price"))
                    .append("</b> (").append(Ui.vi(Json.str(job, "payment_method"))).append(")<br>");
        } else {
            h.append("<b>Nhà hàng:</b> ").append(Ui.esc(Json.str(job, "restaurant_name"))).append(" · ")
                    .append(Json.str(job, "restaurant_phone", "")).append("<br>")
                    .append(Ui.esc(Json.str(job, "restaurant_address"))).append("<br>");
            h.append("<b>Giao tới:</b> ").append(Ui.esc(Json.str(job, "delivery_address"))).append("<br>");
            if (job.has("items") && job.get("items").isJsonArray()) {
                for (JsonElement e : job.getAsJsonArray("items")) {
                    JsonObject it = e.getAsJsonObject();
                    h.append("• ").append(Json.integer(it, "quantity", 1)).append(" × ").append(Ui.esc(Json.str(it, "item_name"))).append("<br>");
                }
            }
            if (Json.has(job, "note")) h.append("<i>Ghi chú: ").append(Ui.esc(Json.str(job, "note"))).append("</i><br>");
            h.append("Thu khách <b>").append(Ui.money(job, "total")).append("</b> (").append(Ui.vi(Json.str(job, "payment_method")))
                    .append(") · Phí giao ").append(Ui.money(job, "delivery_fee")).append("<br>");
        }
        if (t != null) {
            double km = Geo.km(gps.lat(), gps.lng(), t[0], t[1]);
            h.append("<span style='color:#35946F'>Cách điểm cần tới ").append(String.format("%.2f km", km)).append("</span>");
        }
        h.append("</div></html>");
        jobInfo.setText(h.toString());
        String[] n = next();
        nextBtn.setVisible(n != null);
        if (n != null) nextBtn.setText(n[1]);
    }

    /** Di chuyển vị trí xe dọc theo tuyến đường để demo theo dõi realtime. */
    private void simulateStep() {
        if (job == null || route == null || !route.isJsonArray()) return;
        JsonArray pts = route.getAsJsonArray();
        if (simIndex >= pts.size()) {
            simulate.setSelected(false);
            simTimer.stop();
            return;
        }
        double lat = gps.lat(), lng = gps.lng();
        double budget = SIM_METERS_PER_TICK;
        while (simIndex < pts.size() && budget > 0) {
            JsonArray p = pts.get(simIndex).getAsJsonArray();
            double tLat = p.get(0).getAsDouble(), tLng = p.get(1).getAsDouble();
            double d = Geo.km(lat, lng, tLat, tLng) * 1000;
            if (d <= budget) {
                lat = tLat;
                lng = tLng;
                budget -= d;
                simIndex++;
            } else {
                double f = budget / d;
                lat += (tLat - lat) * f;
                lng += (tLng - lng) * f;
                budget = 0;
            }
        }
        moveTo(lat, lng, false);
    }

    // ---------- Bản đồ ----------

    private void drawMap(boolean fit) {
        if (tabs.getSelectedIndex() != 0) return;
        map.clearAll();
        map.onPick(simulate.isSelected() ? null : "Bấm bản đồ để đặt vị trí xe", (lat, lng) -> moveTo(lat, lng, false));
        map.setMarker("me", gps.lat(), gps.lng(), Theme.MINT_DEEP, "Bạn");
        JsonObject o = job;
        String kind = jobKind;
        if (o == null) {
            o = offers.getSelectedValue();
            kind = o == null ? null : Json.str(o, "kind");
        }
        if (o != null) {
            if ("RIDE".equals(kind)) {
                map.setMarker("a", Json.dbl(o, "pickup_lat", 0), Json.dbl(o, "pickup_lng", 0), Theme.LAVENDER_DEEP, "Khách đón");
                map.setMarker("b", Json.dbl(o, "drop_lat", 0), Json.dbl(o, "drop_lng", 0), Theme.ROSE_DEEP, "Điểm đến");
            } else {
                map.setMarker("a", Json.dbl(o, "restaurant_lat", 0), Json.dbl(o, "restaurant_lng", 0), Theme.PEACH_DEEP, Json.str(o, "restaurant_name"));
                map.setMarker("b", Json.dbl(o, "delivery_lat", 0), Json.dbl(o, "delivery_lng", 0), Theme.ROSE_DEEP, "Khách nhận");
            }
        }
        if (job != null) map.setRoute(route, Theme.MINT_DEEP);
        if (fit) map.fit();
    }

    // ---------- Lịch sử / thu nhập ----------

    private JPanel buildHistory() {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        p.add(titled("Chuyến xe đã nhận", histRides));
        p.add(titled("Đơn đồ ăn đã giao", histOrders));
        return p;
    }

    private void loadHistory() {
        net.quiet("DRIVER_HISTORY", null, r -> {
            JsonObject o = r.getAsJsonObject();
            histRides.setRows(o.get("rides"));
            histOrders.setRows(o.get("orders"));
        });
    }

    private JPanel buildEarnings() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel grid = new JPanel(new GridLayout(0, 2, 10, 10));
        grid.add(Ui.stat("Thu nhập hôm nay", eToday, Theme.MINT));
        grid.add(Ui.stat("Tổng thu nhập", eTotal, Theme.LAVENDER));
        grid.add(Ui.stat("Chuyến xe hoàn thành", eRides, Theme.SKY));
        grid.add(Ui.stat("Đơn đồ ăn đã giao", eFood, Theme.PEACH));
        grid.add(Ui.stat("Đánh giá trung bình", eRating, Theme.BUTTER));
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(grid);
        top.add(eNote, BorderLayout.SOUTH);
        p.add(top, BorderLayout.NORTH);
        p.add(titled("Nhận xét từ khách", reviews));
        return p;
    }

    private void loadEarnings() {
        net.quiet("DRIVER_EARNINGS", null, r -> {
            JsonObject o = r.getAsJsonObject();
            eToday.setText(Ui.money(o, "today_income"));
            eTotal.setText(Ui.money(o, "total_income"));
            eRides.setText(Json.integer(o, "ride_count", 0) + " · " + Ui.money(o, "ride_income"));
            eFood.setText(Json.integer(o, "food_count", 0) + " · " + Ui.money(o, "food_income"));
            eNote.setText("Tài xế nhận " + Math.round(Json.dbl(o, "share", 0.8) * 100)
                    + "% giá chuyến / phí giao; phần còn lại là phí nền tảng.");
        });
        net.quiet("DRIVER_REVIEWS", null, r -> {
            JsonObject o = r.getAsJsonObject();
            eRating.setText(String.format("%.1f ★ (%d)", Json.dbl(o, "rating_avg", 0), Json.integer(o, "rating_count", 0)));
            reviews.setRows(o.get("reviews"));
        });
    }

    private static JPanel titled(String title, DataTable table) {
        JPanel b = new JPanel(new BorderLayout(0, 4));
        b.add(Ui.label(title, 13f, Font.BOLD), BorderLayout.NORTH);
        b.add(table.scroll());
        return b;
    }

    // ---------- Tiện ích ----------

    private static long id(JsonObject o) { return o == null ? -1 : Json.lng(o, "id", -1); }

    /** Gọi nền có cả nhánh lỗi (hiện hộp thoại rồi chạy onFail). */
    private void call(String type, JsonObject data, Consumer<JsonElement> onOk, Runnable onFail) {
        new Thread(() -> {
            try {
                JsonElement r = net.call(type, data);
                SwingUtilities.invokeLater(() -> onOk.accept(r));
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, e.getMessage(), "GoRide", JOptionPane.WARNING_MESSAGE);
                    onFail.run();
                });
            }
        }, "driver-call").start();
    }

    private static class OfferRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JsonObject o = (JsonObject) value;
            boolean ride = "RIDE".equals(Json.str(o, "kind"));
            String html = ride
                    ? "<html><b>🛵 Chuyến #" + id(o) + "</b> · <b style='color:#35946F'>" + Ui.money(o, "price") + "</b> · "
                    + Ui.km(Json.dbl(o, "distance_m", 0)) + "<br>Cách bạn " + Json.dbl(o, "distance_to_start_km", 0) + " km · "
                    + Ui.esc(Json.str(o, "user_name")) + "<br><span style='color:#8B8AA6'>Đón: " + Ui.esc(Json.str(o, "pickup_address"))
                    + "<br>Đến: " + Ui.esc(Json.str(o, "drop_address")) + "</span></html>"
                    : "<html><b>🍜 Đơn #" + id(o) + "</b> · phí giao <b style='color:#C46A36'>" + Ui.money(o, "delivery_fee") + "</b> · "
                    + Ui.km(Json.dbl(o, "distance_m", 0)) + "<br>Nhà hàng cách bạn " + Json.dbl(o, "distance_to_start_km", 0) + " km<br>"
                    + "<span style='color:#8B8AA6'>Lấy: " + Ui.esc(Json.str(o, "restaurant_name"))
                    + "<br>Giao: " + Ui.esc(Json.str(o, "delivery_address")) + "</span></html>";
            JLabel l = (JLabel) super.getListCellRendererComponent(list, html, index, sel, false);
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 1, 0, ride ? Theme.LAVENDER : Theme.PEACH),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            if (sel) {
                l.setBackground(Theme.MINT);
                l.setForeground(Theme.INK);
            }
            return l;
        }
    }
}
