package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Json;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Xem / sửa hồ sơ (dùng chung). Với tài xế có thêm khối thông tin xe. */
public class ProfilePanel extends JPanel {
    private final Net net;
    private final boolean driver;
    private final JPanel info = new JPanel(new GridBagLayout());
    private final JPanel vehicle = new JPanel(new GridBagLayout());
    private JsonObject profile;
    private Consumer<JsonObject> onUpdated = p -> { };

    public ProfilePanel(Net net) {
        super(new BorderLayout());
        this.net = net;
        this.driver = "DRIVER".equals(Json.str(net.me, "role"));
        JPanel col = Ui.column();
        col.setOpaque(false);

        col.add(Ui.section("Thông tin cá nhân"));
        col.add(card(info));
        col.add(Ui.row(Ui.button("Sửa hồ sơ", Theme.LAVENDER, this::editProfile)));

        if (driver) {
            col.add(Ui.section("Thông tin xe"));
            col.add(card(vehicle));
            col.add(Ui.row(Ui.button("Sửa thông tin xe", Theme.MINT, this::editVehicle)));
            col.add(Ui.muted("Đổi loại xe hoặc biển số sẽ cần Admin duyệt lại."));
        }
        add(BaseFrame.vscroll(col));
        render(net.me);
    }

    public void onUpdated(Consumer<JsonObject> c) { onUpdated = c; }

    public void reload() {
        net.quiet("PROFILE_GET", null, r -> render(r.getAsJsonObject()));
    }

    private void render(JsonObject p) {
        profile = p;
        fill(info,
                "Họ tên", Json.str(p, "full_name", ""),
                "Số điện thoại", Json.str(p, "phone", ""),
                "Email", Json.str(p, "email", "—"),
                "Địa chỉ", Json.str(p, "address", "—"),
                "Vai trò", Ui.vi(Json.str(p, "role")),
                "Trạng thái", Ui.vi(Json.str(p, "status")),
                "Ngày tạo", Json.str(p, "created_at", ""));
        if (driver) {
            fill(vehicle,
                    "Loại xe", Ui.vi(Json.str(p, "vehicle_type")),
                    "Biển số", Json.str(p, "plate_number", ""),
                    "Hãng xe", Json.str(p, "vehicle_brand", "—"),
                    "Màu xe", Json.str(p, "vehicle_color", "—"),
                    "Số GPLX", Json.str(p, "license_number", "—"),
                    "Duyệt", Ui.vi(Json.str(p, "approval")),
                    "Đánh giá", String.format("%.1f ★ (%d lượt)", Json.dbl(p, "rating_avg", 0), Json.integer(p, "rating_count", 0)));
        }
        revalidate();
        repaint();
    }

    private void editProfile() {
        JsonObject d = Ui.form(this, "Sửa hồ sơ", profile,
                "full_name|Họ tên", "email|Email", "address|Địa chỉ|area", "new_password|Mật khẩu mới (để trống nếu không đổi)|pass");
        if (d == null) return;
        net.run(this, "PROFILE_UPDATE", d, r -> {
            JsonObject p = r.getAsJsonObject();
            mergeInto(p);
            render(p);
            onUpdated.accept(p);
            Ui.info(this, "Đã cập nhật hồ sơ");
        });
    }

    private void editVehicle() {
        JsonObject d = Ui.form(this, "Thông tin xe", profile,
                "vehicle_type|Loại xe|enum:BIKE,CAR", "plate_number|Biển số", "vehicle_brand|Hãng xe",
                "vehicle_color|Màu xe", "license_number|Số GPLX");
        if (d == null) return;
        net.run(this, "DRIVER_VEHICLE_UPDATE", d, r -> {
            JsonObject p = r.getAsJsonObject();
            mergeInto(p);
            render(p);
            onUpdated.accept(p);
            Ui.info(this, Json.str(p, "message", "Đã lưu"));
        });
    }

    /** Giữ lại udp_token... của phiên hiện tại. */
    private void mergeInto(JsonObject p) {
        for (String k : new String[]{"udp_token", "udp_port"}) {
            if (net.me.has(k) && !p.has(k)) p.add(k, net.me.get(k));
        }
        net.me = p;
    }

    private static JPanel card(JPanel grid) {
        grid.setOpaque(false);
        Ui.Card c = new Ui.Card(new BorderLayout(), Theme.SURFACE);
        c.add(grid);
        return c;
    }

    private static void fill(JPanel grid, String... kv) {
        grid.removeAll();
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < kv.length; i += 2) {
            g.gridy = i / 2;
            g.gridx = 0;
            g.weightx = 0;
            g.insets = new Insets(4, 0, 4, 18);
            grid.add(Ui.muted(kv[i]), g);
            g.gridx = 1;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            JLabel v = Ui.label(kv[i + 1] == null ? "—" : kv[i + 1], 13f, Font.PLAIN);
            grid.add(v, g);
            g.fill = GridBagConstraints.NONE;
        }
    }
}
