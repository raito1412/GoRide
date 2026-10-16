package com.goride.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Thông báo nổi ở góc trên bên phải cửa sổ: hiện ngay khi có việc mới, tự tắt sau vài giây
 * và không chặn thao tác như hộp thoại. Nhiều thông báo sẽ xếp chồng xuống dưới.
 */
public final class Toast {
    private static final int WIDTH = 420;
    private static final int GAP = 10;
    private static final int TOP = 16;
    private static final int PLAIN_MS = 6000;
    private static final int ACTION_MS = 15000;
    private static final int MAX_ON_SCREEN = 4;
    /** Bề rộng phần chữ; cộng thêm icon, nút đóng và lề là vừa WIDTH. */
    private static final int TEXT_WIDTH = 280;

    /** Các thông báo đang hiện của từng cửa sổ, để xếp chồng và dọn khi tắt. */
    private static final java.util.Map<JLayeredPane, List<JComponent>> LIVE = new java.util.WeakHashMap<>();

    private Toast() {}

    public static void info(Component owner, String emoji, String title, String body, Color accent) {
        show(owner, emoji, title, body, accent, null, null);
    }

    /**
     * actionText != null thì thông báo có thêm một nút; bấm nút sẽ chạy action rồi tự đóng.
     * Gọi được từ bất kỳ luồng nào.
     */
    public static void show(Component owner, String emoji, String title, String body,
                            Color accent, String actionText, Runnable action) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(owner, emoji, title, body, accent, actionText, action));
            return;
        }
        // getWindowAncestor trả null khi owner chính là cửa sổ, nên phải xét riêng
        Window w = owner instanceof Window self ? self : SwingUtilities.getWindowAncestor(owner);
        if (!(w instanceof JFrame frame) || !frame.isShowing()) return;
        JLayeredPane layers = frame.getLayeredPane();
        List<JComponent> live = LIVE.computeIfAbsent(layers, k -> new ArrayList<>());
        while (live.size() >= MAX_ON_SCREEN) remove(layers, live.get(0));

        JComponent card = build(emoji, title, body, accent, actionText, action, layers);
        live.add(card);
        layers.add(card, JLayeredPane.POPUP_LAYER);
        relayout(layers);

        Timer life = new Timer(actionText == null ? PLAIN_MS : ACTION_MS, e -> remove(layers, card));
        life.setRepeats(false);
        life.start();
        card.putClientProperty("timer", life);
    }

    /** Đóng mọi thông báo đang hiện trên cửa sổ của owner. */
    public static void clear(Component owner) {
        Window w = owner instanceof Window self ? self : SwingUtilities.getWindowAncestor(owner);
        if (!(w instanceof JFrame frame)) return;
        JLayeredPane layers = frame.getLayeredPane();
        List<JComponent> live = LIVE.get(layers);
        if (live == null) return;
        for (JComponent c : new ArrayList<>(live)) remove(layers, c);
    }

    private static JComponent build(String emoji, String title, String body, Color accent,
                                    String actionText, Runnable action, JLayeredPane layers) {
        Ui.Card card = new Ui.Card(new BorderLayout(12, 0), accent);
        card.setBorder(new EmptyBorder(14, 20, 14, 12));

        String iconName = Icons.forEmoji(emoji);
        Color tone = Theme.deep(accent) != null ? Theme.deep(accent) : Theme.INK;
        JLabel icon = iconName != null ? new JLabel(Icons.get(iconName, 22, tone)) : new JLabel(emoji);
        icon.setVerticalAlignment(SwingConstants.TOP);
        card.add(icon, BorderLayout.WEST);

        // BorderLayout.CENTER kéo giãn nhãn HTML khiến nó xuống dòng theo bề rộng thật và bị cắt;
        // bọc mỗi dòng trong FlowLayout để nhãn giữ đúng bề rộng đã đặt trong style.
        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);

        JLabel head = new JLabel(title);
        head.setFont(head.getFont().deriveFont(Font.BOLD, 14f));
        head.setForeground(Theme.INK);
        text.add(line(head));

        // JLabel + CSS width không ép được bề rộng ổn định; JTextArea xuống dòng theo đúng kích thước đặt
        JTextArea detail = new JTextArea(body);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setEditable(false);
        detail.setFocusable(false);
        detail.setOpaque(false);
        detail.setForeground(Theme.INK);
        detail.setFont(head.getFont().deriveFont(Font.PLAIN, 13f));
        detail.setSize(TEXT_WIDTH, Short.MAX_VALUE);
        detail.setPreferredSize(new Dimension(TEXT_WIDTH, detail.getPreferredSize().height));
        text.add(Box.createVerticalStrut(4));
        text.add(line(detail));

        if (actionText != null) {
            text.add(Box.createVerticalStrut(8));
            text.add(line(Ui.button(actionText, Theme.SURFACE, () -> {
                remove(layers, card);
                if (action != null) action.run();
            })));
        }
        card.add(text);

        JButton close = new JButton(Icons.get("x", 14, Theme.MUTED));
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setForeground(Theme.MUTED);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.setMargin(new Insets(0, 0, 0, 0));
        close.setPreferredSize(new Dimension(22, 22));
        close.addActionListener(e -> remove(layers, card));
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(close, BorderLayout.NORTH);
        card.add(east, BorderLayout.EAST);
        return card;
    }

    /** Giữ component đúng bề rộng ưa thích, không bị layout kéo giãn. */
    private static JPanel line(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(c);
        return p;
    }

    private static void remove(JLayeredPane layers, JComponent card) {
        List<JComponent> live = LIVE.get(layers);
        if (live == null || !live.remove(card)) return;
        Object t = card.getClientProperty("timer");
        if (t instanceof Timer timer) timer.stop();
        layers.remove(card);
        layers.revalidate();
        layers.repaint();
        relayout(layers);
    }

    /** Xếp các thông báo thành cột dọc sát mép phải. */
    private static void relayout(JLayeredPane layers) {
        List<JComponent> live = LIVE.get(layers);
        if (live == null) return;
        int y = TOP;
        for (JComponent c : live) {
            int h = c.getPreferredSize().height;
            c.setBounds(layers.getWidth() - WIDTH - GAP * 2, y, WIDTH, h);
            c.validate(); // lớp phủ không có layout manager: tự xếp chữ bên trong, không thì thẻ hiện trống
            y += h + GAP;
        }
        layers.repaint();
    }
}
