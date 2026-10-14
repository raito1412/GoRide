package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Json;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Khung chung cho 3 vai trò: thanh tiêu đề màu theo vai trò, đăng xuất, xử lý mất kết nối. */
public abstract class BaseFrame extends JFrame {
    protected final Net net;
    protected final JLabel nameLabel;
    private final JButton backBtn = Ui.button("← Quay lại", Theme.SURFACE, null);
    private final JLabel screenTitle = Ui.label("", 15f, Font.BOLD);
    private Runnable onBack;
    private final JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    private boolean leaving;

    protected BaseFrame(Net net, String roleName, Color accent, Color accentDeep) {
        super(Ui.title("GoRide · " + roleName));
        this.net = net;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                leaving = true;
                beforeClose();
                net.close();
                System.exit(0);
            }
        });

        JPanel header = new JPanel(new BorderLayout());
        // thanh tiêu đề trắng, vạch màu vai trò dày 3px ở đáy thay cho cả dải màu pastel
        header.setBackground(Theme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, accentDeep), new EmptyBorder(10, 18, 9, 14)));
        JLabel logo = Ui.label("GoRide", 20f, Font.BOLD);
        logo.setForeground(accentDeep);
        JLabel role = Ui.label("  " + roleName, 13f, Font.BOLD);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(logo);
        left.add(role);
        header.add(left, BorderLayout.WEST);

        // khu điều hướng: nút quay lại + tên màn hình đang mở
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        nav.setOpaque(false);
        nav.setBorder(new EmptyBorder(0, 24, 0, 0));
        backBtn.addActionListener(e -> {
            if (onBack != null) onBack.run();
        });
        backBtn.setVisible(false);
        nav.add(backBtn);
        nav.add(screenTitle);
        header.add(nav, BorderLayout.CENTER);

        nameLabel = Ui.label(Json.str(net.me, "full_name", ""), 13f, Font.PLAIN);
        headerActions.setOpaque(false);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(headerActions);
        right.add(nameLabel);
        right.add(Ui.button("Đăng xuất", Theme.SURFACE, this::logout));
        header.add(right, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        setContentPane(root);

        net.onDisconnect(() -> backToLogin("Mất kết nối tới server."));
        net.on("SESSION_CLOSED", d -> backToLogin(Json.str(d, "reason", "Phiên đăng nhập đã kết thúc.")));

        setSize(1280, 800);
        setMinimumSize(new Dimension(1100, 680));
        Ui.place(this);
    }

    /**
     * Đặt tên màn hình đang mở trên thanh tiêu đề. onBack = null thì ẩn nút quay lại
     * (dùng cho màn hình chính).
     */
    protected void setNav(String title, Runnable onBack) {
        this.onBack = onBack;
        screenTitle.setText(title == null ? "" : title);
        backBtn.setVisible(onBack != null);
    }

    /** Thêm lối tắt vào góc phải thanh tiêu đề (Lịch sử, Tài khoản...). */
    protected void addHeaderAction(String text, Runnable action) {
        JButton b = Ui.button(text, Theme.SURFACE, action);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 12.5f));
        headerActions.add(b);
    }

    /** Tab cuộn ngang thay vì xuống nhiều hàng. */
    protected static JTabbedPane newTabs() {
        JTabbedPane t = new JTabbedPane();
        t.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        return t;
    }

    /** Cho các panel con dùng cùng kiểu tab với khung chính. */
    static JTabbedPane newTabsPublic() { return newTabs(); }

    protected void setBody(Component c) {
        getContentPane().add(c, BorderLayout.CENTER);
    }

    /** Dọn dẹp riêng (tắt GPS, timer...). */
    protected void beforeClose() { }

    private void logout() {
        if (!Ui.confirm(this, "Đăng xuất khỏi GoRide?")) return;
        leaving = true;
        beforeClose();
        new Thread(() -> {
            try { net.call("LOGOUT", null); } catch (RuntimeException ignored) { }
            net.close();
            SwingUtilities.invokeLater(this::openLogin);
        }).start();
    }

    private void backToLogin(String reason) {
        if (leaving) return;
        leaving = true;
        beforeClose();
        net.close();
        JOptionPane.showMessageDialog(this, reason, "GoRide", JOptionPane.WARNING_MESSAGE);
        openLogin();
    }

    private void openLogin() {
        dispose();
        new LoginFrame().setVisible(true);
    }

    /** Khung trái (điều khiển) + phải (bản đồ). */
    protected static JSplitPane split(Component left, Component right, int leftWidth) {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        sp.setDividerLocation(leftWidth);
        sp.setResizeWeight(0);
        sp.setBorder(null);
        sp.setDividerSize(6);
        return sp;
    }

    /** Làm cho panel cuộn dọc được, chiều ngang bám theo khung. */
    protected static JScrollPane vscroll(JPanel content) {
        JPanel holder = new ScrollableColumn(content);
        holder.setBackground(Theme.BG);
        JScrollPane s = new JScrollPane(holder);
        s.setBorder(null);
        s.getViewport().setBackground(Theme.BG);
        s.getVerticalScrollBar().setUnitIncrement(18);
        s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return s;
    }

    private static class ScrollableColumn extends JPanel implements Scrollable {
        ScrollableColumn(JPanel content) {
            super(new BorderLayout());
            add(content, BorderLayout.NORTH);
        }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
