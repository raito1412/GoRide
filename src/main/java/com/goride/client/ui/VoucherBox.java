package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;

/** Ô chọn thẻ giảm giá, dùng chung cho đặt xe và đặt đồ ăn. Mục đầu tiên là "không dùng thẻ". */
public class VoucherBox extends JComboBox<JsonObject> {
    public VoucherBox() {
        setMaximumRowCount(6);
        setPreferredSize(new Dimension(280, 40));
        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int i, boolean sel, boolean focus) {
                JsonObject v = (JsonObject) value;
                String text;
                if (v == null) {
                    text = "<html><span style='color:#5A6472'>Không dùng thẻ giảm giá</span></html>";
                } else {
                    long hours = Math.max(1, Json.lng(v, "hours_left", 0));
                    String left = hours >= 48 ? "còn " + (hours / 24) + " ngày" : "còn " + hours + " giờ";
                    text = "<html><b style='color:#B8480A'>Giảm " + Json.integer(v, "percent", 0) + "%</b>"
                            + "&nbsp;&nbsp;<span style='color:#121619'>tối đa " + Ui.money(Json.dbl(v, "max_discount", 0)) + "</span>"
                            + "&nbsp;&nbsp;<span style='color:#5A6472'>· " + left + "</span></html>";
                }
                JLabel l = (JLabel) super.getListCellRendererComponent(list, text, i, sel, focus);
                l.setIcon(Icons.get("ticket", 16, v == null ? Theme.MUTED : Theme.PEACH_DEEP));
                l.setIconTextGap(9);
                l.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                return l;
            }
        });
        addItem(null);
    }

    /** Tải lại ví, giữ nguyên thẻ đang chọn nếu còn dùng được. */
    public void reload(Net net, Runnable after) {
        net.quiet("VOUCHER_LIST", null, r -> {
            JsonObject keep = selected();
            removeAllItems();
            addItem(null);
            for (JsonElement e : r.getAsJsonArray()) {
                JsonObject v = e.getAsJsonObject();
                addItem(v);
                if (keep != null && Json.str(keep, "code", "").equals(Json.str(v, "code"))) setSelectedItem(v);
            }
            if (after != null) after.run();
        });
    }

    public JsonObject selected() { return (JsonObject) getSelectedItem(); }

    public String code() {
        JsonObject v = selected();
        return v == null ? null : Json.str(v, "code");
    }

    /** Tiền giảm tạm tính ở client để hiện ngay; server vẫn là nơi chốt con số cuối. */
    public int preview(int gross) {
        JsonObject v = selected();
        if (v == null) return 0;
        return Math.min((int) Math.round(gross * Json.integer(v, "percent", 0) / 100.0),
                Json.integer(v, "max_discount", 0));
    }
}
