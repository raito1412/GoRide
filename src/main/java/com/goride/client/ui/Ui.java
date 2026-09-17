package com.goride.client.ui;

import com.goride.common.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Các hàm dựng giao diện dùng lại nhiều nơi. */
public final class Ui {
    private static final NumberFormat VND = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
    private static final Map<String, String> VI = new LinkedHashMap<>();

    static {
        String[][] t = {
                {"SEARCHING", "Đang tìm tài xế"}, {"ACCEPTED", "Tài xế đã nhận"}, {"ARRIVED", "Tài xế đã tới"},
                {"PICKED_UP", "Đã đón / lấy món"}, {"COMPLETED", "Hoàn thành"}, {"CANCELLED", "Đã hủy"},
                {"PLACED", "Chờ nhà hàng"}, {"CONFIRMED", "Đang tìm tài xế"}, {"AT_RESTAURANT", "Tài xế ở nhà hàng"},
                {"DELIVERING", "Đang giao"}, {"DELIVERED", "Đã giao"},
                {"PENDING", "Chờ duyệt"}, {"APPROVED", "Đã duyệt"}, {"REJECTED", "Từ chối"},
                {"ACTIVE", "Hoạt động"}, {"LOCKED", "Đã khóa"}, {"PAID", "Đã thanh toán"}, {"REFUNDED", "Đã hoàn tiền"},
                {"BIKE", "Xe máy"}, {"CAR", "Ô tô"}, {"DELIVERY", "Phí giao đồ ăn"},
                {"CASH", "Tiền mặt"}, {"WALLET", "Ví GoRide"}, {"RIDE", "Chuyến xe"}, {"FOOD", "Đồ ăn"},
                {"USER", "Khách hàng"}, {"DRIVER", "Tài xế"}, {"ADMIN", "Quản trị"},
        };
        for (String[] r : t) VI.put(r[0], r[1]);
    }

    private Ui() {}

    public static String vi(String code) { return code == null ? "" : VI.getOrDefault(code, code); }

    public static String money(double v) { return VND.format(Math.round(v)) + "đ"; }

    public static String money(JsonObject o, String k) { return Json.has(o, k) ? money(o.get(k).getAsDouble()) : ""; }

    public static String km(double meters) { return String.format(Locale.US, "%.1f km", meters / 1000.0); }

    public static String minutes(double seconds) { return Math.max(1, Math.round(seconds / 60.0)) + " phút"; }

    public static String stars(int n) { return "★".repeat(Math.max(0, n)) + "☆".repeat(Math.max(0, 5 - n)); }

    public static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------- Thành phần ----------

    public static JButton button(String text, Color bg, Runnable action) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Theme.INK);
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    public static JLabel label(String text, float size, int style) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(style, size));
        l.setForeground(Theme.INK);
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.MUTED);
        return l;
    }

    /** Tiêu đề nhỏ cho từng khối trong cột trái. */
    public static JLabel section(String text) {
        JLabel l = label(text, 13f, Font.BOLD);
        l.setBorder(new EmptyBorder(14, 0, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    public static JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        s.getViewport().setBackground(Theme.SURFACE);
        return s;
    }

    /** Cột dọc căn trái, các con giãn hết chiều ngang. */
    public static JPanel column() {
        JPanel p = new JPanel() {
            @Override
            public Component add(Component comp) {
                if (comp instanceof JComponent jc) jc.setAlignmentX(LEFT_ALIGNMENT);
                return super.add(comp);
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(8, 16, 16, 16));
        return p;
    }

    public static JPanel row(Component... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        p.setOpaque(false);
        for (Component c : items) p.add(c);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    public static JPanel grow(Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return p;
    }

    public static JComboBox<String> codes(String... codes) {
        JComboBox<String> box = new JComboBox<>(codes);
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(list, vi((String) value), index, sel, focus);
            }
        });
        return box;
    }

    /** Khối nền trắng bo góc. */
    public static class Card extends JPanel {
        private final Color fill;

        public Card(LayoutManager layout, Color fill) {
            super(layout);
            this.fill = fill;
            setOpaque(false);
            setBorder(new EmptyBorder(14, 16, 14, 16));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.setColor(Theme.LINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Nhãn trạng thái dạng viên thuốc. */
    public static class Pill extends JLabel {
        public Pill() {
            setBorder(new EmptyBorder(4, 12, 4, 12));
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setForeground(Theme.INK);
        }

        public void setStatus(String code) {
            setText(vi(code));
            setBackground(Theme.statusColor(code));
            setVisible(code != null);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------- Hộp thoại ----------

    public static void info(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "GoRide", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String msg) {
        return JOptionPane.showConfirmDialog(parent, msg, "GoRide", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    /**
     * Form nhập nhanh. Mỗi spec dạng "key|Nhãn|kiểu", kiểu: text (mặc định), int, num, pass, area, bool, enum:A,B
     * Trả về null nếu bấm Hủy.
     */
    public static JsonObject form(Component parent, String title, JsonObject init, String... specs) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Theme.SURFACE);
        Map<String, JComponent> inputs = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 4, 5, 4);
        g.anchor = GridBagConstraints.WEST;
        int row = 0;
        for (String spec : specs) {
            String[] p = spec.split("\\|");
            String key = p[0], label = p[1], type = p.length > 2 ? p[2] : "text";
            String value = Json.has(init, key) ? init.get(key).getAsString() : "";
            JComponent input;
            JComponent view;
            if (type.equals("bool")) {
                input = new JCheckBox("", Json.bool(init, key, true));
                view = input;
            } else if (type.equals("pass")) {
                input = new JPasswordField(24);
                view = input;
            } else if (type.startsWith("enum:")) {
                JComboBox<String> box = codes(type.substring(5).split(","));
                if (!value.isEmpty()) box.setSelectedItem(value);
                input = box;
                view = box;
            } else if (type.equals("area")) {
                JTextArea area = new JTextArea(value, 3, 24);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                input = area;
                view = new JScrollPane(area);
            } else {
                if (type.equals("int") && value.endsWith(".0")) value = value.substring(0, value.length() - 2);
                input = new JTextField(value, 24);
                view = input;
            }
            inputs.put(key, input);
            types.put(key, type);
            labels.put(key, label);
            g.gridx = 0;
            g.gridy = row;
            g.fill = GridBagConstraints.NONE;
            panel.add(new JLabel(label), g);
            g.gridx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            panel.add(view, g);
            row++;
        }
        while (true) {
            int r = JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return null;
            JsonObject out = new JsonObject();
            String bad = null;
            for (Map.Entry<String, JComponent> e : inputs.entrySet()) {
                String key = e.getKey(), type = types.get(key);
                JComponent c = e.getValue();
                if (c instanceof JCheckBox cb) out.addProperty(key, cb.isSelected());
                else if (c instanceof JComboBox<?> box) out.addProperty(key, (String) box.getSelectedItem());
                else if (c instanceof JPasswordField pf) out.addProperty(key, new String(pf.getPassword()));
                else {
                    String text = ((javax.swing.text.JTextComponent) c).getText().trim();
                    try {
                        if (type.equals("int")) out.addProperty(key, text.isEmpty() ? 0 : Integer.parseInt(text.replaceAll("[.,\\s]", "")));
                        else if (type.equals("num")) out.addProperty(key, text.isEmpty() ? 0 : Double.parseDouble(text.replace(',', '.')));
                        else out.addProperty(key, text);
                    } catch (NumberFormatException ex) {
                        bad = labels.get(key);
                        break;
                    }
                }
            }
            if (bad == null) return out;
            JOptionPane.showMessageDialog(parent, "\"" + bad + "\" phải là số", title, JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Lấy giá trị số trong JsonElement an toàn. */
    public static double num(JsonElement e) {
        return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
    }

    /** Ô thống kê: tiêu đề nhỏ + giá trị lớn. */
    public static Card stat(String title, JLabel value, Color bg) {
        Card c = new Card(new BorderLayout(0, 4), bg);
        c.add(muted(title), BorderLayout.NORTH);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 22f));
        value.setForeground(Theme.INK);
        c.add(value);
        return c;
    }
}
