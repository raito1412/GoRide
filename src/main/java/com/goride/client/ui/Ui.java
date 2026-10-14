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
                {"USER", "Khách hàng"}, {"DRIVER", "Tài xế"}, {"ADMIN", "Quản trị"}, {"RESTAURANT", "Quán ăn"},
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

    /**
     * Nút vuông. Truyền màu nhạt (Theme.MINT, ROSE...) thì nút được tô bằng bản đậm, chữ trắng;
     * truyền Theme.SURFACE thì thành nút viền (nền trắng, viền xám đậm). Đổi màu về sau bằng
     * setBackground cũng được quy đổi y như vậy.
     */
    public static JButton button(String text, Color bg, Runnable action) {
        JButton b = new SolidButton(text);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    private static final class SolidButton extends JButton {
        SolidButton(String text) { super(text); }

        @Override
        public void setBackground(Color bg) {
            Color solid = bg == null ? null : Theme.deep(bg);
            if (solid != null) {
                super.setBackground(solid);
                setForeground(Color.WHITE);
                setBorder(new EmptyBorder(9, 16, 9, 16));
            } else {
                super.setBackground(bg);
                setForeground(Theme.INK);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.MUTED), new EmptyBorder(8, 15, 8, 15)));
            }
        }
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

    /** Chỉ dùng trong các cột trên nền chung, nên tự vẽ nền Theme.BG cho gọn việc vẽ lại. */
    public static JPanel grow(Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.BG);
        p.add(c);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return p;
    }

    /**
     * Ô chỉ nhận chữ số, tối đa maxLen ký tự. Dán hay bộ gõ có thả chữ vào thì cũng bị lọc sạch,
     * nên số điện thoại / mã OTP không bao giờ lẫn chữ của ô khác.
     */
    public static void digitsOnly(JTextField field, int maxLen) {
        ((javax.swing.text.AbstractDocument) field.getDocument()).setDocumentFilter(
                new javax.swing.text.DocumentFilter() {
                    @Override
                    public void insertString(FilterBypass fb, int off, String text, javax.swing.text.AttributeSet a)
                            throws javax.swing.text.BadLocationException {
                        replace(fb, off, 0, text, a);
                    }

                    @Override
                    public void replace(FilterBypass fb, int off, int len, String text, javax.swing.text.AttributeSet a)
                            throws javax.swing.text.BadLocationException {
                        String digits = text == null ? "" : text.replaceAll("\\D", "");
                        int room = maxLen - (fb.getDocument().getLength() - len);
                        if (digits.length() > room) digits = digits.substring(0, Math.max(0, room));
                        fb.replace(off, len, digits, a);
                    }
                });
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

    /**
     * Khối vuông viền mảnh. Màu nền trắng/xám giữ nguyên; truyền màu nhấn (MINT, PEACH...) thì thẻ vẫn
     * nền trắng nhưng có vạch màu đậm 4px ở mép trái để nhận diện, thay cho cả mảng màu pastel.
     */
    public static class Card extends JPanel {
        private final Color fill;
        private boolean hover;

        public Card(LayoutManager layout, Color fill) {
            super(layout);
            this.fill = fill;
            setOpaque(false);
            setBorder(new EmptyBorder(14, Theme.deep(fill) != null ? 20 : 16, 14, 16));
        }

        /** Biến cả thẻ thành một nút bấm (kể cả khi bấm lên chữ bên trong). */
        public void onClick(Runnable action) {
            if (action == null) return;
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) { action.run(); }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = false;
                    repaint();
                }
            };
            addMouseListener(m);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            for (Component c : getComponents()) {
                c.addMouseListener(m);
                if (c instanceof Container inner) for (Component c2 : inner.getComponents()) c2.addMouseListener(m);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color stripe = Theme.deep(fill);
            Color base = stripe != null ? Theme.SURFACE : fill;
            g2.setColor(hover ? Theme.mix(base, Theme.LAVENDER, 0.45f) : base);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(hover ? Theme.LAVENDER_DEEP : Theme.LINE);
            g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            if (stripe != null) {
                g2.setColor(stripe);
                g2.fillRect(0, 0, 4, getHeight());
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Nhãn trạng thái dạng thẻ chữ nhật. */
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
            g2.fillRect(0, 0, getWidth(), getHeight());
            Color edge = Theme.deep(getBackground());
            if (edge != null) {
                g2.setColor(edge);
                g2.fillRect(0, 0, 3, getHeight());
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------- Nhiều cửa sổ ----------

    /** Số thứ tự cửa sổ khi chạy nhiều client một lúc (run.sh 4 -> 1..4); 0 = chạy lẻ. */
    public static final int SLOT = Math.max(0, Integer.getInteger("goride.slot", 0));

    /** Đặt cửa sổ giữa màn hình rồi xếp lệch theo SLOT để nhiều client không chồng khít lên nhau. */
    public static void place(Window w) {
        w.setLocationRelativeTo(null);
        if (SLOT <= 1) return;
        Rectangle screen = w.getGraphicsConfiguration().getBounds();
        int step = SLOT - 1;
        int x = Math.max(screen.x, Math.min(screen.x + screen.width - w.getWidth(), screen.x + 20 + step * 60));
        int y = Math.max(screen.y, Math.min(screen.y + screen.height - w.getHeight(), screen.y + 20 + step * 40));
        w.setLocation(x, y);
    }

    /** Tiêu đề cửa sổ kèm số thứ tự client, để phân biệt khi mở nhiều cửa sổ. */
    public static String title(String t) {
        return SLOT > 0 ? t + "  [#" + SLOT + "]" : t;
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

    /** Nút lớn ở màn hình chính: emoji + tên + mô tả ngắn. */
    public static Card tile(String emoji, String title, String subtitle, Color bg, Runnable action) {
        Card c = new Card(new BorderLayout(16, 0), bg);
        c.setBorder(new EmptyBorder(22, 26, 22, 22));
        JLabel icon = new JLabel(emoji);
        icon.setFont(icon.getFont().deriveFont(36f));
        c.add(icon, BorderLayout.WEST);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setOpaque(false);
        text.add(label(title, 16f, Font.BOLD));
        text.add(muted("<html><div style='width:330px'>" + esc(subtitle) + "</div></html>"));
        c.add(text);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.onClick(action);
        return c;
    }

    /** Hàng tiêu đề của một bước: "Bước 2 · Chọn món" + phần phụ bên phải. */
    public static JPanel stepHeader(String step, String title, Component... right) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.BG); // tự vẽ nền: hàng tiêu đề có thể chứa ô tìm kiếm
        p.setBorder(new EmptyBorder(4, 0, 10, 0));
        JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
        left.setBackground(Theme.BG);
        JLabel s = muted(step);
        s.setFont(s.getFont().deriveFont(Font.BOLD, 11.5f));
        left.add(s);
        left.add(label(title, 19f, Font.BOLD));
        p.add(left, BorderLayout.WEST);
        if (right.length > 0) {
            JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            r.setBackground(Theme.BG);
            for (Component c : right) r.add(c);
            p.add(r, BorderLayout.EAST);
        }
        return p;
    }

    /** Lưới thẻ cuộn dọc, số cột cố định. */
    public static JPanel grid(int columns) {
        JPanel g = new JPanel(new GridLayout(0, columns, 12, 12));
        g.setOpaque(false);
        return g;
    }

    /** Emoji đại diện khi món/quán chưa có ảnh, chọn theo loại món. */
    public static String emojiFor(String category) {
        String c = category == null ? "" : category.toLowerCase();
        if (c.contains("phở") || c.contains("bún") || c.contains("hủ tiếu") || c.contains("mì")) return "🍜";
        if (c.contains("cơm")) return "🍚";
        if (c.contains("bánh mì")) return "🥖";
        if (c.contains("lẩu") || c.contains("nướng")) return "🍲";
        if (c.contains("chay")) return "🥗";
        if (c.contains("trà")) return "🧋";
        if (c.contains("cà phê")) return "☕";
        if (c.contains("tráng miệng") || c.contains("chè")) return "🍧";
        if (c.contains("ăn vặt")) return "🍢";
        return "🍽";
    }

    /** Ô ảnh vuông: hiện ảnh nếu có image_id, không thì nền pastel + emoji. */
    public static JLabel photo(com.goride.client.Net net, JsonObject src, String category, int w, int h) {
        JLabel l = new JLabel(emojiFor(category), SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Math.min(w, h) * 0.45f));
        l.setPreferredSize(new Dimension(w, h));
        l.setMinimumSize(new Dimension(w, h));
        l.setOpaque(true);
        l.setBackground(Theme.ROW_ALT);
        l.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        if (Json.has(src, "image_id")) {
            long id = src.get("image_id").getAsLong();
            ImageStore.load(net, id, w - 2, h - 2, icon -> {
                l.setText("");
                l.setIcon(icon);
            });
        }
        return l;
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
