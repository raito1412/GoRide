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
    /** Giao gộp tối đa ngần này đơn đồ ăn cùng lúc (khớp Sessions.MAX_FOOD_BATCH phía server). */
    private static final int MAX_BATCH = 3;
    private static final double SIM_METERS_PER_TICK = 35;

    private final MapPanel map = new MapPanel(Config.defaultLat(), Config.defaultLng());
    private final JPanel workPanel = new JPanel(new BorderLayout(12, 0));
    private JScrollPane workColumn;
    /** Bản đồ luôn bật khi đang có việc; ngoài ra tài xế tự bấm mở để xem mình đang ở đâu. */
    private boolean mapVisible, manualMap;
    private final JTabbedPane tabs = newTabs();
    private final GpsSender gps;

    // trạng thái làm việc
    private final ToggleSwitch onlineBtn = new ToggleSwitch("Đang Online", "Đang Offline", false);
    private final Ui.Pill approvalPill = new Ui.Pill();
    private final JLabel addressLabel = Ui.label("Đang xác định vị trí...", 13.5f, Font.BOLD);
    private final JLabel posLabel = Ui.muted("");
    private final JButton mapBtn = Ui.button("🗺 Xem trên bản đồ", Theme.SKY, null);
    /** Toạ độ của lần hỏi địa chỉ gần nhất, để bỏ qua phản hồi đến trễ. */
    private double[] geoAt;
    private long geoAtMs;
    private final JLabel offerHint = Ui.muted("");
    private final DefaultListModel<JsonObject> offerModel = new DefaultListModel<>();
    private final JList<JsonObject> offers = new JList<>(offerModel);
    private final JPanel offerBox = Ui.column();

    private final JPanel jobBox = Ui.column();
    private final JLabel jobTitle = Ui.label("", 15f, Font.BOLD);
    private final Ui.Pill jobPill = new Ui.Pill();
    private final JLabel jobInfo = new JLabel();
    private final JButton nextBtn;
    /** GPS tự chạy theo tuyến ngay khi nhận việc; nút này chỉ để tạm dừng khi cần. */
    private final JToggleButton autoDrive = new JToggleButton("⏸ Tạm dừng di chuyển", true);
    private JsonObject job;
    private String jobKind;
    /** Các đơn đồ ăn đang giữ, theo mã đơn. Với đồ ăn, "job" là đơn của điểm dừng cần tới ngay. */
    private final Map<Long, JsonObject> foodJobs = new java.util.LinkedHashMap<>();
    /** Lộ trình tối ưu server tính cho các đơn đang giữ; điểm dừng đầu tiên là chỗ cần tới ngay. */
    private JsonObject plan;
    private JsonObject focusStop;
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

        StatementView statement = new StatementView(net, "DRIVER_STATEMENT");
        tabs.addTab("🛵 Làm việc", buildWork());
        tabs.addTab("🕘 Lịch sử", buildHistory());
        tabs.addTab("💰 Thu nhập", buildEarnings());
        tabs.addTab("📄 Đối soát tháng", statement);
        tabs.addTab("👤 Tài khoản", profile);
        tabs.addChangeListener(e -> {
            switch (tabs.getSelectedIndex()) {
                case 0 -> drawMap(true);
                case 1 -> loadHistory();
                case 2 -> loadEarnings();
                case 3 -> statement.load();
                case 4 -> profile.reload();
                default -> { }
            }
        });
        setBody(tabs);

        registerPushes();
        renderDriverInfo();
        renderOnline(false);
        renderJob();
        renderMapBtn();
        updatePosLabel();
        if (!known) {
            new Thread(() -> {
                double[] p = MyLocation.approximate();
                SwingUtilities.invokeLater(() -> moveTo(p[0], p[1], true, true));
            }, "my-location").start();
        }
        // khôi phục việc dở dang (đăng nhập lại khi đang chạy)
        net.quiet("DRIVER_CURRENT", null, r -> {
            if (r.isJsonObject()) {
                JsonObject o = r.getAsJsonObject();
                if ("FOOD".equals(Json.str(o, "kind")) && o.has("jobs")) {
                    for (JsonElement e : o.getAsJsonArray("jobs")) foodJobs.put(id(e.getAsJsonObject()), e.getAsJsonObject());
                    refreshPlan();
                } else {
                    setJob(Json.str(o, "kind"), o.getAsJsonObject("job"));
                }
                setOnline(true);
            } else if ("APPROVED".equals(Json.str(net.me, "approval"))) {
                setOnline(true); // GPS bật sẵn, tài xế không phải bấm gì
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
        onlineBtn.onToggle(this::setOnline);
        top.add(onlineBtn, BorderLayout.WEST);
        top.add(approvalPill, BorderLayout.EAST);
        status.add(top, BorderLayout.NORTH);
        JPanel lines = new JPanel(new GridLayout(0, 1, 0, 3));
        lines.setOpaque(false);
        lines.add(addressLabel);
        lines.add(posLabel);
        status.add(lines);
        mapBtn.addActionListener(e -> toggleMap());
        JPanel where = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        where.setOpaque(false);
        where.add(mapBtn);
        where.add(Ui.button("📍 Lấy lại vị trí (theo IP)", Theme.SURFACE, () -> new Thread(() -> {
            double[] p = MyLocation.approximate();
            SwingUtilities.invokeLater(() -> moveTo(p[0], p[1], true, true));
        }).start()));
        status.add(where, BorderLayout.SOUTH);
        col.add(status);
        col.add(Ui.muted("<html>Đứng sai chỗ? Mở bản đồ rồi bấm vào vị trí của bạn để chỉnh lại.</html>"));

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
        autoDrive.setFocusPainted(false);
        autoDrive.addActionListener(e -> renderAutoDrive());
        jobBox.add(Ui.row(autoDrive));
        col.add(jobBox);

        // yêu cầu đang chờ
        offerBox.setOpaque(false);
        offerBox.setBorder(null);
        offerBox.add(Ui.section("Yêu cầu gần bạn"));
        offers.setCellRenderer(new OfferRenderer());
        offers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        offers.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && mapVisible) drawMap(offers.getSelectedValue() != null);
        });
        JScrollPane os = Ui.scroll(offers);
        os.setPreferredSize(new Dimension(300, 280));
        os.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        offerBox.add(os);
        offerBox.add(Ui.row(Ui.button("Nhận việc", Theme.MINT, this::acceptOffer),
                Ui.button("Xem bản đồ", Theme.SKY, this::previewOnMap),
                Ui.button("Bỏ qua", Theme.ROSE, this::rejectOffer),
                Ui.button("Làm mới", Theme.SURFACE, this::loadPending)));
        offerBox.add(offerHint);
        col.add(offerBox);

        workColumn = vscroll(col);
        workPanel.setOpaque(false);
        workPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        layoutWork();
        return workPanel;
    }

    /** Không có việc thì cột điều khiển chiếm trọn bề ngang; nhận việc xong mới chia đôi với bản đồ. */
    private void layoutWork() {
        boolean want = job != null || manualMap;
        if (want == mapVisible && workPanel.getComponentCount() > 0) return;
        mapVisible = want;
        workPanel.removeAll();
        if (want) {
            workColumn.setPreferredSize(new Dimension(470, 100));
            workPanel.add(workColumn, BorderLayout.WEST);
            workPanel.add(map, BorderLayout.CENTER);
        } else {
            workColumn.setPreferredSize(null);
            workPanel.add(workColumn, BorderLayout.CENTER);
        }
        workPanel.revalidate();
        workPanel.repaint();
    }

    /** Xem trước một yêu cầu trên bản đồ trước khi quyết định nhận. */
    private void previewOnMap() {
        if (offers.getSelectedValue() == null) {
            Ui.info(this, "Chọn một yêu cầu trong danh sách");
            return;
        }
        manualMap = true;
        layoutWork();
        renderMapBtn();
        drawMap(true);
    }

    /** Mở / đóng bản đồ để xem vị trí hiện tại. Đang có việc thì bản đồ luôn mở. */
    private void toggleMap() {
        if (job != null) {
            drawMap(true);
            return;
        }
        manualMap = !manualMap;
        layoutWork();
        renderMapBtn();
        if (manualMap) {
            drawMap(true);
            map.center(gps.lat(), gps.lng());
        }
    }

    private void renderMapBtn() {
        mapBtn.setText(job != null ? "🗺 Căn lại bản đồ" : manualMap ? "🗺 Ẩn bản đồ" : "🗺 Xem trên bản đồ");
    }

    /** Bật/tắt việc tự di chuyển theo tuyến và cập nhật chữ trên nút. */
    private void renderAutoDrive() {
        boolean on = autoDrive.isSelected();
        autoDrive.setText(on ? "⏸ Tạm dừng di chuyển" : "▶ Tiếp tục di chuyển");
        autoDrive.setBackground(on ? Theme.MINT : Theme.LINE);
        if (on && job != null && route != null) simTimer.start();
        else simTimer.stop();
        drawMap(false); // bật/tắt tự chạy -> cập nhật lại quyền bấm bản đồ
    }

    private void renderDriverInfo() {
        approvalPill.setStatus(Json.str(net.me, "approval"));
    }

    private void renderOnline(boolean on) {
        onlineBtn.setSelected(on);
        offerBox.setVisible(on && canTakeMore());
        renderOfferHint();
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
        if (!onlineBtn.isSelected() || !canTakeMore()) {
            renderOfferHint();
            return;
        }
        net.quiet("DRIVER_PENDING", null, r -> {
            offerModel.clear();
            for (JsonElement e : r.getAsJsonArray()) offerModel.addElement(e.getAsJsonObject());
            renderOfferHint();
        });
    }

    /** Danh sách trống thì nói rõ vì sao, thay vì để tài xế nhìn một ô rỗng. */
    private void renderOfferHint() {
        double radius = Config.getDouble("MATCH_RADIUS_KM", 5);
        if (!onlineBtn.isSelected()) {
            offerHint.setText("<html>Đang Offline nên không nhận được yêu cầu nào.</html>");
        } else if (!foodJobs.isEmpty()) {
            offerHint.setText("<html>Đang giao <b>" + foodJobs.size() + "/" + MAX_BATCH + "</b> đơn — nhận thêm được "
                    + (MAX_BATCH - foodJobs.size()) + " đơn đồ ăn, lộ trình sẽ tự tính lại cho ngắn nhất."
                    + (offerModel.isEmpty() ? "<br>Hiện chưa có đơn nào gần bạn." : "") + "</html>");
        } else if (offerModel.isEmpty()) {
            offerHint.setText("<html>Chưa có yêu cầu nào phù hợp.<br>"
                    + "Chỉ hiện chuyến/đơn loại <b>" + Ui.vi(Json.str(net.me, "vehicle_type")) + "</b> "
                    + "có điểm đón cách <b>vị trí hiện tại của bạn</b> không quá " + radius + " km.<br>"
                    + "Nếu bạn vừa đổi chỗ đứng, bấm <b>🗺 Xem trên bản đồ</b> rồi bấm đúng chỗ bạn đang đứng.</html>");
        } else {
            offerHint.setText("<html><b>" + offerModel.size() + "</b> yêu cầu đang chờ bạn nhận.</html>");
        }
    }

    /** Mô phỏng chạy theo tuyến: chỉ đổi vị trí tại chỗ, để UDP lo việc báo server. */
    private void moveTo(double lat, double lng, boolean center) {
        moveTo(lat, lng, center, false);
    }

    /**
     * Đổi vị trí xe. manual = tài xế tự đặt (bấm bản đồ / lấy theo IP) -> báo server ngay bằng TCP
     * thay vì chờ gói UDP, vì server dùng vị trí này để lọc yêu cầu theo bán kính.
     */
    private void moveTo(double lat, double lng, boolean center, boolean manual) {
        gps.setPosition(lat, lng);
        updatePosLabel();
        if (mapVisible && tabs.getSelectedIndex() == 0) {
            map.setMarker("me", lat, lng, Theme.MINT_DEEP, "Bạn");
            if (center) map.center(lat, lng);
        }
        if (job != null) renderJob();
        if (manual) net.quiet("DRIVER_SET_LOCATION", Json.obj("lat", lat, "lng", lng), r -> loadPending());
    }

    private void updatePosLabel() {
        posLabel.setText(String.format("<html>Toạ độ %.5f, %.5f</html>", gps.lat(), gps.lng()));
        // đang tự chạy theo tuyến thì hỏi địa chỉ thưa thôi, còn tài xế tự đổi chỗ thì cập nhật ngay
        refreshAddress(!simTimer.isRunning());
    }

    /** Hỏi server xem toạ độ hiện tại là địa chỉ nào để tài xế biết mình đang đứng ở đâu. */
    private void refreshAddress(boolean now) {
        double lat = gps.lat(), lng = gps.lng();
        long t = System.currentTimeMillis();
        boolean moved = geoAt == null || Geo.km(geoAt[0], geoAt[1], lat, lng) > 0.15;
        if (!now && (!moved || t - geoAtMs < 10_000)) return;
        geoAt = new double[]{lat, lng};
        geoAtMs = t;
        net.quiet("GEO_REVERSE", Json.obj("lat", lat, "lng", lng), r -> {
            if (geoAt == null || geoAt[0] != lat || geoAt[1] != lng) return; // phản hồi cũ, bỏ qua
            addressLabel.setText("<html>📍 " + Ui.esc(Json.str(r.getAsJsonObject(), "address", "Không rõ địa chỉ")) + "</html>");
        });
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

    /** Còn nhận thêm được: đang rảnh, hoặc đang giao đồ ăn mà chưa đủ MAX_BATCH đơn. */
    private boolean canTakeMore() {
        return job == null || (!isRide() && foodJobs.size() < MAX_BATCH);
    }

    private void addOffer(JsonObject o) {
        if (!onlineBtn.isSelected() || !canTakeMore()) return;
        if (job != null && "RIDE".equals(Json.str(o, "kind"))) return; // đang giao đồ ăn thì không chở khách
        for (int i = 0; i < offerModel.size(); i++) if (sameOffer(offerModel.get(i), Json.str(o, "kind"), id(o))) return;
        offerModel.add(0, o);
        renderOfferHint();
        Toolkit.getDefaultToolkit().beep();

        boolean ride = "RIDE".equals(Json.str(o, "kind"));
        String money = ride ? Ui.money(o, "price") : "phí giao " + Ui.money(o, "delivery_fee");
        String where = ride
                ? "Đón: " + Json.str(o, "pickup_address", "") + " → " + Json.str(o, "drop_address", "")
                : "Lấy tại " + Json.str(o, "restaurant_name", "") + " → " + Json.str(o, "delivery_address", "");
        Toast.show(this, ride ? "🛵" : "🍜",
                (ride ? "Chuyến mới #" : "Đơn mới #") + id(o) + " · " + money,
                "Cách bạn " + Json.dbl(o, "distance_to_start_km", 0) + " km · " + where,
                ride ? Theme.LAVENDER : Theme.PEACH, "Nhận việc", () -> accept(o));
    }

    private void removeOffer(String kind, long id) {
        for (int i = offerModel.size() - 1; i >= 0; i--) if (sameOffer(offerModel.get(i), kind, id)) offerModel.remove(i);
        renderOfferHint();
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
        accept(o);
    }

    private void accept(JsonObject o) {
        String kind = Json.str(o, "kind");
        if (!canTakeMore() || (job != null && "RIDE".equals(kind))) return;
        net.run(this, "RIDE".equals(kind) ? "DRIVER_RIDE_ACCEPT" : "DRIVER_FOOD_ACCEPT", Json.obj("id", id(o)), r -> {
            if ("RIDE".equals(kind)) {
                offerModel.clear();
                setJob(kind, r.getAsJsonObject());
            } else {
                removeOffer(kind, id(o));
                foodJobs.put(id(o), r.getAsJsonObject());
                if (foodJobs.size() > 1)
                    Toast.info(this, "🧭", "Đang giao gộp " + foodJobs.size() + " đơn",
                            "Lộ trình được tính lại để đi ngắn và nhanh nhất.", Theme.SKY);
                refreshPlan();
                loadPending();
            }
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
        layoutWork();
        renderMapBtn();
        renderJob();
        renderOnline(onlineBtn.isSelected());
        if (o != null) computeRoute();
        else drawMap(true);
    }

    /**
     * Hỏi server lộ trình tối ưu cho các đơn đang giữ, rồi lấy điểm dừng đầu tiên làm việc cần làm ngay.
     * Gọi lại mỗi khi nhận thêm đơn, xong một điểm dừng hoặc có đơn bị hủy.
     */
    private void refreshPlan() {
        if (foodJobs.isEmpty()) {
            plan = null;
            focusStop = null;
            simTimer.stop();
            setJob(null, null);
            return;
        }
        net.quiet("DRIVER_ROUTE_PLAN", null, r -> {
            plan = r.getAsJsonObject();
            JsonArray stops = plan.getAsJsonArray("stops");
            if (stops == null || stops.isEmpty()) {
                foodJobs.clear();
                refreshPlan();
                return;
            }
            focusStop = stops.get(0).getAsJsonObject();
            JsonObject o = foodJobs.get(Json.lng(focusStop, "order_id", -1));
            if (o == null) return;
            // tới lượt giao cho khách này mà đơn còn ở "đã lấy món" -> báo khách là tài xế đang trên đường tới
            if ("DROPOFF".equals(Json.str(focusStop, "kind")) && "PICKED_UP".equals(Json.str(o, "status"))) {
                net.quiet("DRIVER_FOOD_STATUS", Json.obj("id", id(o), "status", "DELIVERING"), u -> {
                    foodJobs.put(id(o), u.getAsJsonObject());
                    if (job != null && id(job) == id(o)) {
                        job = u.getAsJsonObject();
                        renderJob();
                    }
                });
            }
            job = o;
            jobKind = "FOOD";
            route = null;
            layoutWork();
            renderMapBtn();
            renderJob();
            renderOnline(onlineBtn.isSelected());
            computeRoute();
        });
    }

    private void jobCancelled(String kind, JsonObject d) {
        long cancelled = Json.lng(d, "id", -1);
        if ("FOOD".equals(kind)) {
            if (foodJobs.remove(cancelled) == null) return;
            Toast.info(this, "⚠️", "Đơn #" + cancelled + " đã bị hủy", Json.str(d, "reason", ""), Theme.ROSE);
            refreshPlan(); // bỏ điểm lấy/giao của đơn đó, đi tiếp các đơn còn lại
            loadPending();
            return;
        }
        if (job == null || !kind.equals(jobKind) || id(job) != cancelled) return;
        simTimer.stop();
        setJob(null, null);
        Toast.info(this, "⚠️", "Việc đã bị hủy", Json.str(d, "reason", ""), Theme.ROSE);
        loadPending();
    }

    private String status() { return job == null ? "" : Json.str(job, "status", ""); }

    private boolean isRide() { return "RIDE".equals(jobKind); }

    private String[] next() {
        if (job == null) return null;
        if (isRide()) return RIDE_NEXT.get(status());
        if (focusStop == null) return null;
        String st = status();
        long oid = id(job);
        if ("PICKUP".equals(Json.str(focusStop, "kind"))) {
            if (st.equals("ACCEPTED")) return new String[]{"AT_RESTAURANT", "Đã tới " + Json.str(job, "restaurant_name", "quán")};
            if (st.equals("AT_RESTAURANT")) return new String[]{"PICKED_UP", "Đã lấy món đơn #" + oid};
        } else {
            if (st.equals("PICKED_UP")) return new String[]{"DELIVERING", "Bắt đầu giao đơn #" + oid};
            if (st.equals("DELIVERING")) return new String[]{"DELIVERED", "Đã giao đơn #" + oid};
        }
        return null;
    }

    private void nextStatus() {
        String[] n = next();
        if (n == null) return;
        String kind = jobKind;
        if ("FOOD".equals(kind)) {
            long oid = id(job);
            net.run(this, "DRIVER_FOOD_STATUS", Json.obj("id", oid, "status", n[0]), r -> {
                JsonObject o = r.getAsJsonObject();
                if ("DELIVERED".equals(Json.str(o, "status"))) {
                    foodJobs.remove(oid);
                    Toast.info(this, "✅", "Đã giao đơn #" + oid + " · +" + Ui.money(Json.dbl(o, "delivery_fee", 0)
                                    * Config.getDouble("DRIVER_SHARE", 0.8)),
                            foodJobs.isEmpty() ? "Xong hết đơn trong lô." : "Còn " + foodJobs.size() + " đơn, đi tiếp điểm kế.",
                            Theme.MINT);
                } else {
                    foodJobs.put(oid, o);
                }
                simTimer.stop();
                refreshPlan(); // điểm dừng kế tiếp do server tính lại
                loadPending();
            });
            return;
        }
        net.run(this, "DRIVER_RIDE_STATUS", Json.obj("id", id(job), "status", n[0]), r -> {
            JsonObject o = r.getAsJsonObject();
            String st = Json.str(o, "status");
            if ("COMPLETED".equals(st) || "DELIVERED".equals(st)) {
                simTimer.stop();
                double amount = "RIDE".equals(kind) ? Json.dbl(o, "price", 0) : Json.dbl(o, "delivery_fee", 0);
                setJob(null, null);
                Toast.info(this, "✅", "Hoàn thành! +" + Ui.money(amount * Config.getDouble("DRIVER_SHARE", 0.8)),
                        "Khách trả " + ("RIDE".equals(kind) ? Ui.money(o, "price") : Ui.money(o, "total"))
                                + " (" + Ui.vi(Json.str(o, "payment_method")) + ")", Theme.MINT);
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
        if (focusStop != null) return new double[]{Json.dbl(focusStop, "lat", 0), Json.dbl(focusStop, "lng", 0)};
        return st.equals("ACCEPTED")
                ? new double[]{Json.dbl(job, "restaurant_lat", 0), Json.dbl(job, "restaurant_lng", 0)}
                : new double[]{Json.dbl(job, "delivery_lat", 0), Json.dbl(job, "delivery_lng", 0)};
    }

    private void computeRoute() {
        double[] t = target();
        if (t == null) return;
        String vehicle = "CAR".equals(Json.str(net.me, "vehicle_type")) ? "car" : "bike";
        net.run(this, "GEO_ROUTE", Json.obj("from_lat", gps.lat(), "from_lng", gps.lng(), "to_lat", t[0], "to_lng", t[1],
                "vehicle", vehicle), r -> {
            // điểm cần tới đã đổi trong lúc chờ (xong điểm dừng, đơn bị hủy...) thì bỏ kết quả cũ
            double[] now = target();
            if (now == null || now[0] != t[0] || now[1] != t[1]) return;
            route = r.getAsJsonObject().get("points");
            simIndex = 0;
            renderAutoDrive(); // có tuyến là GPS tự chạy, không cần tick gì
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
        jobTitle.setText(isRide() ? "🛵 Chuyến #" + id(job)
                : foodJobs.size() > 1 ? "🍜 Giao gộp " + foodJobs.size() + "/" + MAX_BATCH + " đơn"
                : "🍜 Đơn #" + id(job));
        jobPill.setStatus(st);
        double[] t = target();
        StringBuilder h = new StringBuilder("<html><div style='width:300px'>");
        if (!isRide() && focusStop != null) {
            boolean pick = "PICKUP".equals(Json.str(focusStop, "kind"));
            h.append("<div style='color:#0F4FD6'><b>").append(pick ? "📍 Điểm tới: LẤY MÓN tại " : "📍 Điểm tới: GIAO cho ")
                    .append(Ui.esc(Json.str(focusStop, "name", ""))).append("</b> (đơn #").append(id(job)).append(")</div>");
        }
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
            h.append("<span style='color:#13703F'>Cách điểm cần tới ").append(String.format("%.2f km", km)).append("</span>");
        }
        if (!isRide() && plan != null && plan.has("stops")) h.append(planHtml());
        h.append("</div></html>");
        jobInfo.setText(h.toString());
        String[] n = next();
        nextBtn.setVisible(n != null);
        if (n != null) nextBtn.setText(n[1]);
    }

    /** Danh sách điểm dừng theo lộ trình tối ưu + phần tiết kiệm so với đi lần lượt từng đơn. */
    private String planHtml() {
        JsonArray stops = plan.getAsJsonArray("stops");
        StringBuilder h = new StringBuilder("<br><br><b>Lộ trình tối ưu</b> · ")
                .append(Ui.km(Json.dbl(plan, "total_distance_m", 0))).append(" · ")
                .append(Ui.minutes(Json.dbl(plan, "total_duration_s", 0)));
        long saved = Json.lng(plan, "saved_distance_m", 0);
        if (Json.integer(plan, "orders", 0) > 1 && saved > 0)
            h.append("<br><span style='color:#13703F'>Ngắn hơn ").append(Ui.km(saved)).append(", nhanh hơn ")
                    .append(Ui.minutes(Json.dbl(plan, "saved_duration_s", 0)))
                    .append(" so với giao lần lượt từng đơn</span>");
        h.append("<table cellpadding='1' cellspacing='0'>");
        for (JsonElement e : stops) {
            JsonObject s = e.getAsJsonObject();
            boolean pick = "PICKUP".equals(Json.str(s, "kind"));
            boolean now = s == stops.get(0);
            h.append("<tr><td valign='top'>").append(now ? "<b>▶ " : "").append(Json.integer(s, "seq", 0)).append(".")
                    .append(now ? "</b>" : "").append("</td><td>").append(now ? "<b>" : "")
                    .append(pick ? "Lấy #" : "Giao #").append(Json.lng(s, "order_id", 0)).append(" · ")
                    .append(Ui.esc(Json.str(s, "name", ""))).append(now ? "</b>" : "")
                    .append(" <span style='color:#5A6472'>~").append(Ui.minutes(Json.dbl(s, "eta_s", 0)))
                    .append("</span></td></tr>");
        }
        return h.append("</table>").toString();
    }

    /** Di chuyển vị trí xe dọc theo tuyến đường để demo theo dõi realtime. */
    private void simulateStep() {
        if (job == null || route == null || !route.isJsonArray()) return;
        JsonArray pts = route.getAsJsonArray();
        if (simIndex >= pts.size()) {
            simTimer.stop(); // đã tới nơi, chờ tài xế bấm bước kế tiếp
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
        if (!mapVisible || tabs.getSelectedIndex() != 0) return;
        map.clearAll();
        // đang tự chạy theo tuyến thì khoá hẳn việc bấm bản đồ, không chỉ ẩn dòng gợi ý
        boolean driving = simTimer.isRunning();
        map.onPick(driving ? null : "Bấm bản đồ để đặt vị trí xe",
                driving ? null : (lat, lng) -> moveTo(lat, lng, false, true));
        map.setMarker("me", gps.lat(), gps.lng(), Theme.MINT_DEEP, "Bạn");
        if (job != null && !isRide() && plan != null && plan.has("stops")) {
            for (JsonElement e : plan.getAsJsonArray("stops")) {
                JsonObject s = e.getAsJsonObject();
                boolean pick = "PICKUP".equals(Json.str(s, "kind"));
                map.setMarker("stop" + Json.integer(s, "seq", 0), Json.dbl(s, "lat", 0), Json.dbl(s, "lng", 0),
                        pick ? Theme.PEACH_DEEP : Theme.ROSE_DEEP,
                        Json.integer(s, "seq", 0) + " · " + (pick ? "Lấy #" : "Giao #") + Json.lng(s, "order_id", 0));
            }
            map.setRoute(plan.get("points"), Theme.MINT_DEEP);
            if (fit) map.fit();
            return;
        }
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
                    ? "<html><b>🛵 Chuyến #" + id(o) + "</b> · <b style='color:#13703F'>" + Ui.money(o, "price") + "</b> · "
                    + Ui.km(Json.dbl(o, "distance_m", 0)) + "<br>Cách bạn " + Json.dbl(o, "distance_to_start_km", 0) + " km · "
                    + Ui.esc(Json.str(o, "user_name")) + "<br><span style='color:#5A6472'>Đón: " + Ui.esc(Json.str(o, "pickup_address"))
                    + "<br>Đến: " + Ui.esc(Json.str(o, "drop_address")) + "</span></html>"
                    : "<html><b>🍜 Đơn #" + id(o) + "</b> · phí giao <b style='color:#B8480A'>" + Ui.money(o, "delivery_fee") + "</b> · "
                    + Ui.km(Json.dbl(o, "distance_m", 0)) + "<br>Nhà hàng cách bạn " + Json.dbl(o, "distance_to_start_km", 0) + " km<br>"
                    + "<span style='color:#5A6472'>Lấy: " + Ui.esc(Json.str(o, "restaurant_name"))
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
