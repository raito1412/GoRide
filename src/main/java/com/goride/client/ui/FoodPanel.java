package com.goride.client.ui;

import com.goride.client.MyLocation;
import com.goride.client.Net;
import com.goride.common.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** User: nhà hàng -> menu -> giỏ hàng -> đặt đồ ăn -> theo dõi đơn + tài xế giao realtime. */
public class FoodPanel extends JPanel {
    private static final Set<String> CANCELLABLE = Set.of("PLACED", "CONFIRMED", "ACCEPTED");
    private static final Set<String> FINISHED = Set.of("DELIVERED", "CANCELLED");

    private final Net net;
    private final MapPanel map;
    private final BooleanSupplier visible;

    private final DefaultListModel<JsonObject> restaurantModel = new DefaultListModel<>();
    private final JList<JsonObject> restaurants = new JList<>(restaurantModel);
    private final DataTable menu = new DataTable("name|Món", "price|Giá|money", "description|Mô tả");
    private final JSpinner qty = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
    private final DataTable cartTable = new DataTable("item_name|Món", "quantity|SL", "subtotal|Thành tiền|money");
    private final Map<Long, JsonObject> cart = new LinkedHashMap<>();
    private JsonObject cartRestaurant;

    private final PlaceField address;
    private final JTextField note = new JTextField();
    private final JComboBox<String> payment = Ui.codes("CASH", "WALLET");
    private final JLabel summary = Ui.muted("Giỏ hàng trống");
    private final JButton orderBtn;
    private JsonObject quote;

    private final JPanel shopBox = Ui.column();
    private final JPanel trackBox = Ui.column();
    private final JLabel trackTitle = Ui.label("", 15f, Font.BOLD);
    private final Ui.Pill pill = new Ui.Pill();
    private final JLabel trackInfo = new JLabel();
    private final JButton cancelBtn, reviewBtn, newBtn;
    private JsonObject order;
    private JsonElement orderPoints;
    private double[] driverPos;

    public FoodPanel(Net net, MapPanel map, BooleanSupplier visible) {
        super(new BorderLayout());
        this.net = net;
        this.map = map;
        this.visible = visible;
        address = new PlaceField(net, "Địa chỉ giao hàng", Theme.ROSE_DEEP, () -> null);
        address.onChange(() -> {
            quote = null;
            renderSummary();
            drawMap(false);
        });

        // ----- chọn món -----
        shopBox.setOpaque(false);
        shopBox.setBorder(null);
        shopBox.add(Ui.section("Nhà hàng"));
        restaurants.setCellRenderer(new RestaurantRenderer());
        restaurants.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        restaurants.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) restaurantSelected();
        });
        JScrollPane rs = Ui.scroll(restaurants);
        rs.setPreferredSize(new Dimension(300, 170));
        rs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        shopBox.add(rs);

        shopBox.add(Ui.section("Thực đơn"));
        menu.widths(150, 80, 160);
        JScrollPane ms = menu.scroll();
        ms.setPreferredSize(new Dimension(300, 170));
        ms.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        shopBox.add(ms);
        menu.onDoubleClick(m -> addToCart());
        shopBox.add(Ui.row(Ui.muted("Số lượng"), qty, Ui.button("Thêm vào giỏ", Theme.PEACH, this::addToCart)));

        shopBox.add(Ui.section("Giỏ hàng"));
        JScrollPane cs = cartTable.scroll();
        cs.setPreferredSize(new Dimension(300, 120));
        cs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        shopBox.add(cs);
        shopBox.add(Ui.row(Ui.button("Bớt 1", Theme.SURFACE, () -> changeQty(-1)),
                Ui.button("Thêm 1", Theme.SURFACE, () -> changeQty(1)),
                Ui.button("Xóa giỏ", Theme.ROSE, this::clearCart)));

        shopBox.add(Ui.section("Giao tới"));
        shopBox.add(Ui.row(Ui.button("📍 Vị trí hiện tại", Theme.MINT, this::useMyLocation), Ui.muted("hoặc bấm lên bản đồ")));
        shopBox.add(address);
        note.putClientProperty("JTextField.placeholderText", "Ghi chú cho nhà hàng (ít cay, không hành...)");
        shopBox.add(Box.createVerticalStrut(6));
        shopBox.add(Ui.grow(note));
        shopBox.add(Ui.row(Ui.muted("Thanh toán:"), payment, Ui.button("Tính phí giao", Theme.SKY, this::requestQuote)));
        shopBox.add(summary);
        orderBtn = Ui.button("Đặt món", Theme.PEACH, this::placeOrder);
        shopBox.add(Box.createVerticalStrut(6));
        shopBox.add(Ui.grow(orderBtn));

        // ----- theo dõi đơn -----
        trackBox.setOpaque(false);
        trackBox.setBorder(null);
        trackBox.add(Ui.section("Đơn đồ ăn của bạn"));
        Ui.Card card = new Ui.Card(new BorderLayout(0, 8), Theme.SURFACE);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(trackTitle, BorderLayout.WEST);
        head.add(pill, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(trackInfo);
        cancelBtn = Ui.button("Hủy đơn", Theme.ROSE, this::cancel);
        reviewBtn = Ui.button("Đánh giá tài xế", Theme.BUTTER, this::review);
        newBtn = Ui.button("Đặt đơn mới", Theme.PEACH, this::newOrder);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(cancelBtn);
        actions.add(reviewBtn);
        actions.add(newBtn);
        card.add(actions, BorderLayout.SOUTH);
        trackBox.add(card);
        trackBox.add(Box.createVerticalStrut(8));
        trackBox.add(Ui.button("Căn bản đồ theo đơn", Theme.SURFACE, map::fit));
        trackBox.setVisible(false);

        JPanel col = Ui.column();
        col.setOpaque(false);
        col.add(trackBox);
        col.add(shopBox);
        add(BaseFrame.vscroll(col));
        renderSummary();

        net.on("FOOD_UPDATE", this::onOrderUpdate);
        net.on("DRIVER_LOCATION", d -> {
            if (order != null && "FOOD".equals(Json.str(d, "ref_type")) && Json.lng(d, "ref_id", -1) == id(order)) {
                driverPos = new double[]{Json.dbl(d, "lat", 0), Json.dbl(d, "lng", 0)};
                drawMap(false);
            }
        });
        loadRestaurants();
        net.quiet("FOOD_CURRENT", null, r -> {
            if (r.isJsonObject() && r.getAsJsonObject().has("id")) showOrder(r.getAsJsonObject(), null);
        });
        useMyLocation();
    }

    // ---------- Nhà hàng & giỏ ----------

    public void loadRestaurants() {
        net.quiet("RESTAURANT_LIST", null, r -> {
            JsonObject sel = restaurants.getSelectedValue();
            restaurantModel.clear();
            for (JsonElement e : r.getAsJsonArray()) restaurantModel.addElement(e.getAsJsonObject());
            if (sel != null) {
                for (int i = 0; i < restaurantModel.size(); i++)
                    if (id(restaurantModel.get(i)) == id(sel)) restaurants.setSelectedIndex(i);
            }
        });
    }

    private void restaurantSelected() {
        JsonObject r = restaurants.getSelectedValue();
        if (r == null) return;
        net.run(this, "MENU_LIST", Json.obj("restaurant_id", id(r)), menu::setRows);
        drawMap(false);
        if (visible.getAsBoolean() && order == null) map.center(Json.dbl(r, "lat", 0), Json.dbl(r, "lng", 0));
    }

    private void addToCart() {
        JsonObject r = restaurants.getSelectedValue();
        JsonObject m = menu.selected();
        if (r == null || m == null) {
            Ui.info(this, "Chọn nhà hàng và món trước");
            return;
        }
        if (cartRestaurant != null && id(cartRestaurant) != id(r)) {
            if (!Ui.confirm(this, "Giỏ đang có món của \"" + Json.str(cartRestaurant, "name") + "\".\nXóa giỏ để đặt ở nhà hàng này?")) return;
            cart.clear();
        }
        cartRestaurant = r;
        int n = (Integer) qty.getValue();
        JsonObject line = cart.computeIfAbsent(id(m), k -> Json.obj("menu_item_id", k, "item_name", Json.str(m, "name"),
                "unit_price", m.get("price").getAsDouble(), "quantity", 0));
        line.addProperty("quantity", Math.min(50, line.get("quantity").getAsInt() + n));
        qty.setValue(1);
        cartChanged();
    }

    private void changeQty(int delta) {
        JsonObject sel = cartTable.selected();
        if (sel == null) {
            Ui.info(this, "Chọn một món trong giỏ");
            return;
        }
        JsonObject line = cart.get(Json.lng(sel, "menu_item_id", -1));
        int q = line.get("quantity").getAsInt() + delta;
        if (q <= 0) cart.remove(Json.lng(sel, "menu_item_id", -1));
        else line.addProperty("quantity", Math.min(50, q));
        cartChanged();
    }

    private void clearCart() {
        cart.clear();
        cartChanged();
    }

    private void cartChanged() {
        if (cart.isEmpty()) cartRestaurant = null;
        JsonArray rows = new JsonArray();
        for (JsonObject l : cart.values()) {
            JsonObject row = l.deepCopy();
            row.addProperty("id", l.get("menu_item_id").getAsLong());
            row.addProperty("subtotal", l.get("unit_price").getAsDouble() * l.get("quantity").getAsInt());
            rows.add(row);
        }
        cartTable.setRows(rows);
        quote = null;
        renderSummary();
        drawMap(false);
    }

    private double cartTotal() {
        double t = 0;
        for (JsonObject l : cart.values()) t += l.get("unit_price").getAsDouble() * l.get("quantity").getAsInt();
        return t;
    }

    private void renderSummary() {
        if (cart.isEmpty()) {
            summary.setText("Giỏ hàng trống");
        } else {
            String s = "<html>" + Ui.esc(Json.str(cartRestaurant, "name")) + " · Tiền món <b>" + Ui.money(cartTotal()) + "</b>";
            if (quote != null) {
                s += "<br>Phí giao " + Ui.money(quote, "delivery_fee") + " (" + Ui.km(Json.dbl(quote, "distance_m", 0)) + ", ~"
                        + Ui.minutes(Json.dbl(quote, "duration_s", 0)) + ")<br>Tổng cộng <b style='color:#C46A36'>"
                        + Ui.money(quote, "total") + "</b>";
            } else if (!address.has()) {
                s += "<br>Chọn địa chỉ giao để tính phí";
            }
            summary.setText(s + "</html>");
        }
        orderBtn.setEnabled(quote != null);
    }

    private void useMyLocation() {
        new Thread(() -> {
            double[] p = MyLocation.approximate();
            SwingUtilities.invokeLater(() -> {
                if (!address.has()) address.setPoint(p[0], p[1]);
            });
        }, "my-location").start();
    }

    private JsonObject orderRequest() {
        JsonArray items = new JsonArray();
        for (JsonObject l : cart.values())
            items.add(Json.obj("menu_item_id", l.get("menu_item_id"), "quantity", l.get("quantity")));
        return Json.obj("restaurant_id", id(cartRestaurant), "items", items,
                "delivery_lat", address.lat(), "delivery_lng", address.lng());
    }

    private void requestQuote() {
        if (cart.isEmpty()) {
            Ui.info(this, "Giỏ hàng đang trống");
            return;
        }
        if (!address.has()) {
            Ui.info(this, "Chọn địa chỉ giao hàng (tìm địa chỉ hoặc bấm lên bản đồ)");
            return;
        }
        net.run(this, "FOOD_QUOTE", orderRequest(), r -> {
            quote = r.getAsJsonObject();
            renderSummary();
            drawMap(true);
        });
    }

    private void placeOrder() {
        if (quote == null) return;
        JsonObject req = orderRequest();
        req.addProperty("delivery_address", address.address());
        req.addProperty("note", note.getText().trim());
        req.addProperty("payment_method", (String) payment.getSelectedItem());
        if (!Ui.confirm(this, "Đặt đơn " + Ui.money(quote, "total") + " từ " + Json.str(cartRestaurant, "name") + "?")) return;
        net.run(this, "FOOD_ORDER", req, r -> {
            JsonObject o = r.getAsJsonObject();
            cart.clear();
            cartChanged();
            note.setText("");
            showOrder(o, o.get("points"));
            drawMap(true);
        });
    }

    // ---------- Theo dõi đơn ----------

    private void cancel() {
        if (order == null || !Ui.confirm(this, "Hủy đơn này?")) return;
        net.run(this, "FOOD_CANCEL", Json.obj("id", id(order)), r -> showOrder(r.getAsJsonObject(), orderPoints));
    }

    private void review() {
        if (order == null) return;
        JsonObject target = order;
        UserFrame.review(this, net, "FOOD", id(target), stars -> {
            target.addProperty("my_stars", stars);
            renderTrack();
        });
    }

    private void newOrder() {
        order = null;
        orderPoints = null;
        driverPos = null;
        trackBox.setVisible(false);
        shopBox.setVisible(true);
        drawMap(true);
    }

    private void onOrderUpdate(JsonObject d) {
        if (order != null && id(order) != id(d)) return;
        String before = status();
        showOrder(d, orderPoints);
        String now = status();
        if (!now.equals(before) && visible.getAsBoolean()) {
            String msg = switch (now) {
                case "CONFIRMED" -> "Nhà hàng đã xác nhận đơn, đang tìm tài xế...";
                case "ACCEPTED" -> "Tài xế " + Json.str(d, "driver_name", "") + " đã nhận giao đơn của bạn";
                case "DELIVERING" -> "Tài xế đang giao đồ ăn tới bạn";
                case "DELIVERED" -> "Đơn đã giao thành công. Chúc ngon miệng!";
                case "CANCELLED" -> "Đơn đã hủy: " + Json.str(d, "cancel_reason", "");
                default -> null;
            };
            if (msg != null) Ui.info(this, msg);
        }
    }

    private void showOrder(JsonObject o, JsonElement points) {
        order = o;
        if (Json.has(o, "driver_lat")) driverPos = new double[]{o.get("driver_lat").getAsDouble(), o.get("driver_lng").getAsDouble()};
        orderPoints = points;
        if (orderPoints == null) {
            net.quiet("GEO_ROUTE", Json.obj("from_lat", o.get("restaurant_lat"), "from_lng", o.get("restaurant_lng"),
                    "to_lat", o.get("delivery_lat"), "to_lng", o.get("delivery_lng")), res -> {
                if (order != null && id(order) == id(o)) {
                    orderPoints = res.getAsJsonObject().get("points");
                    drawMap(false);
                }
            });
        }
        shopBox.setVisible(false);
        trackBox.setVisible(true);
        renderTrack();
        drawMap(false);
    }

    private void renderTrack() {
        if (order == null) return;
        String st = status();
        trackTitle.setText("Đơn #" + id(order));
        pill.setStatus(st);
        StringBuilder h = new StringBuilder("<html><div style='width:300px'>");
        h.append("<b>").append(Ui.esc(Json.str(order, "restaurant_name"))).append("</b><br>")
                .append(Ui.esc(Json.str(order, "restaurant_address"))).append("<br>");
        if (order.has("items") && order.get("items").isJsonArray()) {
            for (JsonElement e : order.getAsJsonArray("items")) {
                JsonObject it = e.getAsJsonObject();
                h.append("• ").append(Json.integer(it, "quantity", 1)).append(" × ").append(Ui.esc(Json.str(it, "item_name")))
                        .append(" — ").append(Ui.money(Json.dbl(it, "unit_price", 0) * Json.integer(it, "quantity", 1))).append("<br>");
            }
        }
        h.append("Phí giao ").append(Ui.money(order, "delivery_fee")).append(" · <b>Tổng ").append(Ui.money(order, "total"))
                .append("</b> (").append(Ui.vi(Json.str(order, "payment_method"))).append(")<br>");
        h.append("<b>Giao tới:</b> ").append(Ui.esc(Json.str(order, "delivery_address"))).append("<br>");
        if (Json.has(order, "note")) h.append("<i>Ghi chú: ").append(Ui.esc(Json.str(order, "note"))).append("</i><br>");
        if (Json.has(order, "driver_id")) {
            h.append("<br><b>Tài xế:</b> ").append(Ui.esc(Json.str(order, "driver_name"))).append(" · ")
                    .append(Json.str(order, "driver_phone", "")).append(" · ").append(Ui.esc(Json.str(order, "plate_number", "")))
                    .append(String.format(" · %.1f ★", Json.dbl(order, "driver_rating", 0))).append("<br>");
        } else if (st.equals("PLACED")) {
            h.append("<br><span style='color:#8B8AA6'>Đang chờ nhà hàng xác nhận...</span>");
        } else if (st.equals("CONFIRMED")) {
            h.append("<br><span style='color:#8B8AA6'>Nhà hàng đang chuẩn bị, đang tìm tài xế...</span>");
        }
        if (st.equals("CANCELLED")) h.append("<br><span style='color:#B24B66'>").append(Ui.esc(Json.str(order, "cancel_reason", ""))).append("</span>");
        if (st.equals("DELIVERED") && Json.has(order, "my_stars")) h.append("<br>Bạn đã đánh giá ").append(Ui.stars(Json.integer(order, "my_stars", 0)));
        h.append("</div></html>");
        trackInfo.setText(h.toString());
        cancelBtn.setVisible(CANCELLABLE.contains(st));
        reviewBtn.setVisible(st.equals("DELIVERED") && !Json.has(order, "my_stars"));
        newBtn.setVisible(FINISHED.contains(st));
    }

    private String status() { return order == null ? "" : Json.str(order, "status", ""); }

    private static long id(JsonObject o) { return o == null ? -1 : Json.lng(o, "id", -1); }

    // ---------- Bản đồ ----------

    public void drawMap(boolean fit) {
        if (!visible.getAsBoolean()) return;
        map.clearAll();
        if (order != null) {
            map.onPick(null, null);
            map.setMarker("restaurant", Json.dbl(order, "restaurant_lat", 0), Json.dbl(order, "restaurant_lng", 0),
                    Theme.PEACH_DEEP, Json.str(order, "restaurant_name"));
            map.setMarker("delivery", Json.dbl(order, "delivery_lat", 0), Json.dbl(order, "delivery_lng", 0), Theme.ROSE_DEEP, "Giao tới");
            map.setRoute(orderPoints, Theme.PEACH_DEEP);
            if (driverPos != null && !FINISHED.contains(status()))
                map.setMarker("driver", driverPos[0], driverPos[1], Theme.LAVENDER_DEEP, "Tài xế");
        } else {
            map.onPick("Bấm bản đồ để chọn địa chỉ giao hàng", (lat, lng) -> address.setPoint(lat, lng));
            JsonObject r = cartRestaurant != null ? cartRestaurant : restaurants.getSelectedValue();
            if (r != null) map.setMarker("restaurant", Json.dbl(r, "lat", 0), Json.dbl(r, "lng", 0), Theme.PEACH_DEEP, Json.str(r, "name"));
            if (address.has()) map.setMarker("delivery", address.lat(), address.lng(), Theme.ROSE_DEEP, "Giao tới");
            if (quote != null) map.setRoute(quote.get("points"), Theme.PEACH_DEEP);
        }
        if (fit) map.fit();
    }

    private static class RestaurantRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JsonObject r = (JsonObject) value;
            String html = "<html><b>" + Ui.esc(Json.str(r, "name")) + "</b> <span style='color:#C46A36'>"
                    + Ui.esc(Json.str(r, "category", "")) + "</span><br><span style='color:#8B8AA6'>"
                    + Ui.esc(Json.str(r, "address", "")) + "</span></html>";
            JLabel l = (JLabel) super.getListCellRendererComponent(list, html, index, sel, false);
            l.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            if (sel) {
                l.setBackground(Theme.PEACH);
                l.setForeground(Theme.INK);
            }
            return l;
        }
    }
}
