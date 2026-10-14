package com.goride.client.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Phong cách phẳng, góc vuông, tương phản rõ (tham khảo IBM Carbon / Windows Fluent bản doanh nghiệp):
 * không bo tròn, không đổ bóng. Màu nhạt chỉ dùng làm nền nhãn/nền nhấn; nút bấm dùng màu đậm đặc.
 * Mỗi vai trò có một màu nhận diện ở vạch dưới thanh tiêu đề: Khách = xanh dương, Tài xế = xanh lá, Admin = cam.
 */
public final class Theme {
    public static final Color BG = hex("#F0F2F5");
    public static final Color SURFACE = Color.WHITE;
    public static final Color ROW_ALT = hex("#F7F8FA");
    public static final Color LINE = hex("#C9CED6");
    public static final Color INK = hex("#121619");
    public static final Color MUTED = hex("#5A6472");

    // cặp màu: bản nhạt (nền nhãn, nền nhấn) và bản đậm (nút, vạch, chữ nhấn)
    public static final Color LAVENDER = hex("#DCE5FF");
    public static final Color LAVENDER_DEEP = hex("#0F4FD6");
    public static final Color MINT = hex("#D3EEDD");
    public static final Color MINT_DEEP = hex("#13703F");
    public static final Color PEACH = hex("#FCE1CC");
    public static final Color PEACH_DEEP = hex("#B8480A");
    public static final Color ROSE = hex("#F9D6D6");
    public static final Color ROSE_DEEP = hex("#B3161B");
    public static final Color BUTTER = hex("#FBEDB8");
    public static final Color BUTTER_DEEP = hex("#7D5A00");
    public static final Color SKY = hex("#D4EAF5");
    public static final Color SKY_DEEP = hex("#035E8C");

    private Theme() {}

    public static void install() {
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#0F4FD6"));
        FlatLightLaf.setup();
        Font base = UIManager.getFont("defaultFont");
        if (base != null) UIManager.put("defaultFont", base.deriveFont(13.5f));
        UIManager.put("Panel.background", BG);
        // góc vuông cho mọi thứ: nút, ô nhập, combo, thanh cuộn, checkbox, tooltip
        UIManager.put("Button.arc", 0);
        UIManager.put("Component.arc", 0);
        UIManager.put("TextComponent.arc", 0);
        UIManager.put("CheckBox.arc", 0);
        UIManager.put("ProgressBar.arc", 0);
        UIManager.put("ScrollBar.thumbArc", 0);
        UIManager.put("ScrollBar.trackArc", 0);
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Component.innerFocusWidth", 1);
        UIManager.put("Button.innerFocusWidth", 0);
        UIManager.put("Component.borderColor", LINE);
        UIManager.put("Component.focusedBorderColor", LAVENDER_DEEP);
        UIManager.put("Component.focusColor", LAVENDER_DEEP);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumb", hex("#A8AFB9"));
        UIManager.put("TabbedPane.tabHeight", 40);
        UIManager.put("TabbedPane.selectedBackground", SURFACE);
        UIManager.put("TabbedPane.underlineColor", LAVENDER_DEEP);
        UIManager.put("TabbedPane.tabSelectionHeight", 3);
        UIManager.put("TabbedPane.inactiveUnderlineColor", LAVENDER_DEEP);
        UIManager.put("ToggleButton.selectedBackground", LAVENDER);
        UIManager.put("ToggleButton.selectedForeground", INK);
        UIManager.put("ToggleButton.borderColor", LINE);
        UIManager.put("TabbedPane.hoverColor", hex("#E4E8EE"));
        UIManager.put("TabbedPane.tabAreaInsets", new Insets(0, 12, 0, 12));
        UIManager.put("Table.rowHeight", 32);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.gridColor", LINE);
        UIManager.put("Table.selectionBackground", LAVENDER);
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TableHeader.background", hex("#E3E7EC"));
        UIManager.put("TableHeader.font", UIManager.getFont("defaultFont").deriveFont(Font.BOLD));
        UIManager.put("TableHeader.foreground", INK);
        UIManager.put("SplitPane.background", BG);
        UIManager.put("OptionPane.background", SURFACE);
        commitInputMethodOnFocusLost();
    }

    /**
     * Bộ gõ (tiếng Việt, hoặc gõ nhanh chưa kịp chốt) giữ lại đoạn chữ "chưa chốt" — phần có gạch
     * chân mờ. Nếu người dùng bấm sang ô khác lúc đó, macOS chốt đoạn này vào ô VỪA được focus,
     * làm chữ của ô trên rơi xuống ô dưới. Chốt ngay khi rời ô là hết, và chỉ cần cài một lần
     * cho toàn ứng dụng nên không sót ô nào.
     */
    private static void commitInputMethodOnFocusLost() {
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() != java.awt.event.FocusEvent.FOCUS_LOST) return;
            if (!(e.getSource() instanceof javax.swing.text.JTextComponent c)) return;
            java.awt.im.InputContext ic = c.getInputContext();
            if (ic != null) ic.endComposition();
        }, AWTEvent.FOCUS_EVENT_MASK);
    }

    /** Màu nền cho các "nhãn trạng thái". */
    public static Color statusColor(String code) {
        if (code == null) return LINE;
        return switch (code) {
            case "SEARCHING", "PLACED", "PENDING" -> BUTTER;
            case "ACCEPTED", "CONFIRMED", "ARRIVED", "AT_RESTAURANT", "RIDE", "CAR" -> LAVENDER;
            case "PICKED_UP", "DELIVERING", "FOOD", "DELIVERY" -> PEACH;
            case "COMPLETED", "DELIVERED", "APPROVED", "PAID", "ACTIVE", "BIKE" -> MINT;
            case "CANCELLED", "LOCKED", "REJECTED", "REFUNDED" -> ROSE;
            default -> LINE;
        };
    }

    public static Color hex(String h) { return Color.decode(h); }

    /** Bản đậm tương ứng của một màu nhạt (dùng cho nút đặc, vạch nhấn). Màu không có cặp thì trả null. */
    public static Color deep(Color c) {
        if (LAVENDER.equals(c)) return LAVENDER_DEEP;
        if (MINT.equals(c)) return MINT_DEEP;
        if (PEACH.equals(c)) return PEACH_DEEP;
        if (ROSE.equals(c)) return ROSE_DEEP;
        if (BUTTER.equals(c)) return BUTTER_DEEP;
        if (SKY.equals(c)) return SKY_DEEP;
        if (LINE.equals(c)) return MUTED;
        return null;
    }

    /** Trộn hai màu, t = 0 giữ nguyên a, t = 1 thành b. Dùng cho hiệu ứng rê chuột. */
    public static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
