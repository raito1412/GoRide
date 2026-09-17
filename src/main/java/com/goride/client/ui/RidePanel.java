package com.goride.client.ui;

import com.goride.client.MyLocation;
import com.goride.client.Net;
import com.goride.common.Geo;
import com.goride.common.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** User: chọn điểm đón/đến, xem giá, đặt xe Bike/Car, theo dõi tài xế realtime, hủy, đánh giá. */
public class RidePanel extends JPanel {
    private static final Set<String> CANCELLABLE = Set.of("SEARCHING", "ACCEPTED", "ARRIVED");
    private static final Set<String> FINISHED = Set.of("COMPLETED", "CANCELLED");

    private final Net net;
    private final MapPanel map;
    private final BooleanSupplier visible;

    private final PlaceField pickup, drop;
    private final JRadioButton pickPickup = new JRadioButton("Điểm đón", true), pickDrop = new JRadioButton("Điểm đến");
    private final JLabel routeInfo = Ui.muted("Chọn điểm đón và điểm đến để xem giá");
    private final JToggleButton bike = choice(), car = choice();
    private final JComboBox<String> payment = Ui.codes("CASH", "WALLET");
    private final JButton bookBtn;
    private final JPanel formBox = Ui.column();

    private final JPanel trackBox = Ui.column();
    private final JLabel trackTitle = Ui.label("", 15f, Font.BOLD);
    private final Ui.Pill pill = new Ui.Pill();
    private final JLabel trackInfo = new JLabel();
    private final JButton cancelBtn, reviewBtn, newBtn;

    private JsonObject quote;
    private JsonObject ride;
    private JsonElement ridePoints;
    private double[] driverPos;

    public RidePanel(Net net, MapPanel map, BooleanSupplier visible) {
        super(new BorderLayout());
        this.net = net;
        this.map = map;
        this.visible = visible;
        pickup = new PlaceField(net, "Điểm đón", Theme.MINT_DEEP, this::biasPoint);
        drop = new PlaceField(net, "Bạn muốn đi đâu?", Theme.ROSE_DEEP, this::biasPoint);
        pickup.onChange(this::pointsChanged);
        drop.onChange(this::pointsChanged);

        // ----- form đặt xe -----
        formBox.setOpaque(false);
        formBox.setBorder(null);
        formBox.add(Ui.section("Đặt xe"));
        formBox.add(Ui.row(Ui.button("📍 Lấy vị trí hiện tại", Theme.MINT, this::useMyLocation)));
        formBox.add(pickup);
        formBox.add(Box.createVerticalStrut(6));
        formBox.add(drop);
        ButtonGroup g = new ButtonGroup();
        g.add(pickPickup);
        g.add(pickDrop);
        pickPickup.setOpaque(false);
        pickDrop.setOpaque(false);
        formBox.add(Ui.row(Ui.muted("Bấm bản đồ để chọn:"), pickPickup, pickDrop));
        formBox.add(Ui.row(Ui.button("Xem quãng đường & giá", Theme.SKY, this::requestQuote)));
        formBox.add(routeInfo);

        formBox.add(Ui.section("Chọn loại xe"));
        ButtonGroup vg = new ButtonGroup();
        vg.add(bike);
        vg.add(car);
        bike.setSelected(true);
        JPanel vehicles = new JPanel(new GridLayout(1, 2, 8, 0));
        vehicles.setOpaque(false);
        vehicles.add(bike);
        vehicles.add(car);
        vehicles.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        formBox.add(vehicles);
        formBox.add(Box.createVerticalStrut(8));
        formBox.add(Ui.row(Ui.muted("Thanh toán:"), payment));
        bookBtn = Ui.button("Đặt xe", Theme.LAVENDER, this::book);
        formBox.add(Ui.grow(bookBtn));
        renderChoices();

        // ----- theo dõi chuyến -----
        trackBox.setOpaque(false);
        trackBox.setBorder(null);
        trackBox.add(Ui.section("Chuyến của bạn"));
        Ui.Card card = new Ui.Card(new BorderLayout(0, 8), Theme.SURFACE);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(trackTitle, BorderLayout.WEST);
        head.add(pill, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(trackInfo);
        cancelBtn = Ui.button("Hủy chuyến", Theme.ROSE, this::cancel);
        reviewBtn = Ui.button("Đánh giá tài xế", Theme.BUTTER, this::review);
        newBtn = Ui.button("Đặt chuyến mới", Theme.LAVENDER, this::newRide);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(cancelBtn);
        actions.add(reviewBtn);
        actions.add(newBtn);
        card.add(actions, BorderLayout.SOUTH);
        trackBox.add(card);
        trackBox.add(Box.createVerticalStrut(8));
        trackBox.add(Ui.button("Căn bản đồ theo chuyến", Theme.SURFACE, map::fit));
        trackBox.setVisible(false);

        JPanel col = Ui.column();
        col.setOpaque(false);
        col.add(trackBox);
        col.add(formBox);
        add(BaseFrame.vscroll(col));

        net.on("RIDE_UPDATE", this::onRideUpdate);
        net.on("DRIVER_LOCATION", d -> {
            if (ride != null && "RIDE".equals(Json.str(d, "ref_type")) && Json.lng(d, "ref_id", -1) == id(ride)) {
                driverPos = new double[]{Json.dbl(d, "lat", 0), Json.dbl(d, "lng", 0)};
                renderTrack();
                drawMap(false);
            }
        });

        net.quiet("RIDE_CURRENT", null, r -> {
            if (r.isJsonObject() && r.getAsJsonObject().has("id")) {
                showRide(r.getAsJsonObject(), null);
                drawMap(true);
            }
        });
        useMyLocation();
    }

    // ---------- Điểm đón / đến ----------

    private double[] biasPoint() {
        return pickup.has() ? new double[]{pickup.lat(), pickup.lng()} : null;
    }

    private void useMyLocation() {
        new Thread(() -> {
            double[] p = MyLocation.approximate();
            SwingUtilities.invokeLater(() -> {
                if (ride != null && !FINISHED.contains(status())) return;
                pickup.setPoint(p[0], p[1]);
                map.center(p[0], p[1]);
            });
        }, "my-location").start();
    }

    private void pointsChanged() {
        quote = null;
        routeInfo.setText(pickup.has() && drop.has() ? "Bấm \"Xem quãng đường & giá\"" : "Chọn điểm đón và điểm đến để xem giá");
        renderChoices();
        drawMap(false);
        if (pickup.has() && drop.has()) requestQuote();
    }

    private void onMapPick(double lat, double lng) {
        if (pickPickup.isSelected()) {
            pickup.setPoint(lat, lng);
            pickDrop.setSelected(true);
        } else {
            drop.setPoint(lat, lng);
        }
    }

    private JsonObject points() {
        return Json.obj("pickup_lat", pickup.lat(), "pickup_lng", pickup.lng(), "drop_lat", drop.lat(), "drop_lng", drop.lng());
    }

    private void requestQuote() {
        if (!pickup.has() || !drop.has()) {
            Ui.info(this, "Hãy chọn điểm đón và điểm đến (tìm địa chỉ hoặc bấm lên bản đồ)");
            return;
        }
        double[] a = {pickup.lat(), pickup.lng(), drop.lat(), drop.lng()};
        routeInfo.setText("Đang tính...");
        net.run(this, "RIDE_QUOTE", points(), r -> {
            if (a[0] != pickup.lat() || a[1] != pickup.lng() || a[2] != drop.lat() || a[3] != drop.lng()) return; // đã đổi điểm
            quote = r.getAsJsonObject();
            String src = !"estimate".equals(Json.str(quote, "source")) ? "" : " (ước lượng — chưa có Mapbox token)";
            routeInfo.setText("Quãng đường " + Ui.km(Json.dbl(quote, "distance_m", 0))
                    + " · khoảng " + Ui.minutes(Json.dbl(quote, "duration_s", 0)) + src);
            renderChoices();
            drawMap(true);
        });
    }

    private void renderChoices() {
        JsonObject prices = quote == null ? null : quote.getAsJsonObject("prices");
        bike.setText(choiceText("🛵 Xe máy", prices == null ? "—" : Ui.money(prices, "BIKE")));
        car.setText(choiceText("🚗 Ô tô", prices == null ? "—" : Ui.money(prices, "CAR")));
        bookBtn.setEnabled(quote != null);
    }

    private static String choiceText(String name, String price) {
        return "<html><center>" + name + "<br><b>" + price + "</b></center></html>";
    }

    private static JToggleButton choice() {
        JToggleButton b = new JToggleButton();
        b.setBackground(Theme.SURFACE);
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setPreferredSize(new Dimension(120, 64));
        return b;
    }

    // ---------- Đặt / hủy ----------

    private void book() {
        if (quote == null) return;
        String vehicle = car.isSelected() ? "CAR" : "BIKE";
        JsonObject req = points();
        req.addProperty("pickup_address", pickup.address());
        req.addProperty("drop_address", drop.address());
        req.addProperty("vehicle_type", vehicle);
        req.addProperty("payment_method", (String) payment.getSelectedItem());
        net.run(this, "RIDE_BOOK", req, r -> {
            JsonObject o = r.getAsJsonObject();
            showRide(o, o.get("points"));
            drawMap(true);
        });
    }

    private void cancel() {
        if (ride == null || !Ui.confirm(this, "Hủy chuyến này?")) return;
        net.run(this, "RIDE_CANCEL", Json.obj("id", id(ride)), r -> showRide(r.getAsJsonObject(), ridePoints));
    }

    private void review() {
        if (ride == null) return;
        JsonObject target = ride;
        UserFrame.review(this, net, "RIDE", id(target), stars -> {
            target.addProperty("my_stars", stars);
            renderTrack();
        });
    }

    private void newRide() {
        ride = null;
        ridePoints = null;
        driverPos = null;
        trackBox.setVisible(false);
        formBox.setVisible(true);
        drop.clear();
        drawMap(true);
    }

    // ---------- Theo dõi ----------

    private void onRideUpdate(JsonObject d) {
        if (ride != null && id(ride) != id(d)) return;
        String before = status();
        showRide(d, ridePoints);
        String now = status();
        if (!now.equals(before)) {
            if (now.equals("ACCEPTED")) toast("Đã có tài xế " + Json.str(d, "driver_name", "") + " nhận chuyến!");
            else if (now.equals("ARRIVED")) toast("Tài xế đã tới điểm đón");
            else if (now.equals("COMPLETED")) toast("Chuyến đi hoàn thành. Cảm ơn bạn đã dùng GoRide!");
            else if (now.equals("CANCELLED")) toast("Chuyến đã hủy: " + Json.str(d, "cancel_reason", ""));
        }
    }

    private void toast(String msg) {
        if (visible.getAsBoolean()) Ui.info(this, msg);
    }

    private void showRide(JsonObject r, JsonElement points) {
        ride = r;
        if (Json.has(r, "driver_lat")) driverPos = new double[]{r.get("driver_lat").getAsDouble(), r.get("driver_lng").getAsDouble()};
        ridePoints = points;
        if (ridePoints == null) {
            net.quiet("GEO_ROUTE", Json.obj("from_lat", r.get("pickup_lat"), "from_lng", r.get("pickup_lng"),
                    "to_lat", r.get("drop_lat"), "to_lng", r.get("drop_lng"),
                    "vehicle", "CAR".equals(Json.str(r, "vehicle_type")) ? "car" : "bike"), res -> {
                if (ride == r || (ride != null && id(ride) == id(r))) {
                    ridePoints = res.getAsJsonObject().get("points");
                    drawMap(false);
                }
            });
        }
        formBox.setVisible(false);
        trackBox.setVisible(true);
        renderTrack();
        drawMap(false);
    }

    private void renderTrack() {
        if (ride == null) return;
        String st = status();
        trackTitle.setText("Chuyến #" + id(ride) + " · " + Ui.vi(Json.str(ride, "vehicle_type")));
        pill.setStatus(st);
        StringBuilder h = new StringBuilder("<html><div style='width:300px'>");
        h.append("<b>Đón:</b> ").append(Ui.esc(Json.str(ride, "pickup_address"))).append("<br>");
        h.append("<b>Đến:</b> ").append(Ui.esc(Json.str(ride, "drop_address"))).append("<br>");
        h.append(Ui.km(Json.dbl(ride, "distance_m", 0))).append(" · ").append(Ui.minutes(Json.dbl(ride, "duration_s", 0)))
                .append(" · <b>").append(Ui.money(ride, "price")).append("</b> (").append(Ui.vi(Json.str(ride, "payment_method"))).append(")<br><br>");
        if (Json.has(ride, "driver_id")) {
            h.append("<b>Tài xế:</b> ").append(Ui.esc(Json.str(ride, "driver_name"))).append(" · ").append(Json.str(ride, "driver_phone", "")).append("<br>");
            h.append("<b>Xe:</b> ").append(Ui.esc(Json.str(ride, "plate_number", ""))).append(" · ")
                    .append(Ui.esc(Json.str(ride, "vehicle_brand", ""))).append(" ").append(Ui.esc(Json.str(ride, "vehicle_color", "")))
                    .append(String.format(" · %.1f ★", Json.dbl(ride, "driver_rating", 0))).append("<br>");
            if (driverPos != null && (st.equals("ACCEPTED") || st.equals("PICKED_UP"))) {
                double km = st.equals("ACCEPTED")
                        ? Geo.km(driverPos[0], driverPos[1], Json.dbl(ride, "pickup_lat", 0), Json.dbl(ride, "pickup_lng", 0))
                        : Geo.km(driverPos[0], driverPos[1], Json.dbl(ride, "drop_lat", 0), Json.dbl(ride, "drop_lng", 0));
                String target = st.equals("ACCEPTED") ? "tới điểm đón" : "tới điểm đến";
                h.append("<span style='color:#5E66C4'>Tài xế cách ").append(target).append(String.format(" %.1f km", km))
                        .append(" (~").append(Math.max(1, Math.round(km * 1.3 / 25 * 60))).append(" phút)</span><br>");
            }
        } else if (st.equals("SEARCHING")) {
            h.append("<span style='color:#8B8AA6'>Đang tìm tài xế gần bạn...</span><br>");
        }
        if (st.equals("CANCELLED")) h.append("<span style='color:#B24B66'>").append(Ui.esc(Json.str(ride, "cancel_reason", ""))).append("</span>");
        if (st.equals("COMPLETED") && Json.has(ride, "my_stars")) h.append("Bạn đã đánh giá ").append(Ui.stars(Json.integer(ride, "my_stars", 0)));
        h.append("</div></html>");
        trackInfo.setText(h.toString());
        cancelBtn.setVisible(CANCELLABLE.contains(st));
        reviewBtn.setVisible(st.equals("COMPLETED") && !Json.has(ride, "my_stars"));
        newBtn.setVisible(FINISHED.contains(st));
    }

    private String status() { return ride == null ? "" : Json.str(ride, "status", ""); }

    private static long id(JsonObject o) { return Json.lng(o, "id", -1); }

    // ---------- Bản đồ ----------

    /** Vẽ lại bản đồ dùng chung (chỉ khi tab này đang mở). */
    public void drawMap(boolean fit) {
        if (!visible.getAsBoolean()) return;
        map.clearAll();
        if (ride != null) {
            map.onPick(null, null);
            map.setMarker("pickup", Json.dbl(ride, "pickup_lat", 0), Json.dbl(ride, "pickup_lng", 0), Theme.MINT_DEEP, "Điểm đón");
            map.setMarker("drop", Json.dbl(ride, "drop_lat", 0), Json.dbl(ride, "drop_lng", 0), Theme.ROSE_DEEP, "Điểm đến");
            map.setRoute(ridePoints, Theme.LAVENDER_DEEP);
            if (driverPos != null && !FINISHED.contains(status()))
                map.setMarker("driver", driverPos[0], driverPos[1], Theme.LAVENDER_DEEP, "Tài xế");
        } else {
            map.onPick("Bấm bản đồ để chọn " + (pickPickup.isSelected() ? "điểm đón" : "điểm đến"), this::onMapPickAndHint);
            if (pickup.has()) map.setMarker("pickup", pickup.lat(), pickup.lng(), Theme.MINT_DEEP, "Điểm đón");
            if (drop.has()) map.setMarker("drop", drop.lat(), drop.lng(), Theme.ROSE_DEEP, "Điểm đến");
            if (quote != null) map.setRoute(quote.get("points"), Theme.LAVENDER_DEEP);
        }
        if (fit) map.fit();
    }

    private void onMapPickAndHint(double lat, double lng) {
        onMapPick(lat, lng);
        drawMap(false);
    }
}
