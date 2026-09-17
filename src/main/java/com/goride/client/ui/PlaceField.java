package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Ô nhập địa chỉ: gõ từ khóa -> Mapbox Geocoding (qua server) -> chọn -> lấy toạ độ.
 * Hoặc đặt toạ độ từ bản đồ / vị trí hiện tại, địa chỉ lấy bằng reverse geocode.
 */
public class PlaceField extends JPanel {
    private final Net net;
    private final JTextField text = new JTextField();
    private final Supplier<double[]> bias;
    private double lat = Double.NaN, lng = Double.NaN;
    private Runnable onChange = () -> { };

    public PlaceField(Net net, String placeholder, Color dot, Supplier<double[]> bias) {
        super(new BorderLayout(6, 0));
        this.net = net;
        this.bias = bias;
        setOpaque(false);
        JLabel mark = new JLabel("●");
        mark.setForeground(dot);
        text.putClientProperty("JTextField.placeholderText", placeholder);
        text.addActionListener(e -> search());
        text.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { edited(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { edited(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        add(mark, BorderLayout.WEST);
        add(text);
        add(Ui.button("Tìm", Theme.SKY, this::search), BorderLayout.EAST);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }

    private boolean setting;

    /** Người dùng gõ tay -> toạ độ cũ không còn đúng. */
    private void edited() {
        if (setting || !has()) return;
        lat = Double.NaN;
        lng = Double.NaN;
        onChange.run();
    }

    private void search() {
        String q = text.getText().trim();
        if (q.length() < 2) {
            Ui.info(this, "Nhập ít nhất 2 ký tự để tìm địa chỉ");
            return;
        }
        JsonObject req = Json.obj("input", q);
        double[] b = bias == null ? null : bias.get();
        if (b != null) {
            req.addProperty("lat", b[0]);
            req.addProperty("lng", b[1]);
        }
        net.run(this, "GEO_SEARCH", req, res -> {
            JPopupMenu menu = new JPopupMenu();
            if (res.getAsJsonArray().isEmpty()) menu.add(new JMenuItem("Không tìm thấy kết quả")).setEnabled(false);
            for (JsonElement e : res.getAsJsonArray()) {
                JsonObject p = e.getAsJsonObject();
                JMenuItem item = new JMenuItem(Json.str(p, "description"));
                item.addActionListener(a -> net.run(this, "GEO_DETAIL", Json.obj("place_id", Json.str(p, "place_id")), d -> {
                    JsonObject o = d.getAsJsonObject();
                    set(o.get("lat").getAsDouble(), o.get("lng").getAsDouble(), Json.str(o, "address"));
                }));
                menu.add(item);
            }
            menu.show(text, 0, text.getHeight());
        });
    }

    /** Đặt toạ độ rồi hỏi server địa chỉ tương ứng. */
    public void setPoint(double lat, double lng) {
        set(lat, lng, String.format(Locale.US, "%.5f, %.5f", lat, lng));
        net.quiet("GEO_REVERSE", Json.obj("lat", lat, "lng", lng), res -> {
            if (this.lat == lat && this.lng == lng) setText(Json.str(res.getAsJsonObject(), "address", text.getText()));
        });
    }

    public void set(double lat, double lng, String address) {
        this.lat = lat;
        this.lng = lng;
        setText(address);
        onChange.run();
    }

    private void setText(String s) {
        setting = true;
        text.setText(s);
        text.setCaretPosition(0);
        setting = false;
    }

    public void clear() {
        lat = Double.NaN;
        lng = Double.NaN;
        setText("");
        onChange.run();
    }

    public boolean has() { return !Double.isNaN(lat); }
    public double lat() { return lat; }
    public double lng() { return lng; }
    public String address() { return text.getText().trim(); }
    public void onChange(Runnable r) { onChange = r; }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (Component c : getComponents()) c.setEnabled(enabled);
    }
}
