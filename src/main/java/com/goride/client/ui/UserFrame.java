package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Config;
import com.goride.common.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.function.IntConsumer;

/**
 * Giao diện Khách hàng. Mở lên là màn hình chính gồm các nút lớn; bấm nút nào mới vào màn hình đó.
 * Bản đồ chỉ hiện ở những màn hình thật sự cần (đặt xe, chọn địa chỉ giao, theo dõi đơn).
 */
public class UserFrame extends BaseFrame {
    private static final String HOME = "home", RIDE = "ride", FOOD = "food", MERCHANT = "merchant",
            HISTORY = "history", PROFILE = "profile";

    private final MapPanel map = new MapPanel(Config.defaultLat(), Config.defaultLng());
    private final CardLayout cards = new CardLayout();
    private final JPanel screens = new JPanel(cards);
    private String current = HOME;

    private final RidePanel ride;
    private final FoodPanel food;
    private final MerchantPanel merchant;
    private final ProfilePanel profile;

    private final JLabel homeBanner = new JLabel();
    private final Ui.Card homeBannerCard;
    private final JTabbedPane historyTabs = newTabs();
    private final DataTable rides = new DataTable("id|#", "created_at|Thời gian|time", "vehicle_type|Xe|status",
            "drop_address|Điểm đến", "price|Giá|money", "status|Trạng thái|status", "my_stars|Đánh giá|stars");
    private final DataTable orders = new DataTable("id|#", "created_at|Thời gian|time", "restaurant_name|Nhà hàng",
            "total|Tổng|money", "status|Trạng thái|status", "my_stars|Sao tài xế|stars",
            "my_shop_stars|Sao quán|stars");
    private final DataTable payments = new DataTable("id|#", "created_at|Thời gian|time", "ref_type|Loại|status",
            "ref_id|Mã", "amount|Số tiền|money", "method|Hình thức|status", "status|Trạng thái|status");
    private final JLabel paySummary = Ui.muted(" ");

    public UserFrame(Net net) {
        super(net, "Khách hàng", Theme.LAVENDER, Theme.LAVENDER_DEEP);
        ride = new RidePanel(net, map, () -> current.equals(RIDE));
        food = new FoodPanel(net, map, this::foodStepChanged);
        merchant = new MerchantPanel(net, map, () -> current.equals(MERCHANT), this::merchantLayoutChanged);
        profile = new ProfilePanel(net);
        profile.onUpdated(p -> nameLabel.setText(Json.str(p, "full_name", "")));

        homeBannerCard = new Ui.Card(new BorderLayout(), Theme.BUTTER);
        homeBannerCard.add(homeBanner);
        homeBannerCard.setVisible(false);

        screens.setOpaque(false);
        screens.add(buildHome(), HOME);
        screens.add(ride, RIDE);
        screens.add(food, FOOD);
        screens.add(merchant, MERCHANT);
        screens.add(buildHistory(), HISTORY);
        screens.add(profile, PROFILE);
        setBody(screens);

        addHeaderAction("🕘 Lịch sử", () -> go(HISTORY));
        addHeaderAction("👤 Tài khoản", () -> go(PROFILE));

        net.on("RIDE_UPDATE", d -> {
            loadHistory();
            refreshBanner();
        });
        net.on("FOOD_UPDATE", d -> {
            loadHistory();
            refreshBanner();
        });
        loadHistory();
        go(HOME);
        // tài khoản đang có quán thì mở thẳng màn quản lý quán (có thể bấm Quay lại để đặt món như khách)
        net.quiet("MERCHANT_SHOPS", null, r -> {
            if (r.isJsonArray() && r.getAsJsonArray().size() > 0) go(MERCHANT);
        });
    }

    // ---------- Điều hướng ----------

    private void go(String name) {
        current = name;
        cards.show(screens, name);
        attachMap(mapSlotFor(name));
        switch (name) {
            case HOME -> {
                setNav("Trang chủ", null);
                refreshBanner();
            }
            case RIDE -> {
                setNav("Đặt xe", () -> go(HOME));
                ride.drawMap(true);
            }
            case FOOD -> {
                food.reset();
                foodStepChanged();
            }
            case MERCHANT -> {
                merchant.loadMine();
                merchantLayoutChanged();
                merchant.drawMap();
            }
            case HISTORY -> {
                setNav("Lịch sử & thanh toán", () -> go(HOME));
                loadHistory();
                loadPayments();
            }
            case PROFILE -> {
                setNav("Tài khoản", () -> go(HOME));
                profile.reload();
            }
            default -> setNav("", () -> go(HOME));
        }
    }

    /** FoodPanel đổi bước -> cập nhật tiêu đề, nút quay lại và vị trí bản đồ. */
    private void foodStepChanged() {
        if (!current.equals(FOOD)) return;
        Runnable back = food.back();
        setNav(food.stepTitle(), back != null ? back : () -> go(HOME));
        attachMap(food.mapSlot());
    }

    /** MerchantPanel đổi giữa "theo dõi quán" và "điền hồ sơ" -> chỉ chế độ hồ sơ mới cần bản đồ. */
    private void merchantLayoutChanged() {
        if (!current.equals(MERCHANT)) return;
        setNav(merchant.title(), () -> go(HOME));
        attachMap(merchant.mapSlot());
    }

    private JPanel mapSlotFor(String name) {
        return switch (name) {
            case RIDE -> ride.mapSlot();
            case FOOD -> food.mapSlot();
            case MERCHANT -> merchant.mapSlot();
            default -> null;
        };
    }

    /** Chỉ có một bản đồ duy nhất, chuyển qua lại giữa các màn hình cần dùng. */
    private void attachMap(JPanel slot) {
        Container old = map.getParent();
        if (old == slot) return;
        if (old != null) {
            old.remove(map);
            old.revalidate();
            old.repaint();
        }
        if (slot != null) {
            slot.add(map, BorderLayout.CENTER);
            slot.revalidate();
            slot.repaint();
        } else {
            map.clearAll();
            map.onPick(null, null);
        }
    }

    // ---------- Màn hình chính ----------

    private JPanel buildHome() {
        JPanel col = Ui.column();
        col.setOpaque(false);
        col.setBorder(BorderFactory.createEmptyBorder(18, 22, 22, 22));

        JLabel hello = Ui.label("Xin chào, " + Json.str(net.me, "full_name", "") + "", 24f, Font.BOLD);
        col.add(hello);
        col.add(Ui.muted("Bạn muốn làm gì hôm nay?"));
        col.add(Box.createVerticalStrut(12));
        col.add(homeBannerCard);
        col.add(Box.createVerticalStrut(12));

        JPanel main = Ui.grid(2);
        main.add(Ui.tile("🛵", "Đặt xe", "Chọn điểm đón, điểm đến, xem giá rồi gọi tài xế", Theme.LAVENDER, () -> go(RIDE)));
        main.add(Ui.tile("🍜", "Đặt đồ ăn", "Chọn quán, chọn món, giao tận nơi — có sẵn thẻ giảm giá", Theme.PEACH, () -> go(FOOD)));
        col.add(main);
        col.add(Box.createVerticalStrut(12));
        col.add(Ui.tile("🏪", "Mở quán ăn", "Đăng ký bán đồ ăn trên GoRide, chờ Admin duyệt", Theme.MINT, () -> go(MERCHANT)));

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(BaseFrame.vscroll(col));
        return p;
    }

    /** Nhắc khách nếu đang có chuyến/đơn chưa xong. */
    private void refreshBanner() {
        net.quiet("RIDE_CURRENT", null, r -> {
            if (r.isJsonObject() && r.getAsJsonObject().has("id")) {
                JsonObject o = r.getAsJsonObject();
                showBanner("🛵 Chuyến #" + Json.lng(o, "id", 0) + " — " + Ui.vi(Json.str(o, "status"))
                        + ". Bấm để xem chi tiết.", () -> go(RIDE));
            } else {
                net.quiet("FOOD_CURRENT", null, f -> {
                    if (f.isJsonObject() && f.getAsJsonObject().has("id")) {
                        JsonObject o = f.getAsJsonObject();
                        showBanner("🍜 Đơn #" + Json.lng(o, "id", 0) + " từ " + Json.str(o, "restaurant_name", "")
                                + " — " + Ui.vi(Json.str(o, "status")) + ". Bấm để theo dõi.", () -> go(FOOD));
                    } else {
                        homeBannerCard.setVisible(false);
                    }
                });
            }
        });
    }

    private boolean bannerBound;

    private void showBanner(String text, Runnable action) {
        String[] p = Icons.split(text);
        homeBanner.setText("<html><b>" + Ui.esc(p[1]) + "</b></html>");
        homeBanner.setIcon(Icons.get(p[0], 18, Theme.BUTTER_DEEP));
        homeBanner.setIconTextGap(10);
        homeBannerCard.setVisible(true);
        if (!bannerBound) {
            bannerBound = true;
            homeBannerCard.onClick(() -> {
                if (bannerAction != null) bannerAction.run();
            });
        }
        bannerAction = action;
    }

    private Runnable bannerAction;

    // ---------- Lịch sử ----------

    private JPanel buildHistory() {
        rides.widths(40, 110, 60, 220, 90, 120, 90);
        orders.widths(40, 110, 200, 90, 120, 90, 90);
        orders.onDoubleClick(this::showOrderDetail);

        historyTabs.addTab("🛵 Chuyến xe", block("Chuyến xe đã đặt", rides,
                Ui.button("Đánh giá chuyến", Theme.BUTTER, () -> reviewSelected(rides, "RIDE", "COMPLETED"))));
        historyTabs.addTab("🍜 Đơn đồ ăn", block("Đơn đồ ăn đã đặt", orders,
                Ui.button("Xem chi tiết", Theme.SURFACE, () -> showOrderDetail(orders.selected())),
                Ui.button("Đánh giá tài xế", Theme.BUTTER, () -> reviewSelected(orders, "FOOD", "DELIVERED")),
                Ui.button("Đánh giá quán", Theme.PEACH, this::reviewShopSelected)));
        historyTabs.addTab("💳 Thanh toán", buildPayments());

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        p.add(historyTabs);
        return p;
    }

    private JPanel block(String title, DataTable table, JButton... actions) {
        JPanel b = new JPanel(new BorderLayout(0, 6));
        b.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel head = new JPanel(new BorderLayout());
        head.add(Ui.label(title, 14f, Font.BOLD), BorderLayout.WEST);
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
            int discount = Json.integer(o, "discount", 0);
            s.append("<br>Tiền món: ").append(Ui.money(o, "items_total"))
                    .append("<br>Phí giao: ").append(Ui.money(o, "delivery_fee"));
            if (discount > 0) s.append("<br>Giảm giá (").append(Json.str(o, "voucher_code", "")).append("): -").append(Ui.money(discount));
            s.append("<br><b>Tổng: ").append(Ui.money(o, "total")).append("</b> (").append(Ui.vi(Json.str(o, "payment_method"))).append(")")
                    .append("<br>Giao tới: ").append(Ui.esc(Json.str(o, "delivery_address")))
                    .append("<br>Tài xế: ").append(Ui.esc(Json.str(o, "driver_name", "—")))
                    .append("<br>Trạng thái: ").append(Ui.vi(Json.str(o, "status"))).append("</html>");
            Ui.info(this, s.toString());
        });
    }

    // ---------- Thanh toán ----------

    private JPanel buildPayments() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel head = new JPanel(new BorderLayout());
        head.add(Ui.label("Các khoản đã thanh toán", 14f, Font.BOLD), BorderLayout.WEST);
        head.add(Ui.button("↻ Làm mới", Theme.SURFACE, this::loadPayments), BorderLayout.EAST);
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

    /** Đánh giá quán ăn của một đơn đã giao (tách riêng với đánh giá tài xế). */
    private void reviewShopSelected() {
        JsonObject r = orders.selected();
        if (r == null) {
            Ui.info(this, "Chọn một đơn trong bảng");
            return;
        }
        if (!"DELIVERED".equals(Json.str(r, "status"))) {
            Ui.info(this, "Chỉ đánh giá quán được khi đơn đã giao xong");
            return;
        }
        if (Json.has(r, "my_shop_stars")) {
            Ui.info(this, "Bạn đã đánh giá quán của đơn này rồi");
            return;
        }
        review(this, net, "FOOD", Json.lng(r, "id", -1), "RESTAURANT", s -> loadHistory());
    }

    static void review(Component parent, Net net, String refType, long refId, IntConsumer done) {
        review(parent, net, refType, refId, "DRIVER", done);
    }

    /** target = DRIVER đánh giá tài xế, RESTAURANT đánh giá quán. */
    static void review(Component parent, Net net, String refType, long refId, String target, IntConsumer done) {
        JComboBox<String> stars = new JComboBox<>(new String[]{"★★★★★  Tuyệt vời", "★★★★☆  Tốt", "★★★☆☆  Bình thường", "★★☆☆☆  Chưa tốt", "★☆☆☆☆  Tệ"});
        JTextArea comment = new JTextArea(3, 26);
        comment.setLineWrap(true);
        comment.setWrapStyleWord(true);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(stars, BorderLayout.NORTH);
        p.add(new JScrollPane(comment));
        String title = ("RESTAURANT".equals(target) ? "Đánh giá quán · " : "Đánh giá tài xế · ")
                + ("RIDE".equals(refType) ? "chuyến #" : "đơn #") + refId;
        if (JOptionPane.showConfirmDialog(parent, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        int n = 5 - stars.getSelectedIndex();
        net.run(parent, "REVIEW_CREATE", Json.obj("ref_type", refType, "ref_id", refId, "stars", n,
                "comment", comment.getText().trim(), "target", target), r -> {
            Ui.info(parent, Json.str(r.getAsJsonObject(), "message", "Đã gửi đánh giá"));
            done.accept(n);
        });
    }
}
