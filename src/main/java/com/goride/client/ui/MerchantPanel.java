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
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Khách hàng đăng ký mở quán ăn: điền thông tin quán + ảnh, thêm từng món (tên, mô tả, giá, ảnh),
 * gửi hồ sơ rồi chờ Admin duyệt. Duyệt xong quán xuất hiện trong tab Đồ ăn của mọi người.
 */
public class MerchantPanel extends JPanel {
    private static final String[] CATEGORIES = {"Cơm", "Phở", "Bún", "Bánh mì", "Mì / Hủ tiếu", "Lẩu - Nướng",
            "Đồ chay", "Ăn vặt", "Trà sữa", "Cà phê", "Tráng miệng", "Khác"};

    private static final String APPLY = "apply", SHOP = "shop";

    private final Net net;
    private final MapPanel map;
    private final BooleanSupplier visible;
    private final Runnable onLayoutChanged;
    private final CardLayout cards = new CardLayout();
    private final JPanel screens = new JPanel(cards);
    private String mode = APPLY;
    private boolean wantApplyForm;

    // ----- quán đã được duyệt -----
    private final JLabel shopHeadline = Ui.label("Quán của bạn", 19f, Font.BOLD);
    private final JLabel shopSummary = Ui.muted("");
    private final DataTable shopTable = new DataTable("id|#", "name|Quán", "category|Loại", "address|Địa chỉ",
            "item_count|Số món", "active_orders|Đơn đang chạy", "rating_avg|Đánh giá|rating",
            "rating_count|Lượt", "is_open|Mở cửa|bool");
    private final DataTable orderTable = new DataTable("id|#", "created_at|Lúc|time", "user_name|Khách",
            "items_text|Món", "items_total|Tiền món|money", "payment_method|Thanh toán|status",
            "status|Trạng thái|status", "driver_name|Tài xế");
    private final DataTable reviewTable = new DataTable("created_at|Lúc|time", "restaurant_name|Quán",
            "user_name|Khách", "ref_id|Đơn #", "stars|Sao|stars", "comment|Nhận xét");
    private final JLabel pendingHint = Ui.muted(" ");
    private final JTabbedPane shopTabs = BaseFrame.newTabsPublic();
    private StatementView statement;

    // ----- trạng thái hồ sơ đã gửi -----
    private final JPanel statusBox = Ui.column();
    private final JLabel statusTitle = Ui.label("", 15f, Font.BOLD);
    private final Ui.Pill statusPill = new Ui.Pill();
    private final JLabel statusInfo = new JLabel();
    private final JButton withdrawBtn, againBtn;
    private JsonObject application;

    // ----- biểu mẫu -----
    private final JPanel formBox = Ui.column();
    private final JTextField name = new JTextField();
    private final JComboBox<String> category = new JComboBox<>(CATEGORIES);
    private final JTextField phone = new JTextField();
    private final JTextArea description = new JTextArea(3, 20);
    private final PlaceField address;
    private final ImageField shopImage;
    private final DataTable dishes = new DataTable("name|Món", "price|Giá|money", "description|Mô tả", "has_image|Ảnh|bool");
    private final List<JsonObject> menu = new ArrayList<>();
    private final JLabel menuSummary = Ui.muted("Chưa có món nào");
    private final JButton submitBtn;
    private final JPanel mapSlot = new JPanel(new BorderLayout());

    public MerchantPanel(Net net, MapPanel map, BooleanSupplier visible, Runnable onLayoutChanged) {
        super(new BorderLayout());
        this.net = net;
        this.map = map;
        this.visible = visible;
        this.onLayoutChanged = onLayoutChanged;
        address = new PlaceField(net, "Địa chỉ quán", Theme.PEACH_DEEP, () -> null);
        address.onChange(this::drawMap);
        shopImage = new ImageField(net, "Ảnh mặt tiền quán", 140, 100);

        // ---------- khối trạng thái ----------
        statusBox.setOpaque(false);
        statusBox.setBorder(null);
        statusBox.add(Ui.section("Hồ sơ mở quán của bạn"));
        Ui.Card card = new Ui.Card(new BorderLayout(0, 8), Theme.SURFACE);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(statusTitle, BorderLayout.WEST);
        head.add(statusPill, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(statusInfo);
        withdrawBtn = Ui.button("Rút hồ sơ", Theme.ROSE, this::withdraw);
        againBtn = Ui.button("Đăng ký quán mới", Theme.MINT, this::startNew);
        JPanel acts = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        acts.setOpaque(false);
        acts.add(withdrawBtn);
        acts.add(againBtn);
        acts.add(Ui.button("↻ Kiểm tra lại", Theme.SURFACE, this::loadMine));
        card.add(acts, BorderLayout.SOUTH);
        statusBox.add(card);
        statusBox.setVisible(false);

        // ---------- biểu mẫu ----------
        formBox.setOpaque(false);
        formBox.setBorder(null);
        formBox.add(Ui.section("Thông tin quán"));
        name.putClientProperty("JTextField.placeholderText", "Ví dụ: Cơm Tấm Cô Ba");
        formBox.add(Ui.grow(name));
        category.setEditable(true);
        formBox.add(Ui.row(Ui.muted("Loại món:"), category));
        phone.putClientProperty("JTextField.placeholderText", "Số điện thoại quán (không bắt buộc)");
        formBox.add(Box.createVerticalStrut(6));
        formBox.add(Ui.grow(phone));

        formBox.add(Ui.section("Địa chỉ quán"));
        formBox.add(Ui.row(Ui.button("📍 Vị trí hiện tại", Theme.MINT, this::useMyLocation), Ui.muted("hoặc bấm lên bản đồ")));
        formBox.add(address);

        formBox.add(Ui.section("Ảnh quán"));
        formBox.add(shopImage);

        formBox.add(Ui.section("Giới thiệu quán"));
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        JScrollPane ds = Ui.scroll(description);
        ds.setPreferredSize(new Dimension(300, 70));
        ds.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        formBox.add(ds);

        formBox.add(Ui.section("Thực đơn"));
        dishes.widths(130, 80, 150, 40);
        JScrollPane ms = dishes.scroll();
        ms.setPreferredSize(new Dimension(300, 160));
        ms.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        formBox.add(ms);
        dishes.onDoubleClick(d -> editDish());
        formBox.add(Ui.row(Ui.button("+ Thêm món", Theme.PEACH, this::addDish),
                Ui.button("Sửa món", Theme.SKY, this::editDish),
                Ui.button("Xóa món", Theme.ROSE, this::removeDish)));
        formBox.add(menuSummary);

        submitBtn = Ui.button("Gửi hồ sơ cho Admin duyệt", Theme.MINT, this::submit);
        formBox.add(Box.createVerticalStrut(8));
        formBox.add(Ui.grow(submitBtn));
        formBox.add(Ui.muted("<html><span style='font-size:11px'>Admin sẽ xem xét hồ sơ. "
                + "Khi được duyệt, quán và thực đơn của bạn xuất hiện trong tab Đồ ăn.</span></html>"));

        JPanel col = Ui.column();
        col.setOpaque(false);
        col.add(statusBox);
        col.add(formBox);
        JScrollPane left = BaseFrame.vscroll(col);
        left.setPreferredSize(new Dimension(470, 100));
        JPanel applyScreen = new JPanel(new BorderLayout(12, 0));
        applyScreen.setOpaque(false);
        applyScreen.setBorder(BorderFactory.createEmptyBorder(8, 4, 12, 0));
        applyScreen.add(left, BorderLayout.WEST);
        applyScreen.add(mapSlot);

        screens.setOpaque(false);
        screens.add(applyScreen, APPLY);
        screens.add(buildShopScreen(), SHOP);
        add(screens);

        net.on("MERCHANT_UPDATE", d -> {
            boolean approved = "APPROVED".equals(Json.str(d, "status"));
            Toast.show(this, approved ? "🎉" : "😔",
                    approved ? "Quán đã được duyệt!" : "Hồ sơ bị từ chối",
                    approved ? "\"" + Json.str(d, "name") + "\" đã lên hệ thống, khách có thể đặt món ngay."
                             : Json.str(d, "name") + ": " + Json.str(d, "admin_note", ""),
                    approved ? Theme.MINT : Theme.ROSE,
                    approved ? "Xem quán" : null,
                    approved ? this::loadMine : null);
            wantApplyForm = false;
            loadMine();
        });
        net.on("MERCHANT_ORDER", this::onShopOrder);
        net.on("MERCHANT_REVIEW", this::onShopReview);
        renderMenu();
        loadMine();
    }

    /** Chỉ màn hình điền hồ sơ mới cần bản đồ để chọn vị trí quán. */
    public JPanel mapSlot() { return mode.equals(APPLY) ? mapSlot : null; }

    /** Tiêu đề hiện trên thanh đầu trang. */
    public String title() { return mode.equals(SHOP) ? "Quán của bạn" : "Mở quán ăn"; }

    // ---------- Màn hình quán đã duyệt ----------

    private JPanel buildShopScreen() {
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(shopHeadline);
        titles.add(shopSummary);
        head.add(titles, BorderLayout.WEST);
        JPanel acts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acts.setOpaque(false);
        acts.add(Ui.button("↻ Làm mới", Theme.SURFACE, this::loadShop));
        acts.add(Ui.button("+ Đăng ký thêm quán", Theme.MINT, this::startNew));
        head.add(acts, BorderLayout.EAST);

        shopTable.widths(40, 200, 100, 260, 70, 90, 80, 50, 70);
        orderTable.widths(40, 110, 140, 300, 90, 90, 130, 140);
        reviewTable.widths(110, 180, 140, 60, 90, 380);
        orderTable.onDoubleClick(this::showOrder);

        // ----- tab đơn hàng: chủ quán nhận / từ chối -----
        JPanel orders = new JPanel(new BorderLayout(0, 8));
        orders.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel oh = new JPanel(new BorderLayout());
        oh.setOpaque(false);
        pendingHint.setFont(pendingHint.getFont().deriveFont(Font.BOLD, 13f));
        oh.add(pendingHint, BorderLayout.WEST);
        JPanel oa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        oa.setOpaque(false);
        oa.add(Ui.button("✅ Nhận đơn", Theme.MINT, this::acceptSelected));
        oa.add(Ui.button("✖ Từ chối", Theme.ROSE, this::rejectSelected));
        oa.add(Ui.button("Xem chi tiết", Theme.SKY, () -> showOrder(orderTable.selected())));
        oh.add(oa, BorderLayout.EAST);
        orders.add(oh, BorderLayout.NORTH);
        orders.add(orderTable.scroll());
        orders.add(Ui.muted("<html>Đơn ở trạng thái <b>Chờ nhà hàng</b> là đang chờ bạn nhận. Nhận xong, hệ thống mới tìm tài xế "
                + "tới lấy món. Không nhận trong 3 phút thì đơn tự hủy. Khi bạn tắt app, hệ thống xác nhận hộ.</html>"),
                BorderLayout.SOUTH);

        JPanel shops = new JPanel(new BorderLayout(0, 6));
        shops.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        shops.add(shopTable.scroll());

        JPanel reviews = new JPanel(new BorderLayout(0, 6));
        reviews.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        reviews.add(reviewTable.scroll());

        statement = new StatementView(net, "MERCHANT_STATEMENT");
        shopTabs.addTab("🧾 Đơn hàng", orders);
        shopTabs.addTab("🏪 Quán của tôi", shops);
        shopTabs.addTab("⭐ Đánh giá", reviews);
        shopTabs.addTab("📄 Đối soát tháng", statement);
        shopTabs.addChangeListener(e -> {
            if (shopTabs.getSelectedIndex() == 3) statement.load();
        });

        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        p.add(head, BorderLayout.NORTH);
        p.add(shopTabs);
        return p;
    }

    private void loadShop() {
        net.quiet("MERCHANT_SHOPS", null, r -> {
            shopTable.setRows(r);
            int shops = r.getAsJsonArray().size(), active = 0, dishes = 0;
            String first = "";
            double stars = 0;
            int votes = 0;
            for (JsonElement e : r.getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                active += Json.integer(o, "active_orders", 0);
                dishes += Json.integer(o, "item_count", 0);
                int n = Json.integer(o, "rating_count", 0);
                stars += Json.dbl(o, "rating_avg", 0) * n;
                votes += n;
                if (first.isEmpty()) first = Json.str(o, "name", "");
            }
            shopHeadline.setText(shops == 1 ? first : "Quán của bạn");
            shopSummary.setText(shops + " quán · " + dishes + " món đang bán · " + active + " đơn đang chạy · "
                    + (votes == 0 ? "chưa có đánh giá" : String.format("%.1f ★ (%d đánh giá)", stars / votes, votes)));
        });
        net.quiet("MERCHANT_ORDERS", null, r -> {
            orderTable.setRows(r);
            int waiting = 0;
            for (JsonElement e : r.getAsJsonArray())
                if ("PLACED".equals(Json.str(e.getAsJsonObject(), "status"))) waiting++;
            pendingHint.setText("<html>" + (waiting == 0 ? "Không có đơn nào đang chờ bạn nhận"
                    : "🔔 " + waiting + " đơn đang chờ bạn nhận") + "&nbsp;</html>");
            pendingHint.setForeground(waiting == 0 ? Theme.MUTED : Theme.PEACH_DEEP);
            shopTabs.setTitleAt(0, waiting == 0 ? "🧾 Đơn hàng" : "🧾 Đơn hàng (" + waiting + ")");
        });
        net.quiet("MERCHANT_REVIEWS", null, reviewTable::setRows);
    }

    // ---------- Nhận / từ chối đơn ----------

    private void acceptSelected() {
        JsonObject o = orderTable.selected();
        if (o == null) {
            Ui.info(this, "Chọn một đơn trong bảng trước");
            return;
        }
        accept(Json.lng(o, "id", 0));
    }

    private void accept(long id) {
        net.run(this, "MERCHANT_ORDER_ACCEPT", Json.obj("id", id), r -> {
            Toast.info(this, "✅", "Đã nhận đơn #" + id, "Hệ thống đang tìm tài xế tới lấy món.", Theme.MINT);
            loadShop();
        });
    }

    private void rejectSelected() {
        JsonObject o = orderTable.selected();
        if (o == null) {
            Ui.info(this, "Chọn một đơn trong bảng trước");
            return;
        }
        if (!"PLACED".equals(Json.str(o, "status"))) {
            Ui.info(this, "Chỉ từ chối được đơn đang chờ bạn nhận");
            return;
        }
        String[] reasons = {"Hết món", "Quán quá tải", "Sắp đóng cửa", "Lý do khác..."};
        Object pick = JOptionPane.showInputDialog(this, "Lý do từ chối đơn #" + Json.lng(o, "id", 0)
                + " (khách sẽ đọc được):", "Từ chối đơn", JOptionPane.QUESTION_MESSAGE, null, reasons, reasons[0]);
        if (pick == null) return;
        String reason = pick.toString();
        if (reason.startsWith("Lý do khác")) {
            reason = JOptionPane.showInputDialog(this, "Nhập lý do:", "Từ chối đơn", JOptionPane.QUESTION_MESSAGE);
            if (reason == null || reason.isBlank()) return;
        }
        net.run(this, "MERCHANT_ORDER_REJECT", Json.obj("id", Json.lng(o, "id", 0), "reason", reason.trim()),
                r -> loadShop());
    }

    /** Có đơn mới thì bật thông báo nổi kèm nút nhận ngay; các lần đổi trạng thái sau chỉ làm mới bảng. */
    private void onShopOrder(JsonObject d) {
        loadShop();
        if (!"NEW".equals(Json.str(d, "kind"))) return;
        StringBuilder items = new StringBuilder();
        if (d.has("items") && d.get("items").isJsonArray()) {
            for (JsonElement e : d.getAsJsonArray("items")) {
                JsonObject it = e.getAsJsonObject();
                if (items.length() > 0) items.append(", ");
                items.append(Json.integer(it, "quantity", 1)).append(" × ").append(Json.str(it, "item_name"));
            }
        }
        long id = Json.lng(d, "id", 0);
        Toast.show(this, "🔔", "Đơn mới #" + id + " · " + Ui.money(Json.dbl(d, "items_total", 0)),
                Json.str(d, "restaurant_name", "") + " — " + items
                        + (Json.has(d, "note") ? "\nGhi chú: " + Json.str(d, "note") : ""),
                Theme.BUTTER, "✅ Nhận đơn ngay", () -> accept(id));
        Toolkit.getDefaultToolkit().beep();
        wantApplyForm = false;
        setMode(SHOP);
        shopTabs.setSelectedIndex(0);
    }

    private void onShopReview(JsonObject d) {
        loadShop();
        Toast.info(this, "⭐", Ui.stars(Json.integer(d, "stars", 0)) + " · " + Json.str(d, "restaurant_name", ""),
                Json.str(d, "comment", "").isBlank() ? "Khách vừa đánh giá quán của bạn." : Json.str(d, "comment", ""),
                Theme.BUTTER);
    }

    private void showOrder(JsonObject o) {
        if (o == null) {
            Ui.info(this, "Chọn một đơn trong bảng trước");
            return;
        }
        int discount = Json.integer(o, "discount", 0);
        Ui.info(this, "<html><b>Đơn #" + Json.lng(o, "id", 0) + " · " + Ui.vi(Json.str(o, "status")) + "</b><br><br>"
                + "Quán: " + Ui.esc(Json.str(o, "restaurant_name", "")) + "<br>"
                + "Khách: " + Ui.esc(Json.str(o, "user_name", "")) + " · " + Json.str(o, "user_phone", "") + "<br>"
                + "Món: " + Ui.esc(Json.str(o, "items_text", "")) + "<br>"
                + (Json.has(o, "note") ? "Ghi chú: " + Ui.esc(Json.str(o, "note")) + "<br>" : "")
                + "<br>Tiền món " + Ui.money(o, "items_total") + " · phí giao " + Ui.money(o, "delivery_fee")
                + (discount > 0 ? " · giảm " + Ui.money(discount) : "")
                + "<br><b>Tổng " + Ui.money(o, "total") + "</b> (" + Ui.vi(Json.str(o, "payment_method")) + ")<br>"
                + "Giao tới: " + Ui.esc(Json.str(o, "delivery_address", "")) + "<br>"
                + "Tài xế: " + Ui.esc(Json.str(o, "driver_name", "—")) + "</html>");
    }

    private void setMode(String m) {
        if (mode.equals(m)) return;
        mode = m;
        cards.show(screens, m);
        onLayoutChanged.run();
    }

    // ---------- Hồ sơ ----------

    public void loadMine() {
        net.quiet("MERCHANT_SHOPS", null, shops -> {
            boolean hasShop = shops.getAsJsonArray().size() > 0;
            setMode(hasShop && !wantApplyForm ? SHOP : APPLY);
            if (hasShop) loadShop();
        });
        net.quiet("MERCHANT_MINE", null, r -> {
            JsonArray list = r.getAsJsonArray();
            if (list.size() == 0) {
                showApplication(null);
                return;
            }
            JsonObject latest = list.get(0).getAsJsonObject();
            net.quiet("MERCHANT_DETAIL", Json.obj("id", Json.lng(latest, "id", 0)), d -> showApplication(d.getAsJsonObject()));
        });
    }

    private void showApplication(JsonObject app) {
        application = app;
        if (app == null) {
            statusBox.setVisible(false);
            formBox.setVisible(true);
            revalidate();
            repaint();
            return;
        }
        String status = Json.str(app, "status", "PENDING");
        statusTitle.setText(Json.str(app, "name", ""));
        statusPill.setStatus(status);

        StringBuilder h = new StringBuilder("<html><div style='width:380px'>");
        h.append(Ui.esc(Json.str(app, "address", ""))).append("<br>");
        int items = app.has("items") ? app.getAsJsonArray("items").size() : Json.integer(app, "item_count", 0);
        h.append("<span style='color:#5A6472'>").append(items).append(" món · gửi lúc ")
                .append(Json.str(app, "created_at", "")).append("</span><br><br>");
        switch (status) {
            case "PENDING" -> h.append("Hồ sơ đang chờ Admin duyệt. Bạn có thể rút lại để sửa rồi gửi hồ sơ mới.");
            case "APPROVED" -> h.append("<b style='color:#13703F'>Đã được duyệt.</b> Quán #")
                    .append(Json.lng(app, "restaurant_id", 0))
                    .append(" đã lên hệ thống, khách có thể đặt món trong tab Đồ ăn.");
            default -> h.append("<b style='color:#B3161B'>Bị từ chối.</b><br>Lý do: ")
                    .append(Ui.esc(Json.str(app, "admin_note", "—")));
        }
        h.append("</div></html>");
        statusInfo.setText(h.toString());

        withdrawBtn.setVisible(status.equals("PENDING"));
        againBtn.setVisible(!status.equals("PENDING"));
        statusBox.setVisible(true);
        formBox.setVisible(!status.equals("PENDING"));
        if (status.equals("REJECTED") && app.has("items")) refill(app);
        revalidate();
        repaint();
    }

    /** Hồ sơ bị từ chối: nạp lại nội dung cũ vào biểu mẫu để chủ quán chỉ việc sửa chỗ sai. */
    private void refill(JsonObject app) {
        if (!name.getText().isBlank()) return; // đang soạn dở thì không ghi đè
        name.setText(Json.str(app, "name", ""));
        category.setSelectedItem(Json.str(app, "category", ""));
        phone.setText(Json.str(app, "phone", ""));
        description.setText(Json.str(app, "description", ""));
        address.set(Json.dbl(app, "lat", 0), Json.dbl(app, "lng", 0), Json.str(app, "address", ""));
        shopImage.setImageId(Json.has(app, "image_id") ? app.get("image_id").getAsLong() : null);
        menu.clear();
        for (JsonElement e : app.getAsJsonArray("items")) {
            JsonObject it = e.getAsJsonObject();
            JsonObject dish = Json.obj("name", Json.str(it, "name"), "description", Json.str(it, "description", ""),
                    "price", it.get("price").getAsInt());
            if (Json.has(it, "image_id")) dish.addProperty("image_id", it.get("image_id").getAsLong());
            menu.add(dish);
        }
        renderMenu();
    }

    private void startNew() {
        wantApplyForm = true;
        setMode(APPLY);
        statusBox.setVisible(false);
        formBox.setVisible(true);
        revalidate();
        repaint();
    }

    private void withdraw() {
        if (application == null || !Ui.confirm(this, "Rút lại hồ sơ \"" + Json.str(application, "name") + "\"?")) return;
        net.run(this, "MERCHANT_WITHDRAW", Json.obj("id", Json.lng(application, "id", 0)), r -> {
            refill(application);
            showApplication(null);
        });
    }

    // ---------- Thực đơn đang soạn ----------

    private void addDish() {
        JsonObject d = dishDialog(null);
        if (d != null) {
            menu.add(d);
            renderMenu();
        }
    }

    private void editDish() {
        int i = dishes.getSelectedRow();
        if (i < 0) {
            Ui.info(this, "Chọn một món trong bảng trước");
            return;
        }
        JsonObject d = dishDialog(menu.get(i));
        if (d != null) {
            menu.set(i, d);
            renderMenu();
        }
    }

    private void removeDish() {
        int i = dishes.getSelectedRow();
        if (i < 0) {
            Ui.info(this, "Chọn một món trong bảng trước");
            return;
        }
        if (!Ui.confirm(this, "Xóa món \"" + Json.str(menu.get(i), "name") + "\" khỏi thực đơn?")) return;
        menu.remove(i);
        renderMenu();
    }

    /** Hộp thoại nhập một món: tên, mô tả, giá và ảnh riêng của món. */
    private JsonObject dishDialog(JsonObject init) {
        JTextField dName = new JTextField(Json.str(init, "name", ""), 22);
        JTextField dPrice = new JTextField(init == null ? "" : String.valueOf(Json.integer(init, "price", 0)), 22);
        JTextArea dDesc = new JTextArea(Json.str(init, "description", ""), 3, 22);
        dDesc.setLineWrap(true);
        dDesc.setWrapStyleWord(true);
        ImageField dImage = new ImageField(net, "Ảnh món ăn", 120, 90);
        if (init != null && Json.has(init, "image_id")) dImage.setImageId(init.get("image_id").getAsLong());

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.SURFACE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 4, 5, 4);
        g.anchor = GridBagConstraints.WEST;
        String[] labels = {"Tên món", "Giá (đ)", "Mô tả", "Ảnh món"};
        Component[] fields = {dName, dPrice, new JScrollPane(dDesc), dImage};
        for (int i = 0; i < labels.length; i++) {
            g.gridx = 0;
            g.gridy = i;
            g.fill = GridBagConstraints.NONE;
            p.add(new JLabel(labels[i]), g);
            g.gridx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            p.add(fields[i], g);
        }

        while (true) {
            int r = JOptionPane.showConfirmDialog(this, p, init == null ? "Thêm món" : "Sửa món",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return null;
            String n = dName.getText().trim();
            if (n.isEmpty()) {
                Ui.info(this, "Nhập tên món");
                continue;
            }
            int price;
            try {
                price = Integer.parseInt(dPrice.getText().trim().replaceAll("[.,\\s]", ""));
            } catch (NumberFormatException e) {
                Ui.info(this, "Giá món phải là số, ví dụ 45000");
                continue;
            }
            if (price <= 0) {
                Ui.info(this, "Giá món phải lớn hơn 0");
                continue;
            }
            if (dImage.busy()) {
                Ui.info(this, "Ảnh đang được tải lên, đợi một chút rồi bấm OK lại");
                continue;
            }
            JsonObject out = Json.obj("name", n, "description", dDesc.getText().trim(), "price", price);
            if (dImage.imageId() != null) out.addProperty("image_id", dImage.imageId());
            return out;
        }
    }

    private void renderMenu() {
        JsonArray rows = new JsonArray();
        int total = 0;
        for (JsonObject d : menu) {
            JsonObject row = d.deepCopy();
            row.addProperty("has_image", Json.has(d, "image_id"));
            rows.add(row);
            total += Json.integer(d, "price", 0);
        }
        dishes.setRows(rows);
        menuSummary.setText(menu.isEmpty() ? "Chưa có món nào"
                : menu.size() + " món · giá trung bình " + Ui.money((double) total / menu.size()));
    }

    // ---------- Gửi hồ sơ ----------

    private void submit() {
        if (name.getText().trim().isEmpty()) {
            Ui.info(this, "Nhập tên quán");
            return;
        }
        if (address.address().isBlank()) {
            Ui.info(this, "Nhập địa chỉ quán (bấm Tìm hoặc chọn trên bản đồ để lấy toạ độ)");
            return;
        }
        if (menu.isEmpty()) {
            Ui.info(this, "Thực đơn phải có ít nhất 1 món");
            return;
        }
        if (shopImage.busy()) {
            Ui.info(this, "Ảnh quán đang được tải lên, đợi một chút rồi gửi lại");
            return;
        }
        JsonArray items = new JsonArray();
        menu.forEach(items::add);
        JsonObject req = Json.obj("name", name.getText().trim(),
                "category", String.valueOf(category.getEditor().getItem()).trim(),
                "address", address.address(), "phone", phone.getText().trim(),
                "description", description.getText().trim(), "items", items);
        if (address.has()) {
            req.addProperty("lat", address.lat());
            req.addProperty("lng", address.lng());
        }
        if (shopImage.imageId() != null) req.addProperty("image_id", shopImage.imageId());

        submitBtn.setEnabled(false);
        net.run(this, "MERCHANT_APPLY", req, r -> {
            submitBtn.setEnabled(true);
            JsonObject app = r.getAsJsonObject();
            Toast.info(this, "📨", "Đã gửi hồ sơ", Json.str(app, "message", "Hồ sơ đang chờ Admin duyệt."), Theme.MINT);
            wantApplyForm = false;
            resetForm();
            showApplication(app);
        });
        // net.run chỉ gọi lại khi thành công -> bật lại nút sau một nhịp nếu có lỗi
        Timer t = new Timer(1500, e -> submitBtn.setEnabled(true));
        t.setRepeats(false);
        t.start();
    }

    private void resetForm() {
        name.setText("");
        phone.setText("");
        description.setText("");
        category.setSelectedIndex(0);
        shopImage.clear();
        menu.clear();
        renderMenu();
    }

    // ---------- Bản đồ ----------

    private void useMyLocation() {
        new Thread(() -> {
            double[] p = MyLocation.approximate();
            SwingUtilities.invokeLater(() -> address.setPoint(p[0], p[1]));
        }, "my-location").start();
    }

    public void drawMap() {
        if (!visible.getAsBoolean()) return;
        map.clearAll();
        map.onPick("Bấm bản đồ để chọn vị trí quán", (lat, lng) -> address.setPoint(lat, lng));
        if (address.has()) {
            map.setMarker("shop", address.lat(), address.lng(), Theme.PEACH_DEEP,
                    name.getText().isBlank() ? "Quán của bạn" : name.getText().trim());
            map.center(address.lat(), address.lng());
        }
    }
}
