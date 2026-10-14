package com.goride.client.ui;

import com.goride.client.Net;
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
    // xác thực email bằng OTP
    private final JTextField otpCode = new JTextField(20);
    private final JLabel otpTarget = Ui.muted(" ");
    private final JLabel otpCountdown = Ui.label("05:00", 20f, Font.BOLD);
    private JButton resendBtn;
    private Timer otpTimer;
    private String pendingEmail;
    private int secondsLeft, resendLeft;

    public LoginFrame() {
        super(Ui.title("GoRide"));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.BG);

        Ui.Card card = new Ui.Card(new BorderLayout(0, 12), Theme.SURFACE);
        card.setBorder(new EmptyBorder(28, 32, 24, 32));
        JPanel head = new JPanel(new GridLayout(2, 1));
        head.setBackground(Theme.SURFACE);
        JLabel logo = Ui.label("GoRide", 30f, Font.BOLD);
        logo.setForeground(Theme.LAVENDER_DEEP);
        head.add(logo);
        head.add(Ui.muted("Đặt xe & giao đồ ăn"));
        card.add(head, BorderLayout.NORTH);

        body.setBackground(Theme.SURFACE);
        body.add(buildLogin(), "login");
        body.add(buildRegister(), "register");
        body.add(buildOtp(), "otp");
        card.add(body);
        status.setForeground(Theme.ROSE_DEEP);
        card.add(status, BorderLayout.SOUTH);

        root.add(card);
        setContentPane(root);
        setSize(560, 720);
        setMinimumSize(new Dimension(480, 600));
        Ui.place(this);
    }

    private JPanel buildLogin() {
        JPanel p = formPanel();
        phone.putClientProperty("JTextField.placeholderText", "Nhập số điện thoại (10 số)");
        Ui.digitsOnly(phone, 11);
        addRow(p, "Số điện thoại", phone);
        addRow(p, "Mật khẩu", password);
        password.addActionListener(e -> login());
        JButton go = Ui.button("Đăng nhập", Theme.LAVENDER, this::login);
        addWide(p, go);
        addWide(p, link("Chưa có tài khoản? Đăng ký", () -> show("register")));

        JPanel demo = new JPanel(new GridLayout(0, 1, 0, 4));
        demo.setBackground(Theme.SURFACE);
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
        Ui.digitsOnly(rPhone, 11);
        rEmail.putClientProperty("JTextField.placeholderText", "Nhận mã xác thực — bắt buộc");
        addRow(p, "Email (Gmail)", rEmail);
        addRow(p, "Mật khẩu", rPass);

        driverBox.setBackground(Theme.SURFACE);
        GridBagConstraints c = new GridBagConstraints();
        String[] labels = {"Loại xe", "Biển số", "Hãng xe", "Màu xe", "Số GPLX"};
        JComponent[] inputs = {rVehicle, rPlate, rBrand, rColor, rLicense};
        rPlate.putClientProperty("JTextField.placeholderText", "Ví dụ: 59X1-123.45");
        for (int i = 0; i < labels.length; i++) {
            c.gridx = 0; c.gridy = i; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(4, 0, 4, 12);
            c.fill = GridBagConstraints.NONE; c.weightx = 0;
            driverBox.add(new JLabel(labels[i]), c);
            c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.insets = new Insets(4, 0, 4, 0);
            driverBox.add(inputs[i], c);
        }
        driverBox.setVisible(false);
        addWide(p, driverBox);
        asUser.addActionListener(e -> showDriverFields(false));
        asDriver.addActionListener(e -> showDriverFields(true));

        addWide(p, Ui.button("Tạo tài khoản", Theme.MINT, this::register));
        addWide(p, link("← Quay lại đăng nhập", () -> show("login")));
        return wrapTop(p);
    }

    private JPanel buildOtp() {
        JPanel p = formPanel();
        addWide(p, Ui.label("Xác thực email", 20f, Font.BOLD));
        addWide(p, otpTarget);
        otpCode.putClientProperty("JTextField.placeholderText", "6 chữ số");
        Ui.digitsOnly(otpCode, 6);
        otpCode.setFont(otpCode.getFont().deriveFont(Font.BOLD, 22f));
        otpCode.setHorizontalAlignment(SwingConstants.CENTER);
        otpCode.addActionListener(e -> verifyOtp());
        // chỉ dùng addWide ở thẻ này: addRow đánh số hàng theo cách khác nên trộn hai kiểu sẽ chồng dòng
        JPanel codeBox = new JPanel(new BorderLayout(0, 4));
        codeBox.setOpaque(false);
        codeBox.add(Ui.label("Nhập mã trong email", 12.5f, Font.BOLD), BorderLayout.NORTH);
        codeBox.add(otpCode);
        addWide(p, codeBox);
        addWide(p, Ui.row(Ui.muted("Mã hết hạn sau:"), otpCountdown));
        addWide(p, Ui.button("Xác nhận", Theme.MINT, this::verifyOtp));
        resendBtn = Ui.button("Gửi lại mã", Theme.BUTTER, this::resendOtp);
        addWide(p, resendBtn);
        addWide(p, link("← Sửa lại thông tin đăng ký", () -> {
            stopOtpTimer();
            show("register");
        }));
        return wrapTop(p);
    }

    /** Đếm ngược hiệu lực của mã và thời gian chờ được gửi lại. */
    private void startOtpCountdown(int expiresIn, int resendAfter) {
        secondsLeft = expiresIn;
        resendLeft = resendAfter;
        if (otpTimer == null) otpTimer = new Timer(1000, e -> tickOtp());
        otpTimer.start();
        tickOtp();
    }

    private void stopOtpTimer() {
        if (otpTimer != null) otpTimer.stop();
    }

    private void tickOtp() {
        boolean expired = secondsLeft <= 0;
        otpCountdown.setText(expired ? "đã hết hạn" : String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60));
        otpCountdown.setForeground(expired || secondsLeft <= 30 ? Theme.ROSE_DEEP : Theme.INK);
        resendBtn.setText(resendLeft > 0 ? "Gửi lại mã sau " + resendLeft + "s" : "Gửi lại mã");
        resendBtn.setEnabled(resendLeft <= 0);
        if (secondsLeft > 0) secondsLeft--;
        if (resendLeft > 0) resendLeft--;
    }

    private void showOtpScreen(JsonObject res, String fallbackEmail) {
        busy(false, " ");
        pendingEmail = Json.str(res, "email", fallbackEmail);
        otpTarget.setText("<html>Mã gồm 6 số đã được gửi tới<br><b>" + Ui.esc(pendingEmail) + "</b></html>");
        otpCode.setText("");
        show("otp");
        startOtpCountdown(Json.integer(res, "expires_in_s", 300), Json.integer(res, "resend_after_s", 60));
        otpCode.requestFocus();
        if (Json.has(res, "dev_hint")) Ui.info(this, Json.str(res, "dev_hint"));
    }

    private void verifyOtp() {
        String code = otpCode.getText().trim();
        if (!code.matches("\\d{6}")) {
            busy(false, "Mã xác thực gồm đúng 6 chữ số");
            return;
        }
        JsonObject req = Json.obj("email", pendingEmail, "code", code);
        busy(true, "Đang xác thực...");
        connection().thenAccept(n -> {
            if (n == null) return;
            try {
                JsonObject res = n.call("REGISTER_VERIFY", req).getAsJsonObject();
                SwingUtilities.invokeLater(() -> {
                    stopOtpTimer();
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

    private void resendOtp() {
        JsonObject req = Json.obj("email", pendingEmail);
        busy(true, "Đang gửi lại mã...");
        connection().thenAccept(n -> {
            if (n == null) return;
            try {
                JsonObject res = n.call("REGISTER_RESEND", req).getAsJsonObject();
                SwingUtilities.invokeLater(() -> showOtpScreen(res, pendingEmail));
            } catch (RuntimeException e) {
                fail(e.getMessage());
            }
        });
    }

    /** Hiện/ẩn nhóm ô của tài xế rồi vẽ lại cả thẻ cho sạch. */
    private void showDriverFields(boolean show) {
        driverBox.setVisible(show);
        body.revalidate();
        body.repaint();
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
        if (rEmail.getText().trim().isEmpty()) {
            busy(false, "Nhập email để nhận mã xác thực");
            return;
        }
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
        busy(true, "Đang gửi mã xác thực...");
        connection().thenAccept(n -> {
            if (n == null) return;
            try {
                JsonObject res = n.call("REGISTER", req).getAsJsonObject();
                SwingUtilities.invokeLater(() -> showOtpScreen(res, rEmail.getText().trim().toLowerCase()));
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
                fail("Chưa kết nối được. Hãy kiểm tra lại đường truyền rồi thử lại.");
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

    /**
     * Panel của biểu mẫu tự vẽ nền trắng của thẻ. Nếu để trong suốt, Swing phải nhờ tổ tiên
     * cách 4-5 tầng vẽ lại nền mỗi khi một ô đổi nội dung hoặc bố cục xê dịch — dễ sót nét chữ cũ.
     */
    private static JPanel formPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.SURFACE);
        return p;
    }

    private static JPanel wrapTop(JPanel p) {
        JPanel w = new JPanel(new BorderLayout());
        w.setBackground(Theme.SURFACE);
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
