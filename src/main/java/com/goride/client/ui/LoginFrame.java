package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Config;
import com.goride.common.Json;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/** Màn hình đăng nhập / đăng ký. Sau khi đăng nhập mở giao diện theo vai trò. */
public class LoginFrame extends JFrame {
    private Net net;
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JLabel status = Ui.muted(" ");

    // đăng nhập
    private final JTextField phone = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    // đăng ký
    private final JTextField rName = new JTextField(20), rPhone = new JTextField(20), rEmail = new JTextField(20);
    private final JPasswordField rPass = new JPasswordField(20);
    private final JRadioButton asUser = new JRadioButton("Khách hàng", true), asDriver = new JRadioButton("Tài xế");
    private final JComboBox<String> rVehicle = Ui.codes("BIKE", "CAR");
    private final JTextField rPlate = new JTextField(20), rBrand = new JTextField(20), rColor = new JTextField(20), rLicense = new JTextField(20);
    private final JPanel driverBox = new JPanel(new GridBagLayout());

    public LoginFrame() {
        super("GoRide");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.BG);

        Ui.Card card = new Ui.Card(new BorderLayout(0, 12), Theme.SURFACE);
        card.setBorder(new EmptyBorder(28, 32, 24, 32));
        JPanel head = new JPanel(new GridLayout(2, 1));
        head.setOpaque(false);
        JLabel logo = Ui.label("GoRide", 30f, Font.BOLD);
        logo.setForeground(Theme.LAVENDER_DEEP);
        head.add(logo);
        head.add(Ui.muted("Đặt xe & giao đồ ăn · Server " + Config.serverHost() + ":" + Config.tcpPort()));
        card.add(head, BorderLayout.NORTH);

        body.setOpaque(false);
        body.add(buildLogin(), "login");
        body.add(buildRegister(), "register");
        card.add(body);
        status.setForeground(Theme.ROSE_DEEP);
        card.add(status, BorderLayout.SOUTH);

        root.add(card);
        setContentPane(root);
        setSize(560, 720);
        setMinimumSize(new Dimension(480, 600));
        setLocationRelativeTo(null);
    }

    private JPanel buildLogin() {
        JPanel p = formPanel();
        phone.putClientProperty("JTextField.placeholderText", "0900000000");
        addRow(p, "Số điện thoại", phone);
        addRow(p, "Mật khẩu", password);
        password.addActionListener(e -> login());
        JButton go = Ui.button("Đăng nhập", Theme.LAVENDER, this::login);
        addWide(p, go);
        addWide(p, link("Chưa có tài khoản? Đăng ký", () -> show("register")));

        JPanel demo = new JPanel(new GridLayout(0, 1, 0, 4));
        demo.setOpaque(false);
        demo.setBorder(new EmptyBorder(16, 0, 0, 0));
        demo.add(Ui.muted("Tài khoản demo (bấm để điền):"));
        demo.add(demoLink("Khách hàng · 0911111111 / 123456", "0911111111", "123456", Theme.LAVENDER_DEEP));
        demo.add(demoLink("Tài xế xe máy · 0922222221 / 123456", "0922222221", "123456", Theme.MINT_DEEP));
        demo.add(demoLink("Tài xế ô tô · 0922222222 / 123456", "0922222222", "123456", Theme.MINT_DEEP));
        demo.add(demoLink("Admin · 0900000000 / admin123", "0900000000", "admin123", Theme.PEACH_DEEP));
        addWide(p, demo);
        return wrapTop(p);
    }

    private JPanel buildRegister() {
        JPanel p = formPanel();
        ButtonGroup g = new ButtonGroup();
        g.add(asUser);
        g.add(asDriver);
        JPanel roles = Ui.row(asUser, asDriver);
        addRow(p, "Loại tài khoản", roles);
        addRow(p, "Họ tên", rName);
        addRow(p, "Số điện thoại", rPhone);
        addRow(p, "Email", rEmail);
        addRow(p, "Mật khẩu", rPass);

        driverBox.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        String[] labels = {"Loại xe", "Biển số", "Hãng xe", "Màu xe", "Số GPLX"};
        JComponent[] inputs = {rVehicle, rPlate, rBrand, rColor, rLicense};
        rPlate.putClientProperty("JTextField.placeholderText", "59X1-123.45");
        for (int i = 0; i < labels.length; i++) {
            c.gridx = 0; c.gridy = i; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(4, 0, 4, 12);
            c.fill = GridBagConstraints.NONE; c.weightx = 0;
            driverBox.add(new JLabel(labels[i]), c);
            c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.insets = new Insets(4, 0, 4, 0);
            driverBox.add(inputs[i], c);
        }
        driverBox.setVisible(false);
        addWide(p, driverBox);
        asUser.addActionListener(e -> driverBox.setVisible(false));
        asDriver.addActionListener(e -> driverBox.setVisible(true));

        addWide(p, Ui.button("Tạo tài khoản", Theme.MINT, this::register));
        addWide(p, link("← Quay lại đăng nhập", () -> show("login")));
        return wrapTop(p);
    }

    // ---------- Hành động ----------

    private void login() {
        JsonObject req = Json.obj("phone", phone.getText().trim(), "password", new String(password.getPassword()));
        busy(true, "Đang đăng nhập...");
        connection().thenAccept(n -> {
            if (n == null) return;
            try {
                JsonObject me = n.call("LOGIN", req).getAsJsonObject();
                n.me = me;
                SwingUtilities.invokeLater(() -> openHome(n));
            } catch (RuntimeException e) {
                fail(e.getMessage());
            }
        });
    }

    private void register() {
        JsonObject req = Json.obj("role", asDriver.isSelected() ? "DRIVER" : "USER",
                "full_name", rName.getText().trim(), "phone", rPhone.getText().trim(),
                "email", rEmail.getText().trim(), "password", new String(rPass.getPassword()));
        if (asDriver.isSelected()) {
            req.addProperty("vehicle_type", (String) rVehicle.getSelectedItem());
            req.addProperty("plate_number", rPlate.getText().trim());
            req.addProperty("vehicle_brand", rBrand.getText().trim());
            req.addProperty("vehicle_color", rColor.getText().trim());
            req.addProperty("license_number", rLicense.getText().trim());
        }
        busy(true, "Đang tạo tài khoản...");
        connection().thenAccept(n -> {
            if (n == null) return;
            try {
                JsonObject res = n.call("REGISTER", req).getAsJsonObject();
                SwingUtilities.invokeLater(() -> {
                    busy(false, " ");
                    Ui.info(this, Json.str(res, "message"));
                    phone.setText(rPhone.getText().trim());
                    password.setText("");
                    show("login");
                    password.requestFocus();
                });
            } catch (RuntimeException e) {
                fail(e.getMessage());
            }
        });
    }

    /** Dùng lại kết nối nếu còn, không thì kết nối mới (chạy nền). */
    private CompletableFuture<Net> connection() {
        Net current = net;
        if (current != null) return CompletableFuture.completedFuture(current);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Net n = Net.connect();
                n.onDisconnect(() -> net = null);
                net = n;
                return n;
            } catch (Exception e) {
                fail("Không kết nối được server " + Config.serverHost() + ":" + Config.tcpPort() + " — hãy chạy server trước");
                return null;
            }
        });
    }

    private void openHome(Net n) {
        String role = Json.str(n.me, "role", "");
        JFrame home = switch (role) {
            case "USER" -> new UserFrame(n);
            case "DRIVER" -> new DriverFrame(n);
            case "ADMIN" -> new AdminFrame(n);
            default -> null;
        };
        if (home == null) {
            fail("Vai trò không hợp lệ");
            return;
        }
        net = null; // kết nối đã giao cho màn hình chính
        home.setVisible(true);
        dispose();
    }

    private void fail(String msg) {
        SwingUtilities.invokeLater(() -> busy(false, msg));
    }

    private void busy(boolean on, String msg) {
        status.setForeground(on ? Theme.MUTED : Theme.ROSE_DEEP);
        status.setText(msg);
        setCursor(Cursor.getPredefinedCursor(on ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void show(String name) {
        status.setText(" ");
        cards.show(body, name);
    }

    // ---------- Bố cục ----------

    private static JPanel formPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        return p;
    }

    private static JPanel wrapTop(JPanel p) {
        JPanel w = new JPanel(new BorderLayout());
        w.setOpaque(false);
        w.add(p, BorderLayout.NORTH);
        return w;
    }

    private static void addRow(JPanel p, String label, JComponent input) {
        int y = p.getComponentCount();
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = y; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(10, 0, 2, 0);
        p.add(Ui.label(label, 12.5f, Font.BOLD), c);
        c.gridy = y + 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.insets = new Insets(0, 0, 2, 0);
        p.add(input, c);
    }

    private static void addWide(JPanel p, JComponent comp) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = p.getComponentCount() + 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        c.insets = new Insets(12, 0, 0, 0);
        p.add(comp, c);
    }

    private static JButton link(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setForeground(Theme.LAVENDER_DEEP);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    private JButton demoLink(String text, String ph, String pw, Color color) {
        JButton b = link(text, () -> {
            phone.setText(ph);
            password.setText(pw);
        });
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setForeground(color);
        return b;
    }
}
