package com.goride.client.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Icon nét mảnh (vẽ lại theo bộ Lucide — giấy phép ISC) thay cho emoji: sắc nét ở mọi cỡ chữ, cùng một
 * độ dày nét, tự tô theo màu chữ của nút. Mỗi icon là phần thân SVG trên khung 24×24.
 */
public final class Icons {
    private static final Map<String, String> SHAPES = new ConcurrentHashMap<>();
    /** Emoji cũ trong chữ nút/tab -> tên icon tương ứng (để chuyển dần mà không phải sửa từng chỗ). */
    private static final Map<String, String> EMOJI = new LinkedHashMap<>();

    static {
        SHAPES.put("clock", "<circle cx='12' cy='12' r='10'/><polyline points='12 6 12 12 16 14'/>");
        SHAPES.put("user", "<path d='M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2'/><circle cx='12' cy='7' r='4'/>");
        SHAPES.put("users", "<path d='M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2'/><circle cx='9' cy='7' r='4'/>"
                + "<path d='M22 21v-2a4 4 0 0 0-3-3.87'/><path d='M16 3.13a4 4 0 0 1 0 7.75'/>");
        SHAPES.put("logout", "<path d='M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4'/><polyline points='16 17 21 12 16 7'/>"
                + "<line x1='21' y1='12' x2='9' y2='12'/>");
        SHAPES.put("refresh", "<path d='M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8'/><path d='M21 3v5h-5'/>"
                + "<path d='M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16'/><path d='M8 16H3v5'/>");
        SHAPES.put("map", "<polygon points='3 6 9 3 15 6 21 3 21 18 15 21 9 18 3 21'/><line x1='9' y1='3' x2='9' y2='18'/>"
                + "<line x1='15' y1='6' x2='15' y2='21'/>");
        SHAPES.put("pin", "<path d='M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z'/><circle cx='12' cy='10' r='3'/>");
        SHAPES.put("locate", "<line x1='2' y1='12' x2='5' y2='12'/><line x1='19' y1='12' x2='22' y2='12'/>"
                + "<line x1='12' y1='2' x2='12' y2='5'/><line x1='12' y1='19' x2='12' y2='22'/>"
                + "<circle cx='12' cy='12' r='7'/><circle cx='12' cy='12' r='3'/>");
        SHAPES.put("bike", "<circle cx='18.5' cy='17.5' r='3.5'/><circle cx='5.5' cy='17.5' r='3.5'/>"
                + "<circle cx='15' cy='5' r='1'/><path d='M12 17.5V14l-3-3 4-3 2 3h2'/>");
        SHAPES.put("car", "<path d='M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3"
                + "c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2'/>"
                + "<circle cx='7' cy='17' r='2'/><path d='M9 17h6'/><circle cx='17' cy='17' r='2'/>");
        SHAPES.put("food", "<path d='M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2'/><path d='M7 2v20'/>"
                + "<path d='M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7'/>");
        SHAPES.put("store", "<path d='m2 7 4.41-4.41A2 2 0 0 1 7.83 2h8.34a2 2 0 0 1 1.42.59L22 7'/>"
                + "<path d='M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8'/><path d='M15 22v-4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4'/>"
                + "<path d='M2 7h20'/><path d='M22 7v3a2 2 0 0 1-2 2 2.7 2.7 0 0 1-2-1 2.7 2.7 0 0 1-2 1 2.7 2.7 0 0 1-2-1 "
                + "2.7 2.7 0 0 1-2 1 2.7 2.7 0 0 1-2-1 2.7 2.7 0 0 1-2 1 2.7 2.7 0 0 1-2-1 2.7 2.7 0 0 1-2 1 2 2 0 0 1-2-2V7'/>");
        SHAPES.put("ticket", "<path d='M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4"
                + "a2 2 0 0 0-2 2Z'/><path d='M13 5v2'/><path d='M13 17v2'/><path d='M13 11v2'/>");
        SHAPES.put("check", "<polyline points='20 6 9 17 4 12'/>");
        SHAPES.put("check-circle", "<circle cx='12' cy='12' r='10'/><path d='m9 12 2 2 4-4'/>");
        SHAPES.put("x", "<path d='M18 6 6 18'/><path d='m6 6 12 12'/>");
        SHAPES.put("plus", "<path d='M5 12h14'/><path d='M12 5v14'/>");
        SHAPES.put("minus", "<path d='M5 12h14'/>");
        SHAPES.put("trash", "<path d='M3 6h18'/><path d='M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6'/>"
                + "<path d='M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2'/>");
        SHAPES.put("cart", "<circle cx='8' cy='21' r='1'/><circle cx='19' cy='21' r='1'/>"
                + "<path d='M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12'/>");
        SHAPES.put("bell", "<path d='M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9'/><path d='M10.3 21a1.94 1.94 0 0 0 3.4 0'/>");
        SHAPES.put("chart", "<path d='M3 3v18h18'/><path d='M18 17V9'/><path d='M13 17V5'/><path d='M8 17v-3'/>");
        SHAPES.put("file", "<path d='M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z'/>"
                + "<path d='M14 2v4a2 2 0 0 0 2 2h4'/><path d='M10 9H8'/><path d='M16 13H8'/><path d='M16 17H8'/>");
        SHAPES.put("wallet", "<path d='M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3"
                + "a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1'/><path d='M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4'/>");
        SHAPES.put("star", "<polygon points='12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 "
                + "8.91 8.26 12 2'/>");
        SHAPES.put("tag", "<path d='M12.59 2.59A2 2 0 0 0 11.17 2H4a2 2 0 0 0-2 2v7.17a2 2 0 0 0 .59 1.42l8.7 8.7"
                + "a2.43 2.43 0 0 0 3.42 0l6.58-6.58a2.43 2.43 0 0 0 0-3.42z'/><circle cx='7.5' cy='7.5' r='1'/>");
        SHAPES.put("card", "<rect x='2' y='5' width='20' height='14' rx='2'/><line x1='2' y1='10' x2='22' y2='10'/>");
        SHAPES.put("receipt", "<path d='M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z'/>"
                + "<path d='M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8'/><path d='M12 17.5v-11'/>");
        SHAPES.put("arrow-left", "<path d='m12 19-7-7 7-7'/><path d='M19 12H5'/>");
        SHAPES.put("arrow-right", "<path d='M5 12h14'/><path d='m12 5 7 7-7 7'/>");
        SHAPES.put("route", "<circle cx='6' cy='19' r='3'/><path d='M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15'/>"
                + "<circle cx='18' cy='5' r='3'/>");
        SHAPES.put("pause", "<rect x='6' y='4' width='4' height='16' rx='1'/><rect x='14' y='4' width='4' height='16' rx='1'/>");
        SHAPES.put("play", "<polygon points='6 3 20 12 6 21 6 3'/>");
        SHAPES.put("search", "<circle cx='11' cy='11' r='8'/><path d='m21 21-4.3-4.3'/>");
        SHAPES.put("alert", "<path d='m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3'/>"
                + "<path d='M12 9v4'/><path d='M12 17h.01'/>");
        SHAPES.put("send", "<path d='m22 2-7 20-4-9-9-4Z'/><path d='M22 2 11 13'/>");
        SHAPES.put("eye", "<path d='M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z'/><circle cx='12' cy='12' r='3'/>");
        SHAPES.put("edit", "<path d='M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z'/>");
        SHAPES.put("image", "<rect x='3' y='3' width='18' height='18' rx='2'/><circle cx='9' cy='9' r='2'/>"
                + "<path d='m21 15-3.09-3.09a2 2 0 0 0-2.82 0L6 21'/>");
        SHAPES.put("phone", "<path d='M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6"
                + "A19.79 19.79 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11"
                + "L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92z'/>");

        String[][] e = {
                {"🕘", "clock"}, {"👤", "user"}, {"👥", "users"}, {"↻", "refresh"}, {"🗺", "map"}, {"📍", "pin"},
                {"🛵", "bike"}, {"🚗", "car"}, {"🍜", "food"}, {"🍽", "food"}, {"🏪", "store"}, {"🎟", "ticket"},
                {"✅", "check"}, {"✖", "x"}, {"✕", "x"}, {"🛒", "cart"}, {"🔔", "bell"}, {"📊", "chart"},
                {"📄", "file"}, {"💰", "wallet"}, {"⭐", "star"}, {"🏷", "tag"}, {"💳", "card"}, {"🧾", "receipt"},
                {"⏸", "pause"}, {"▶", "play"}, {"←", "arrow-left"}, {"🧭", "route"}, {"⚠️", "alert"}, {"⚠", "alert"},
                {"🎉", "check-circle"}, {"😔", "alert"}, {"📨", "send"},
        };
        for (String[] p : e) EMOJI.put(p[0], p[1]);
    }

    private static final Map<String, FlatSVGIcon> CACHE = new ConcurrentHashMap<>();

    private Icons() {}

    /** Icon tên name, cỡ size px, tô màu color. Tên lạ thì trả null (nút vẫn hiện chữ bình thường). */
    public static FlatSVGIcon get(String name, int size, Color color) {
        String body = name == null ? null : SHAPES.get(name);
        if (body == null) return null;
        String key = name + "@" + size + "#" + color.getRGB();
        return CACHE.computeIfAbsent(key, k -> {
            String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' "
                    + "stroke='#000000' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" + body + "</svg>";
            try {
                FlatSVGIcon icon = new FlatSVGIcon(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)))
                        .derive(size, size);
                icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
                return icon;
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    /**
     * Tách emoji ở đầu chữ: "🕘 Lịch sử" -> {"clock", "Lịch sử"}. Chữ kết thúc bằng " →" thì icon mũi tên
     * đặt bên phải: {"arrow-right>", "Tiếp tục"}. Không có emoji thì {null, chữ}.
     */
    public static String[] split(String text) {
        if (text == null) return new String[]{null, null};
        String t = text.strip();
        if (t.endsWith("→")) return new String[]{"arrow-right>", t.substring(0, t.length() - 1).strip()};
        for (Map.Entry<String, String> en : EMOJI.entrySet()) {
            if (t.startsWith(en.getKey())) {
                String rest = t.substring(en.getKey().length()).replace("️", "").strip();
                return new String[]{en.getValue(), rest};
            }
        }
        return new String[]{null, text};
    }

    /** Icon cho một emoji đơn lẻ (thông báo nổi, thẻ trang chủ). */
    public static String forEmoji(String emoji) {
        return emoji == null ? null : EMOJI.get(emoji.strip());
    }
}
