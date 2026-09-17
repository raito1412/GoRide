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
    private boolean leaving;

    protected BaseFrame(Net net, String roleName, Color accent, Color accentDeep) {
        super("GoRide · " + roleName);
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
        header.setBackground(accent);
        header.setBorder(new EmptyBorder(10, 18, 10, 14));
        JLabel logo = Ui.label("GoRide", 20f, Font.BOLD);
        logo.setForeground(accentDeep);
        JLabel role = Ui.label("  " + roleName, 13f, Font.BOLD);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(logo);
        left.add(role);
        header.add(left, BorderLayout.WEST);

        nameLabel = Ui.label(Json.str(net.me, "full_name", ""), 13f, Font.PLAIN);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
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
        setLocationRelativeTo(null);
    }

    /** Tab cuộn ngang thay vì xuống nhiều hàng. */
    protected static JTabbedPane newTabs() {
        JTabbedPane t = new JTabbedPane();
        t.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        return t;
    }

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
        JScrollPane s = new JScrollPane(holder);
        s.setBorder(null);
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
