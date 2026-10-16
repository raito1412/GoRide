package com.goride.client.ui;

import com.goride.client.MyLocation;
import com.goride.client.Net;
import com.goride.common.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đặt đồ ăn theo từng bước, mỗi bước một màn hình riêng:
 * 1 Chọn quán → 2 Chọn món → 3 Giỏ hàng → 4 Giao tới & thanh toán → theo dõi đơn.
 * Bản đồ chỉ xuất hiện ở bước 4 và lúc theo dõi đơn.
 */
public class FoodPanel extends JPanel {
    private static final Set<String> CANCELLABLE = Set.of("PLACED", "CONFIRMED", "ACCEPTED");
    private static final Set<String> FINISHED = Set.of("DELIVERED", "CANCELLED");
    public static final String SHOPS = "shops", MENU = "menu", CART = "cart", CHECKOUT = "checkout", TRACK = "track";

    private final Net net;
    private final MapPanel map;
    private final Runnable onStepChanged;
    private final CardLayout cards = new CardLayout();
    private final JPanel screens = new JPanel(cards);
    // mỗi màn hình cần ô chứa riêng: một component Swing chỉ thuộc về một cha
    private final JPanel checkoutMap = new JPanel(new BorderLayout());
    private final JPanel trackMap = new JPanel(new BorderLayout());
    private String step = SHOPS;

    // bước 1
    private final JTextField search = new JTextField();
    private final JPanel shopGrid = Ui.grid(3);
    private final JLabel shopCount = Ui.muted("");
    private final List<JsonObject> allShops = new ArrayList<>();

    // bước 2
    private final JLabel shopName = Ui.label("", 19f, Font.BOLD);
    private final JLabel shopMeta = Ui.muted("");
    private final JPanel dishGrid = Ui.grid(3);
    private final JButton cartBar = Ui.button("🛒 ", Theme.PEACH, () -> go(CART));
    private JsonObject shop;

    // bước 3
    private final Map<Long, JsonObject> cart = new LinkedHashMap<>();
    private final JPanel cartList = new JPanel();
    private final JLabel cartTotals = new JLabel();
    private final VoucherBox voucherBox = new VoucherBox();
    private final JButton toCheckout;

    // bước 4
    private final PlaceField address;
    private final JTextField note = new JTextField();
    private final JComboBox<String> payment = Ui.codes("CASH", "WALLET");
    private final JLabel summary = Ui.muted("Chọn địa chỉ giao để tính phí");
    private final JButton orderBtn;
    private JsonObject quote;

    // theo dõi đơn
    private final JLabel trackTitle = Ui.label("", 17f, Font.BOLD);
    private final Ui.Pill pill = new Ui.Pill();
    private final JLabel trackInfo = new JLabel();
    private final JButton cancelBtn, reviewBtn, reviewShopBtn, newBtn;
    private JsonObject order;
    private JsonElement orderPoints;
    private double[] driverPos;

    public FoodPanel(Net net, MapPanel map, Runnable onStepChanged) {
        super(new BorderLayout());
        this.net = net;
        this.map = map;
        this.onStepChanged = onStepChanged;
        orderBtn = Ui.button("Đặt món", Theme.PEACH, this::placeOrder);
        address = new PlaceField(net, "Địa chỉ giao hàng", Theme.ROSE_DEEP, () -> null);
        address.onChange(() -> {
            quote = null;
            orderBtn.setEnabled(false);
            drawMap(false);
            requestQuote();
        });
        toCheckout = Ui.button("Tiếp tục →", Theme.MINT, () -> go(CHECKOUT));
        cancelBtn = Ui.button("Hủy đơn", Theme.ROSE, this::cancel);
        reviewBtn = Ui.button("Đánh giá tài xế", Theme.BUTTER, this::review);
        reviewShopBtn = Ui.button("Đánh giá quán", Theme.PEACH, this::reviewShop);
        newBtn = Ui.button("Đặt đơn mới", Theme.PEACH, this::newOrder);

        screens.setOpaque(false);
        screens.add(buildShops(), SHOPS);
        screens.add(buildMenu(), MENU);
        screens.add(buildCart(), CART);
        screens.add(buildCheckout(), CHECKOUT);
        screens.add(buildTrack(), TRACK);
        add(screens);

        net.on("FOOD_UPDATE", this::onOrderUpdate);
        net.on("DRIVER_LOCATION", d -> {
            if (order != null && "FOOD".equals(Json.str(d, "ref_type")) && Json.lng(d, "ref_id", -1) == id(order)) {
                driverPos = new double[]{Json.dbl(d, "lat", 0), Json.dbl(d, "lng", 0)};
                drawMap(false);
            }
        });
        loadRestaurants();
        loadVouchers();
        net.quiet("FOOD_CURRENT", null, r -> {
            if (r.isJsonObject() && r.getAsJsonObject().has("id")) showOrder(r.getAsJsonObject(), null);
        });
        useMyLocation();
        renderCart();
    }

    // ---------- Điều hướng giữa các bước ----------

    public String step() { return step; }

    /** Tiêu đề hiện trên thanh đầu trang theo bước đang mở. */
    public String stepTitle() {
        return switch (step) {
            case MENU -> "Đồ ăn · " + Json.str(shop, "name", "");
            case CART -> "Đồ ăn · Giỏ hàng";
            case CHECKOUT -> "Đồ ăn · Giao tới";
            case TRACK -> "Đồ ăn · Đơn của bạn";
            default -> "Đặt đồ ăn";
        };
    }

    /** Bước nào cần bản đồ thì trả về ô chứa, còn lại null để khung ẩn bản đồ đi. */
    public JPanel mapSlot() {
        return switch (step) {
            case CHECKOUT -> checkoutMap;
            case TRACK -> trackMap;
            default -> null;
        };
    }

    /** Quay lại một bước; null nghĩa là về màn hình chính của ứng dụng. */
    public Runnable back() {
        return switch (step) {
            case MENU -> () -> go(SHOPS);
            case CART -> () -> go(MENU);
            case CHECKOUT -> () -> go(CART);
            default -> null;
        };
    }

    private void go(String name) {
        if (name.equals(CART) && cart.isEmpty()) {
            Ui.info(this, "Giỏ hàng đang trống, hãy chọn món trước");
            return;
        }
        if (name.equals(CHECKOUT) && cart.isEmpty()) return;
        step = name;
        cards.show(screens, name);
        if (name.equals(CHECKOUT)) requestQuote();
        onStepChanged.run();
        SwingUtilities.invokeLater(() -> drawMap(true));
    }

    /** Về bước 1 (khung gọi khi mở lại mục Đồ ăn từ màn hình chính). */
    public void reset() {
        go(order != null && !FINISHED.contains(status()) ? TRACK : SHOPS);
        loadRestaurants();
        loadVouchers();
    }

    private JPanel screen(Component header, Component body, Component footer) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        p.setOpaque(false);
        if (header != null) p.add(header, BorderLayout.NORTH);
        if (body != null) p.add(body, BorderLayout.CENTER);
        if (footer != null) p.add(footer, BorderLayout.SOUTH);
        return p;
    }

    // ---------- Bước 1: chọn quán ----------

    private JPanel buildShops() {
        search.putClientProperty("JTextField.placeholderText", "Tìm tên quán hoặc loại món...");
        search.setPreferredSize(new Dimension(260, 34));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { renderShops(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { renderShops(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        JPanel head = Ui.stepHeader("BƯỚC 1", "Hôm nay ăn gì?", search,
                Ui.button("↻ Làm mới", Theme.SURFACE, this::loadRestaurants));
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.add(shopCount, BorderLayout.NORTH);
        body.add(BaseFrame.vscroll(shopGrid));
        return screen(head, body, null);
    }

    public void loadRestaurants() {
        net.quiet("RESTAURANT_LIST", null, r -> {
            allShops.clear();
            for (JsonElement e : r.getAsJsonArray()) allShops.add(e.getAsJsonObject());
            renderShops();
        });
    }

    private void renderShops() {
        String q = search.getText().trim().toLowerCase();
        shopGrid.removeAll();
        int n = 0;
        for (JsonObject s : allShops) {
            String hay = (Json.str(s, "name", "") + " " + Json.str(s, "category", "") + " "
                    + Json.str(s, "address", "")).toLowerCase();
            if (!q.isEmpty() && !hay.contains(q)) continue;
            shopGrid.add(shopCard(s));
            n++;
        }
        shopCount.setText(n + " quán" + (q.isEmpty() ? "" : " khớp với \"" + q + "\""));
        shopGrid.revalidate();
        shopGrid.repaint();
    }

    private Ui.Card shopCard(JsonObject s) {
        Ui.Card c = new Ui.Card(new BorderLayout(12, 0), Theme.SURFACE);
        c.add(Ui.photo(net, s, Json.str(s, "category", ""), 92, 92), BorderLayout.WEST);
        int votes = Json.integer(s, "rating_count", 0);
        String rating = votes == 0 ? "<span style='color:#5A6472'>Chưa có đánh giá</span>"
                : String.format("<span style='color:#B8480A'>★ %.1f</span> <span style='color:#5A6472'>(%d)</span>",
                  Json.dbl(s, "rating_avg", 0), votes);
        c.add(new JLabel("<html><div style='width:210px'><b style='font-size:12px'>" + Ui.esc(Json.str(s, "name"))
                + "</b><br>" + rating + " · <span style='color:#B8480A'>" + Ui.esc(Json.str(s, "category", "Món ăn"))
                + "</span><br><span style='color:#5A6472'>" + Ui.esc(Json.str(s, "address", ""))
                + "</span></div></html>"));
        c.onClick(() -> openShop(s));
        return c;
    }

    // ---------- Bước 2: chọn món ----------

    private JPanel buildMenu() {
        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBox.setOpaque(false);
        titleBox.add(shopName);
        titleBox.add(shopMeta);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));
        head.add(titleBox, BorderLayout.WEST);
        head.add(Ui.button("← Đổi quán", Theme.SURFACE, () -> go(SHOPS)), BorderLayout.EAST);

        cartBar.setFont(cartBar.getFont().deriveFont(Font.BOLD, 14f));
        cartBar.setPreferredSize(new Dimension(200, 44));
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(cartBar);
        return screen(head, BaseFrame.vscroll(dishGrid), footer);
    }

    private void openShop(JsonObject s) {
        shop = s;
        shopName.setText(Json.str(s, "name", ""));
        shopMeta.setText(Json.str(s, "category", "Món ăn") + " · " + Json.str(s, "address", "")
                + (Json.has(s, "description") ? " · " + Json.str(s, "description") : ""));
        dishGrid.removeAll();
        dishGrid.add(new JLabel("Đang tải thực đơn..."));
        dishGrid.revalidate();
        go(MENU);
        net.run(this, "MENU_LIST", Json.obj("restaurant_id", id(s)), r -> {
            dishGrid.removeAll();
            for (JsonElement e : r.getAsJsonArray()) dishGrid.add(dishCard(e.getAsJsonObject()));
            dishGrid.revalidate();
            dishGrid.repaint();
        });
    }

    private Ui.Card dishCard(JsonObject m) {
        Ui.Card c = new Ui.Card(new BorderLayout(12, 0), Theme.SURFACE);
        c.add(Ui.photo(net, m, Json.str(shop, "category", ""), 104, 104), BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setOpaque(false);
        right.add(new JLabel("<html><div style='width:190px'><b>" + Ui.esc(Json.str(m, "name"))
                + "</b><br><span style='color:#5A6472'>" + Ui.esc(Json.str(m, "description", ""))
                + "</span></div></html>"), BorderLayout.NORTH);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        JLabel price = Ui.label(Ui.money(Json.dbl(m, "price", 0)), 14f, Font.BOLD);
        price.setForeground(Theme.PEACH_DEEP);
        bottom.add(price, BorderLayout.WEST);
        bottom.add(Ui.button("Thêm", Theme.SURFACE, () -> addToCart(m)), BorderLayout.EAST);
        right.add(bottom, BorderLayout.SOUTH);
        c.add(right);
        return c;
    }

    private void addToCart(JsonObject m) {
        if (!cart.isEmpty() && shop != null && cartShopId() != id(shop)) {
            if (!Ui.confirm(this, "Giỏ đang có món của quán khác.\nXóa giỏ để đặt ở \"" + Json.str(shop, "name") + "\"?")) return;
            cart.clear();
        }
        JsonObject line = cart.computeIfAbsent(id(m), k -> Json.obj("menu_item_id", k,
                "item_name", Json.str(m, "name"), "unit_price", m.get("price").getAsDouble(),
                "restaurant_id", id(shop), "quantity", 0));
        if (Json.has(m, "image_id")) line.addProperty("image_id", m.get("image_id").getAsLong());
        int q = line.get("quantity").getAsInt();
        if (q >= 50) {
            Ui.info(this, "Mỗi món tối đa 50 phần");
            return;
        }
        line.addProperty("quantity", q + 1);
        renderCart();
    }

    private long cartShopId() {
        return cart.isEmpty() ? -1 : Json.lng(cart.values().iterator().next(), "restaurant_id", -1);
    }

    // ---------- Bước 3: giỏ hàng ----------

    private JPanel buildCart() {
        cartList.setLayout(new BoxLayout(cartList, BoxLayout.Y_AXIS));
        cartList.setOpaque(false);

        JPanel head = Ui.stepHeader("BƯỚC 3", "Giỏ hàng của bạn",
                Ui.button("← Chọn thêm món", Theme.SURFACE, () -> go(MENU)),
                Ui.button("Xóa giỏ", Theme.SURFACE, () -> {
                    if (!cart.isEmpty() && Ui.confirm(this, "Xóa toàn bộ giỏ hàng?")) {
                        cart.clear();
                        renderCart();
                    }
                }));

        JPanel bottom = Ui.column();
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        voucherBox.setMaximumSize(new Dimension(420, 34));
        voucherBox.addActionListener(e -> {
            quote = null;
            renderCart();
        });
        bottom.add(Ui.row(Ui.label("Thẻ giảm giá", 13f, Font.BOLD), voucherBox));
        bottom.add(Box.createVerticalStrut(6));
        Ui.Card totals = new Ui.Card(new BorderLayout(), Theme.ROW_ALT);
        totals.add(cartTotals);
        bottom.add(totals);
        bottom.add(Box.createVerticalStrut(8));
        toCheckout.setPreferredSize(new Dimension(200, 42));
        bottom.add(Ui.grow(toCheckout));
        return screen(head, BaseFrame.vscroll(cartList), bottom);
    }

    private void renderCart() {
        cartList.removeAll();
        int items = 0;
        for (JsonObject line : new ArrayList<>(cart.values())) {
            if (line.get("quantity").getAsInt() <= 0) {
                cart.remove(Json.lng(line, "menu_item_id", -1));
                continue;
            }
            cartList.add(cartRow(line));
            cartList.add(Box.createVerticalStrut(8));
            items += (int) (Json.dbl(line, "unit_price", 0) * line.get("quantity").getAsInt());
        }
        if (cart.isEmpty()) cartList.add(Ui.muted("Giỏ hàng trống"));

        int discount = voucherBox.preview(items);
        cartTotals.setText("<html><div style='width:360px'>Tiền món: <b>" + Ui.money(items) + "</b>"
                + (discount > 0 ? "<br>Giảm giá tạm tính: <b style='color:#13703F'>-" + Ui.money(discount) + "</b>" : "")
                + "</div></html>");

        int count = cart.values().stream().mapToInt(l -> l.get("quantity").getAsInt()).sum();
        cartBar.setText(count == 0 ? "Giỏ hàng trống" : "Xem giỏ hàng (" + count + " món) · " + Ui.money(items));
        cartBar.setEnabled(count > 0);
        toCheckout.setEnabled(count > 0);
        cartList.revalidate();
        cartList.repaint();
    }

    private Ui.Card cartRow(JsonObject line) {
        Ui.Card c = new Ui.Card(new BorderLayout(12, 0), Theme.SURFACE);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        c.add(Ui.photo(net, line, Json.str(shop, "category", ""), 64, 64), BorderLayout.WEST);
        int qty = line.get("quantity").getAsInt();
        double unit = Json.dbl(line, "unit_price", 0);
        c.add(new JLabel("<html><b>" + Ui.esc(Json.str(line, "item_name")) + "</b><br><span style='color:#5A6472'>"
                + Ui.money(unit) + " × " + qty + "</span></html>"));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        JLabel sub = Ui.label(Ui.money(unit * qty), 14f, Font.BOLD);
        sub.setForeground(Theme.PEACH_DEEP);
        right.add(sub);
        right.add(Ui.button("−", Theme.SURFACE, () -> changeQty(line, -1)));
        right.add(Ui.button("+", Theme.SURFACE, () -> changeQty(line, 1)));
        right.add(Ui.button("✕", Theme.SURFACE, () -> {
            cart.remove(Json.lng(line, "menu_item_id", -1));
            renderCart();
        }));
        c.add(right, BorderLayout.EAST);
        return c;
    }

    private void changeQty(JsonObject line, int delta) {
        int q = line.get("quantity").getAsInt() + delta;
        if (q > 50) return;
        line.addProperty("quantity", q);
        quote = null;
        renderCart();
    }

    // ---------- Thẻ giảm giá ----------

    private void loadVouchers() {
        voucherBox.reload(net, this::renderCart);
    }

    // ---------- Bước 4: giao tới & đặt ----------

    private JPanel buildCheckout() {
        JPanel form = Ui.column();
        form.setOpaque(false);
        form.add(Ui.section("Giao tới"));
        form.add(Ui.row(Ui.button("📍 Vị trí hiện tại", Theme.SURFACE, this::useMyLocation), Ui.muted("hoặc bấm lên bản đồ")));
        form.add(address);
        note.putClientProperty("JTextField.placeholderText", "Ghi chú cho quán (ít cay, không hành...)");
        form.add(Box.createVerticalStrut(8));
        form.add(Ui.grow(note));
        form.add(Ui.section("Thanh toán"));
        payment.addActionListener(e -> requestQuote());
        form.add(Ui.row(payment));
        form.add(Box.createVerticalStrut(8));
        Ui.Card box = new Ui.Card(new BorderLayout(), Theme.ROW_ALT);
        box.add(summary);
        form.add(box);
        orderBtn.setPreferredSize(new Dimension(200, 44));
        form.add(Box.createVerticalStrut(10));
        form.add(Ui.grow(orderBtn));

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(Ui.stepHeader("BƯỚC 4", "Giao tới đâu?",
                Ui.button("← Về giỏ hàng", Theme.SURFACE, () -> go(CART))), BorderLayout.NORTH);
        left.add(BaseFrame.vscroll(form));
        left.setPreferredSize(new Dimension(470, 100));

        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 0));
        p.setOpaque(false);
        p.add(left, BorderLayout.WEST);
        p.add(checkoutMap);
        return p;
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
            items.add(Json.obj("menu_item_id", Json.lng(l, "menu_item_id", 0), "quantity", l.get("quantity").getAsInt()));
        JsonObject req = Json.obj("restaurant_id", cartShopId(), "items", items,
                "delivery_lat", address.lat(), "delivery_lng", address.lng());
        String code = voucherBox.code();
        if (code != null) req.addProperty("voucher_code", code);
        return req;
    }

    private void requestQuote() {
        orderBtn.setEnabled(false);
        if (cart.isEmpty() || !address.has()) {
            summary.setText("Chọn địa chỉ giao để tính phí");
            return;
        }
        summary.setText("Đang tính...");
        double[] at = {address.lat(), address.lng()};
        net.run(this, "FOOD_QUOTE", orderRequest(), r -> {
            if (at[0] != address.lat() || at[1] != address.lng()) return; // địa chỉ đã đổi trong lúc chờ
            quote = r.getAsJsonObject();
            orderBtn.setEnabled(true);
            int discount = Json.integer(quote, "discount", 0);
            summary.setText("<html><div style='width:380px'>"
                    + "Tiền món: <b>" + Ui.money(quote, "items_total") + "</b><br>"
                    + "Phí giao (" + Ui.km(Json.dbl(quote, "distance_m", 0)) + "): <b>" + Ui.money(quote, "delivery_fee") + "</b><br>"
                    + (discount > 0 ? "Giảm giá: <b style='color:#13703F'>-" + Ui.money(discount) + "</b><br>" : "")
                    + "<span style='font-size:14px'>Tổng cộng: <b>" + Ui.money(quote, "total") + "</b></span><br>"
                    + "<span style='color:#5A6472'>Giao trong khoảng " + Ui.minutes(Json.dbl(quote, "duration_s", 0)) + "</span>"
                    + "</div></html>");
            drawMap(true);
        });
    }

    private void placeOrder() {
        if (cart.isEmpty()) {
            Ui.info(this, "Giỏ hàng đang trống");
            return;
        }
        if (!address.has()) {
            Ui.info(this, "Hãy chọn địa chỉ giao hàng");
            return;
        }
        if (quote == null) return;
        JsonObject req = orderRequest();
        req.addProperty("delivery_address", address.address());
        req.addProperty("note", note.getText().trim());
        req.addProperty("payment_method", (String) payment.getSelectedItem());
        net.run(this, orderBtn, "FOOD_ORDER", req, r -> {
            JsonObject o = r.getAsJsonObject();
            cart.clear();
            renderCart();
            loadVouchers();
            showOrder(o, o.get("points"));
        });
    }

    // ---------- Theo dõi đơn ----------

    private JPanel buildTrack() {
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(470, 100));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));
        head.add(trackTitle, BorderLayout.WEST);
        head.add(pill, BorderLayout.EAST);
        left.add(head, BorderLayout.NORTH);

        JPanel col = Ui.column();
        col.setOpaque(false);
        col.setBorder(null);
        Ui.Card card = new Ui.Card(new BorderLayout(), Theme.SURFACE);
        card.add(trackInfo);
        col.add(card);
        col.add(Box.createVerticalStrut(10));
        col.add(Ui.row(cancelBtn, reviewBtn, reviewShopBtn, newBtn));
        left.add(BaseFrame.vscroll(col));

        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 0));
        p.setOpaque(false);
        p.add(left, BorderLayout.WEST);
        p.add(trackMap);
        return p;
    }

    private void onOrderUpdate(JsonObject d) {
        if (order != null && id(order) != id(d)) return;
        String before = status();
        showOrder(d, orderPoints);
        String now = status();
        if (!now.equals(before) && step.equals(TRACK)) {
            String t = "Đơn #" + id(d);
            if (now.equals("ACCEPTED")) Toast.info(this, "🛵", t, "Tài xế " + Json.str(d, "driver_name", "") + " đã nhận đơn", Theme.LAVENDER);
            else if (now.equals("DELIVERED")) Toast.info(this, "✅", t, "Đã giao xong. Chúc bạn ngon miệng!", Theme.MINT);
            else if (now.equals("CANCELLED")) Toast.info(this, "⚠️", t, "Đã hủy: " + Json.str(d, "cancel_reason", ""), Theme.ROSE);
        }
    }

    private void showOrder(JsonObject o, JsonElement points) {
        order = o;
        if (points != null) orderPoints = points;
        if (Json.has(o, "driver_lat")) driverPos = new double[]{o.get("driver_lat").getAsDouble(), o.get("driver_lng").getAsDouble()};
        renderTrack();
        go(TRACK);
    }

    private void renderTrack() {
        if (order == null) return;
        String st = status();
        trackTitle.setText("Đơn #" + id(order) + " · " + Json.str(order, "restaurant_name", ""));
        pill.setStatus(st);
        StringBuilder h = new StringBuilder("<html><div style='width:390px'>");
        for (JsonElement e : order.getAsJsonArray("items")) {
            JsonObject it = e.getAsJsonObject();
            h.append(Json.integer(it, "quantity", 1)).append(" × ").append(Ui.esc(Json.str(it, "item_name"))).append("<br>");
        }
        int discount = Json.integer(order, "discount", 0);
        h.append("<br>Tiền món ").append(Ui.money(order, "items_total"))
                .append(" · phí giao ").append(Ui.money(order, "delivery_fee"));
        if (discount > 0) h.append(" · <span style='color:#13703F'>giảm ").append(Ui.money(discount)).append("</span>");
        h.append("<br><b>Tổng ").append(Ui.money(order, "total")).append("</b> (")
                .append(Ui.vi(Json.str(order, "payment_method"))).append(")<br><br>");
        h.append("<b>Giao tới:</b> ").append(Ui.esc(Json.str(order, "delivery_address"))).append("<br>");
        if (Json.has(order, "note")) h.append("<i>Ghi chú: ").append(Ui.esc(Json.str(order, "note"))).append("</i><br>");
        if (Json.has(order, "driver_id")) {
            h.append("<br><b>Tài xế:</b> ").append(Ui.esc(Json.str(order, "driver_name"))).append(" · ")
                    .append(Json.str(order, "driver_phone", "")).append(" · ").append(Ui.esc(Json.str(order, "plate_number", "")))
                    .append(String.format(" · %.1f ★", Json.dbl(order, "driver_rating", 0))).append("<br>");
        } else if (st.equals("PLACED")) {
            h.append("<br><span style='color:#5A6472'>Đang chờ quán xác nhận...</span>");
        } else if (st.equals("CONFIRMED")) {
            h.append("<br><span style='color:#5A6472'>Quán đang chuẩn bị, đang tìm tài xế...</span>");
        }
        if (st.equals("CANCELLED")) h.append("<br><span style='color:#B3161B'>").append(Ui.esc(Json.str(order, "cancel_reason", ""))).append("</span>");
        if (st.equals("DELIVERED") && Json.has(order, "my_stars"))
            h.append("<br>Tài xế: ").append(Ui.stars(Json.integer(order, "my_stars", 0)));
        if (st.equals("DELIVERED") && Json.has(order, "my_shop_stars"))
            h.append("<br>Quán: ").append(Ui.stars(Json.integer(order, "my_shop_stars", 0)));
        trackInfo.setText(h.append("</div></html>").toString());
        cancelBtn.setVisible(CANCELLABLE.contains(st));
        reviewBtn.setVisible(st.equals("DELIVERED") && !Json.has(order, "my_stars"));
        reviewShopBtn.setVisible(st.equals("DELIVERED") && !Json.has(order, "my_shop_stars"));
        newBtn.setVisible(FINISHED.contains(st));
    }

    private void cancel() {
        if (order == null || !Ui.confirm(this, "Hủy đơn này?")) return;
        net.run(this, cancelBtn, "FOOD_CANCEL", Json.obj("id", id(order)), r -> showOrder(r.getAsJsonObject(), orderPoints));
    }

    private void review() {
        if (order == null) return;
        JsonObject target = order;
        UserFrame.review(this, net, "FOOD", id(target), stars -> {
            target.addProperty("my_stars", stars);
            renderTrack();
        });
    }

    private void reviewShop() {
        if (order == null) return;
        JsonObject target = order;
        UserFrame.review(this, net, "FOOD", id(target), "RESTAURANT", stars -> {
            target.addProperty("my_shop_stars", stars);
            renderTrack();
            loadRestaurants(); // điểm quán vừa đổi
        });
    }

    private void newOrder() {
        order = null;
        orderPoints = null;
        driverPos = null;
        quote = null;
        loadVouchers();
        go(SHOPS);
    }

    private String status() { return order == null ? "" : Json.str(order, "status", ""); }

    private static long id(JsonObject o) { return o == null ? -1 : Json.lng(o, "id", -1); }

    // ---------- Bản đồ ----------

    public void drawMap(boolean fit) {
        if (mapSlot() == null) return;
        map.clearAll();
        if (step.equals(TRACK) && order != null) {
            map.onPick(null, null);
            map.setMarker("restaurant", Json.dbl(order, "restaurant_lat", 0), Json.dbl(order, "restaurant_lng", 0),
                    Theme.PEACH_DEEP, Json.str(order, "restaurant_name"));
            map.setMarker("delivery", Json.dbl(order, "delivery_lat", 0), Json.dbl(order, "delivery_lng", 0), Theme.ROSE_DEEP, "Giao tới");
            map.setRoute(orderPoints, Theme.PEACH_DEEP);
            if (driverPos != null && !FINISHED.contains(status()))
                map.setMarker("driver", driverPos[0], driverPos[1], Theme.LAVENDER_DEEP, "Tài xế");
        } else {
            map.onPick("Bấm bản đồ để chọn địa chỉ giao hàng", (lat, lng) -> address.setPoint(lat, lng));
            if (shop != null) map.setMarker("restaurant", Json.dbl(shop, "lat", 0), Json.dbl(shop, "lng", 0),
                    Theme.PEACH_DEEP, Json.str(shop, "name"));
            if (address.has()) map.setMarker("delivery", address.lat(), address.lng(), Theme.ROSE_DEEP, "Giao tới");
            if (quote != null) map.setRoute(quote.get("points"), Theme.PEACH_DEEP);
        }
        if (fit) map.fit();
    }
}
