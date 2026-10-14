package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Bảng đối soát thu nhập theo tháng: tổng doanh thu, chiết khấu nền tảng, thuế khấu trừ, số tiền được
 * chi trả và hạn chi trả; kèm nút xuất ra file HTML để in hoặc lưu PDF. Dùng chung cho tài xế và chủ quán.
 */
public class StatementView extends JPanel {
    private static final DateTimeFormatter MONTH_VI = DateTimeFormatter.ofPattern("MM/yyyy");

    private final Net net;
    private final String command;
    private final JComboBox<YearMonth> month = new JComboBox<>();
    private final JLabel summary = new JLabel();
    private final DataTable lines;
    private JsonObject data;

    /** command = DRIVER_STATEMENT hoặc MERCHANT_STATEMENT. */
    public StatementView(Net net, String command) {
        super(new BorderLayout(0, 10));
        this.net = net;
        this.command = command;
        setBorder(new EmptyBorder(12, 12, 12, 12));
        boolean driver = command.startsWith("DRIVER");
        lines = new DataTable("done_at|Hoàn thành|time", "title|Nội dung", "payment_method|Khách trả|status",
                "gross|Doanh thu|money", "commission|Chiết khấu|money", "earned|Được hưởng|money",
                driver ? "cash_held|Tiền mặt đã thu|money" : "payment_method|Hình thức|status");
        lines.widths(120, 230, 90, 100, 100, 110, 120);

        YearMonth now = YearMonth.now();
        for (int i = 0; i < 12; i++) month.addItem(now.minusMonths(i));
        month.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                YearMonth ym = (YearMonth) v;
                String text = ym == null ? "" : "Tháng " + ym.format(MONTH_VI) + (ym.equals(now) ? " (đang diễn ra)" : "");
                return super.getListCellRendererComponent(l, text, i, s, f);
            }
        });
        month.addActionListener(e -> load());

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(Ui.label("Đối soát thu nhập", 15f, Font.BOLD));
        left.add(month);
        left.add(Ui.button("↻ Làm mới", Theme.SURFACE, this::load));
        bar.add(left, BorderLayout.WEST);
        bar.add(Ui.button("📄 Xuất hoá đơn tháng", Theme.MINT, this::export), BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        Ui.Card card = new Ui.Card(new BorderLayout(), Theme.SURFACE);
        summary.setVerticalAlignment(SwingConstants.TOP);
        card.add(summary);
        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);
        card.setPreferredSize(new Dimension(430, 100));
        body.add(card, BorderLayout.WEST);
        JPanel table = new JPanel(new BorderLayout(0, 6));
        table.setOpaque(false);
        table.add(Ui.label("Chi tiết từng " + (driver ? "chuyến / đơn" : "đơn"), 13f, Font.BOLD), BorderLayout.NORTH);
        table.add(lines.scroll());
        body.add(table);
        add(body);
    }

    public void load() {
        YearMonth ym = (YearMonth) month.getSelectedItem();
        if (ym == null) return;
        net.quiet(command, Json.obj("month", ym.toString()), r -> {
            data = r.getAsJsonObject();
            lines.setRows(data.get("lines"));
            summary.setText(summaryHtml(data));
        });
    }

    // ---------- Tóm tắt trên màn hình ----------

    private static String summaryHtml(JsonObject o) {
        boolean driver = "DRIVER".equals(Json.str(o, "kind"));
        long payout = Json.lng(o, "payout", 0);
        StringBuilder h = new StringBuilder("<html><div style='width:330px'>");
        h.append("<b style='font-size:13px'>Kỳ ").append(vi(Json.str(o, "period_from"))).append(" – ")
                .append(vi(Json.str(o, "period_to"))).append("</b><br>")
                .append("<span style='color:#5A6472'>").append(Json.integer(o, "count", 0))
                .append(driver ? " chuyến/đơn hoàn thành" : " đơn đã giao").append("</span><br><br>");
        h.append("<table cellpadding='3' cellspacing='0'>");
        row(h, "Doanh thu" + (driver ? " (giá gốc)" : " (tiền món)"), Json.lng(o, "gross", 0), false);
        row(h, "− Chiết khấu nền tảng " + pct(Json.dbl(o, "commission_rate", 0)), -Json.lng(o, "commission", 0), false);
        row(h, "= Được hưởng", Json.lng(o, "earned", 0), true);
        String taxLabel = "− Thuế GTGT " + pct(Json.dbl(o, "vat_rate", 0)) + " + TNCN " + pct(Json.dbl(o, "pit_rate", 0));
        row(h, taxLabel, -Json.lng(o, "tax", 0), false);
        if (Json.bool(o, "has_cash", false)) row(h, "− Tiền mặt đã thu của khách", -Json.lng(o, "cash_held", 0), false);
        h.append("<tr><td colspan='2'><hr></td></tr>");
        h.append("<tr><td width='205'><b>").append(payout >= 0 ? "SỐ TIỀN ĐƯỢC CHI TRẢ" : "SỐ TIỀN PHẢI NỘP LẠI")
                .append("</b></td><td width='120' align='right'><b style='font-size:14px;color:")
                .append(payout >= 0 ? "#13703F" : "#B3161B").append("'>")
                .append(Ui.money(Math.abs(payout))).append("</b></td></tr></table><br>");

        if (!Json.bool(o, "taxable", false)) {
            h.append("<span style='color:#5A6472'>Miễn thuế: doanh thu được hưởng lũy kế năm ")
                    .append(Ui.money(Json.dbl(o, "ytd_earned", 0))).append(" chưa vượt ngưỡng ")
                    .append(Ui.money(Json.dbl(o, "tax_threshold", 0))).append(".</span><br>");
        }
        if (payout < 0) {
            h.append("<span style='color:#B3161B'>Tiền mặt bạn thu của khách nhiều hơn phần bạn được hưởng — "
                    + "hãy nộp lại phần chênh lệch cho GoRide.</span><br>");
        }
        h.append("<br><b>Hạn chi trả:</b> ").append(vi(Json.str(o, "payout_date"))).append("<br>")
                .append("<span style='color:#0F4FD6'>").append(Ui.esc(Json.str(o, "status", ""))).append("</span>");
        return h.append("</div></html>").toString();
    }

    private static void row(StringBuilder h, String label, long v, boolean bold) {
        String money = (v < 0 ? "−" : "") + Ui.money(Math.abs(v));
        h.append("<tr><td width='205'>").append(bold ? "<b>" : "").append(Ui.esc(label)).append(bold ? "</b>" : "")
                .append("</td><td width='120' align='right'>").append(bold ? "<b>" : "").append(money)
                .append(bold ? "</b>" : "").append("</td></tr>");
    }

    // ---------- Xuất hoá đơn ----------

    private void export() {
        if (data == null) {
            Ui.info(this, "Đang tải số liệu, thử lại sau một nhịp");
            return;
        }
        boolean driver = "DRIVER".equals(Json.str(data, "kind"));
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Lưu hoá đơn tháng");
        fc.setFileFilter(new FileNameExtensionFilter("Trang HTML (mở bằng trình duyệt để in / lưu PDF)", "html"));
        fc.setSelectedFile(new File("hoa-don-" + (driver ? "tai-xe" : "quan") + "-" + Json.str(data, "month") + ".html"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".html")) f = new File(f.getParentFile(), f.getName() + ".html");
        try {
            Files.writeString(f.toPath(), invoiceHtml(data), StandardCharsets.UTF_8);
            Toast.info(this, "📄", "Đã xuất hoá đơn", f.getName() + " — mở bằng trình duyệt để in hoặc lưu PDF.", Theme.MINT);
        } catch (Exception e) {
            Ui.info(this, "Không lưu được file: " + e.getMessage());
        }
    }

    /** Trang hoá đơn đầy đủ, in được từ trình duyệt. */
    static String invoiceHtml(JsonObject o) {
        boolean driver = "DRIVER".equals(Json.str(o, "kind"));
        long payout = Json.lng(o, "payout", 0);
        String ym = YearMonth.parse(Json.str(o, "month")).format(MONTH_VI);
        StringBuilder rows = new StringBuilder();
        int i = 1;
        for (JsonElement e : o.getAsJsonArray("lines")) {
            JsonObject l = e.getAsJsonObject();
            rows.append("<tr><td>").append(i++).append("</td><td>").append(esc(Json.str(l, "done_at", "")))
                    .append("</td><td>").append(esc(Json.str(l, "title", ""))).append("</td><td>")
                    .append(Ui.vi(Json.str(l, "payment_method", ""))).append("</td><td class=n>")
                    .append(Ui.money(Json.dbl(l, "gross", 0))).append("</td><td class=n>")
                    .append(Ui.money(Json.dbl(l, "commission", 0))).append("</td><td class=n>")
                    .append(Ui.money(Json.dbl(l, "earned", 0))).append("</td>");
            if (driver) rows.append("<td class=n>").append(Ui.money(Json.dbl(l, "cash_held", 0))).append("</td>");
            rows.append("</tr>");
        }
        if (i == 1) rows.append("<tr><td colspan=").append(driver ? 8 : 7)
                .append(" style='text-align:center;color:#888'>Không có phát sinh trong kỳ</td></tr>");

        StringBuilder shops = new StringBuilder();
        if (o.has("shops")) for (JsonElement e : o.getAsJsonArray("shops")) {
            JsonObject s = e.getAsJsonObject();
            shops.append("<div>").append(esc(Json.str(s, "name", ""))).append(" — ")
                    .append(esc(Json.str(s, "address", ""))).append("</div>");
        }

        String tax = Json.bool(o, "taxable", false)
                ? Ui.money(Json.dbl(o, "vat", 0)) + " GTGT + " + Ui.money(Json.dbl(o, "pit", 0)) + " TNCN"
                : "Miễn — doanh thu được hưởng lũy kế năm " + Ui.money(Json.dbl(o, "ytd_earned", 0))
                  + " chưa vượt ngưỡng " + Ui.money(Json.dbl(o, "tax_threshold", 0));

        return """
                <!DOCTYPE html><html lang="vi"><head><meta charset="utf-8">
                <title>Hoá đơn %s tháng %s</title>
                <style>
                  body{font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;color:#121619;max-width:900px;margin:32px auto;padding:0 24px}
                  h1{color:#0F4FD6;margin:0}.sub{color:#5A6472}
                  .box{border:1px solid #C9CED6;padding:16px 20px;margin:18px 0}
                  table{width:100%%;border-collapse:collapse;font-size:13px}
                  th,td{padding:7px 8px;border-bottom:1px solid #E3E7EC;text-align:left}th{background:#F0F2F5}
                  td.n,th.n{text-align:right;white-space:nowrap}
                  .sum td{border:none;padding:5px 0;font-size:14px}.total td{font-size:17px;font-weight:700;border-top:2px solid #121619;padding-top:10px}
                  .pos{color:#13703F}.neg{color:#B3161B}.note{font-size:12px;color:#5A6472;line-height:1.6}
                  @media print{body{margin:0}.box{break-inside:avoid}}
                </style></head><body>
                <h1>GoRide</h1><div class="sub">Nền tảng đặt xe &amp; giao đồ ăn</div>
                <h2>HOÁ ĐƠN ĐỐI SOÁT THU NHẬP THÁNG %s</h2>
                <div class="box">
                  <div><b>%s:</b> %s — %s</div>%s
                  <div><b>Kỳ đối soát:</b> %s đến %s &nbsp;·&nbsp; <b>Ngày lập:</b> %s</div>
                  <div><b>Trạng thái:</b> %s</div>
                </div>
                <table><tr><th>#</th><th>Hoàn thành</th><th>Nội dung</th><th>Khách trả</th>
                  <th class=n>Doanh thu</th><th class=n>Chiết khấu</th><th class=n>Được hưởng</th>%s</tr>%s</table>
                <div class="box"><table class="sum">
                  <tr><td>Tổng doanh thu (%d %s)</td><td class=n>%s</td></tr>
                  <tr><td>Chiết khấu nền tảng %s</td><td class=n>−%s</td></tr>
                  <tr><td><b>Doanh thu được hưởng</b></td><td class=n><b>%s</b></td></tr>
                  <tr><td>Thuế khấu trừ (GTGT %s + TNCN %s)</td><td class=n>%s</td></tr>%s
                  <tr class="total"><td>%s</td><td class="n %s">%s</td></tr>
                </table>
                <p><b>Hạn chi trả:</b> ngày %s (ngày %d của tháng kế tiếp)</p></div>
                <div class="note">
                  Căn cứ tính thuế: Thông tư 40/2021/TT-BTC về thuế đối với hộ, cá nhân kinh doanh — nền tảng khấu trừ và nộp thay
                  trên phần doanh thu được hưởng; ngưỡng doanh thu không chịu thuế theo Luật thuế GTGT 2024. Thẻ giảm giá là khuyến mãi
                  của nền tảng nên không trừ vào thu nhập của %s. Tỷ lệ chiết khấu, thuế và ngày chi trả là tham số cấu hình của hệ thống,
                  cần đối chiếu quy định hiện hành trước khi áp dụng thực tế.<br>
                  Tài liệu này là bảng đối soát nội bộ, không thay thế hoá đơn giá trị gia tăng điện tử theo Nghị định 123/2020/NĐ-CP.
                </div></body></html>
                """.formatted(
                driver ? "tài xế" : "quán", ym, ym,
                driver ? "Tài xế" : "Chủ quán", esc(Json.str(o, "full_name", "")), esc(Json.str(o, "phone", "")),
                shops.length() == 0 ? "" : "<div><b>Quán:</b>" + shops + "</div>",
                vi(Json.str(o, "period_from")), vi(Json.str(o, "period_to")), vi(Json.str(o, "issued_at")),
                esc(Json.str(o, "status", "")),
                driver ? "<th class=n>Tiền mặt đã thu</th>" : "", rows,
                Json.integer(o, "count", 0), driver ? "chuyến/đơn" : "đơn", Ui.money(Json.dbl(o, "gross", 0)),
                pct(Json.dbl(o, "commission_rate", 0)), Ui.money(Json.dbl(o, "commission", 0)),
                Ui.money(Json.dbl(o, "earned", 0)),
                pct(Json.dbl(o, "vat_rate", 0)), pct(Json.dbl(o, "pit_rate", 0)), esc(tax),
                Json.bool(o, "has_cash", false)
                        ? "<tr><td>Tiền mặt đã thu của khách (trừ vào kỳ này)</td><td class=n>−"
                          + Ui.money(Json.dbl(o, "cash_held", 0)) + "</td></tr>" : "",
                payout >= 0 ? "SỐ TIỀN ĐƯỢC CHI TRẢ" : "SỐ TIỀN PHẢI NỘP LẠI CHO GORIDE",
                payout >= 0 ? "pos" : "neg", Ui.money(Math.abs(payout)),
                vi(Json.str(o, "payout_date")), Json.integer(o, "payout_day", 10),
                driver ? "tài xế" : "quán");
    }

    private static String pct(double r) {
        double v = Math.round(r * 10000) / 100.0; // tối đa 2 chữ số lẻ, tránh 19,999999...
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',')) + "%";
    }

    /** yyyy-MM-dd -> dd/MM/yyyy */
    private static String vi(String iso) {
        if (iso == null || iso.length() < 10) return iso == null ? "" : iso;
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
    }

    private static String esc(String s) { return Ui.esc(s); }
}
