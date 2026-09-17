package com.goride.client.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Bảng màu pastel. Mỗi vai trò có một màu nhận diện ở thanh tiêu đề:
 * Khách hàng = oải hương, Tài xế = bạc hà, Admin = đào.
 */
public final class Theme {
    public static final Color BG = hex("#F7F6FC");
    public static final Color SURFACE = Color.WHITE;
    public static final Color ROW_ALT = hex("#FBFAFE");
    public static final Color LINE = hex("#E8E6F2");
    public static final Color INK = hex("#35344D");
    public static final Color MUTED = hex("#8B8AA6");

    public static final Color LAVENDER = hex("#CDD2FF");
    public static final Color LAVENDER_DEEP = hex("#5E66C4");
    public static final Color MINT = hex("#C5ECDA");
    public static final Color MINT_DEEP = hex("#35946F");
    public static final Color PEACH = hex("#FFDCC7");
    public static final Color PEACH_DEEP = hex("#C46A36");
    public static final Color ROSE = hex("#F9C9D2");
    public static final Color ROSE_DEEP = hex("#B24B66");
    public static final Color BUTTER = hex("#FFF0B3");
    public static final Color SKY = hex("#CBE7F8");

    private Theme() {}

    public static void install() {
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#5E66C4"));
        FlatLightLaf.setup();
        Font base = UIManager.getFont("defaultFont");
        if (base != null) UIManager.put("defaultFont", base.deriveFont(13.5f));
        UIManager.put("Panel.background", BG);
        UIManager.put("Button.arc", 16);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Button.innerFocusWidth", 0);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("TabbedPane.tabHeight", 40);
        UIManager.put("TabbedPane.selectedBackground", SURFACE);
        UIManager.put("TabbedPane.underlineColor", LAVENDER_DEEP);
        UIManager.put("TabbedPane.tabAreaInsets", new Insets(0, 12, 0, 12));
        UIManager.put("Table.rowHeight", 32);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.gridColor", LINE);
        UIManager.put("Table.selectionBackground", LAVENDER);
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TableHeader.background", hex("#EFEDF8"));
        UIManager.put("TableHeader.foreground", INK);
        UIManager.put("SplitPane.background", BG);
        UIManager.put("OptionPane.background", SURFACE);
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
}
