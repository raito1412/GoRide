package com.goride.client.ui;

import com.goride.common.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bảng hiển thị JsonArray. Mỗi cột: "key|Tiêu đề|kiểu", kiểu: text, money, km, min, status, bool, stars, time.
 */
public class DataTable extends JTable {
    private final String[] keys, titles, kinds;
    private final List<JsonObject> rows = new ArrayList<>();

    public DataTable(String... columns) {
        keys = new String[columns.length];
        titles = new String[columns.length];
        kinds = new String[columns.length];
        for (int i = 0; i < columns.length; i++) {
            String[] p = columns[i].split("\\|");
            keys[i] = p[0];
            titles[i] = p.length > 1 ? p[1] : p[0];
            kinds[i] = p.length > 2 ? p[2] : "text";
        }
        setModel(new AbstractTableModel() {
            public int getRowCount() { return rows.size(); }
            public int getColumnCount() { return keys.length; }
            public String getColumnName(int c) { return titles[c]; }
            public Object getValueAt(int r, int c) { return format(rows.get(r), c); }
        });
        setFillsViewportHeight(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setAutoCreateRowSorter(false);
        getTableHeader().setReorderingAllowed(false);
        setDefaultRenderer(Object.class, new Renderer());
    }

    private String format(JsonObject row, int c) {
        String k = keys[c];
        if (!Json.has(row, k)) return kinds[c].equals("bool") ? "" : "—";
        JsonElement v = row.get(k);
        return switch (kinds[c]) {
            case "money" -> Ui.money(v.getAsDouble());
            case "km" -> Ui.km(v.getAsDouble());
            case "min" -> Ui.minutes(v.getAsDouble());
            case "status" -> Ui.vi(v.getAsString());
            case "bool" -> Json.bool(row, k, false) ? "✔" : "—";
            case "stars" -> Ui.stars((int) Math.round(v.getAsDouble()));
            case "rating" -> String.format("%.1f ★", v.getAsDouble());
            case "time" -> v.getAsString().length() > 16 ? v.getAsString().substring(0, 16) : v.getAsString();
            default -> v.getAsString();
        };
    }

    public void setRows(JsonElement data) {
        JsonObject keep = selected();
        rows.clear();
        if (data != null && data.isJsonArray()) {
            for (JsonElement e : data.getAsJsonArray()) rows.add(e.getAsJsonObject());
        }
        ((AbstractTableModel) getModel()).fireTableDataChanged();
        if (keep != null && Json.has(keep, "id")) {
            for (int i = 0; i < rows.size(); i++) {
                if (Json.has(rows.get(i), "id") && rows.get(i).get("id").equals(keep.get("id"))) {
                    setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    public JsonArray rows() {
        JsonArray a = new JsonArray();
        rows.forEach(a::add);
        return a;
    }

    /** Dòng đang chọn hoặc null. */
    public JsonObject selected() {
        int r = getSelectedRow();
        return r < 0 || r >= rows.size() ? null : rows.get(r);
    }

    public void onSelect(Consumer<JsonObject> handler) {
        getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) handler.accept(selected());
        });
    }

    public void onDoubleClick(Consumer<JsonObject> handler) {
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && selected() != null) handler.accept(selected());
            }
        });
    }

    public void widths(int... w) {
        for (int i = 0; i < w.length && i < getColumnCount(); i++) getColumnModel().getColumn(i).setPreferredWidth(w[i]);
    }

    private class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, sel, false, row, col);
            l.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            String kind = kinds[col];
            l.setHorizontalAlignment(kind.equals("money") || kind.equals("km") ? RIGHT : kind.equals("bool") ? CENTER : LEFT);
            if (!sel) {
                l.setBackground(row % 2 == 0 ? Theme.SURFACE : Theme.ROW_ALT);
                l.setForeground(Theme.INK);
                if (kind.equals("status")) {
                    JsonObject r = rows.get(row);
                    l.setBackground(Theme.statusColor(Json.str(r, keys[col])));
                } else if (kind.equals("stars")) {
                    l.setForeground(Theme.PEACH_DEEP);
                }
            }
            return l;
        }
    }

    /** Bảng kèm khung cuộn. */
    public JScrollPane scroll() { return Ui.scroll(this); }
}
