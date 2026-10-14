package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Json;
import com.google.gson.JsonObject;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Ảnh quán / ảnh món. Ảnh nằm trên server, client tải theo id (IMAGE_GET) rồi nhớ lại để khỏi tải lại.
 * Khi người dùng chọn ảnh từ máy, ảnh được thu nhỏ + nén JPEG trước khi gửi lên cho nhẹ đường truyền.
 */
public final class ImageStore {
    /** Trùng với Images.MAX_BYTES phía server. */
    private static final int MAX_UPLOAD_BYTES = 800 * 1024;
    private static final int[] TRY_DIMENSIONS = {1000, 800, 640, 480, 360};
    private static final Map<String, ImageIcon> CACHE = new ConcurrentHashMap<>();

    private ImageStore() {}

    /** Tải ảnh #id, thu về khung w×h rồi gọi lại trên luồng giao diện. Lỗi thì không gọi gì cả. */
    public static void load(Net net, long id, int w, int h, Consumer<ImageIcon> onReady) {
        String key = id + "@" + w + "x" + h;
        ImageIcon cached = CACHE.get(key);
        if (cached != null) {
            onReady.accept(cached);
            return;
        }
        net.quiet("IMAGE_GET", Json.obj("id", id), r -> {
            try {
                JsonObject o = r.getAsJsonObject();
                byte[] bytes = Base64.getDecoder().decode(Json.str(o, "data"));
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (img == null) return;
                ImageIcon icon = new ImageIcon(fit(img, w, h));
                CACHE.put(key, icon);
                onReady.accept(icon);
            } catch (RuntimeException | IOException ignored) {
                // ảnh hỏng thì cứ để trống, không làm phiền người dùng
            }
        });
    }

    /** Thu nhỏ vừa khung w×h, giữ nguyên tỉ lệ (không phóng to ảnh nhỏ). */
    public static Image fit(BufferedImage src, int w, int h) {
        double scale = Math.min(w / (double) src.getWidth(), h / (double) src.getHeight());
        if (scale >= 1) return src;
        return src.getScaledInstance((int) Math.round(src.getWidth() * scale),
                (int) Math.round(src.getHeight() * scale), Image.SCALE_SMOOTH);
    }

    /**
     * Đọc ảnh từ file, nén thành JPEG đủ nhỏ để gửi lên server.
     * Trả về {"mime":"image/jpeg","data":"<base64>"}; ném IOException nếu file không phải ảnh.
     */
    public static JsonObject encode(File file) throws IOException {
        BufferedImage src = ImageIO.read(file);
        if (src == null) throw new IOException("Không đọc được ảnh: " + file.getName());
        for (int dim : TRY_DIMENSIONS) {
            byte[] jpeg = toJpeg(src, dim, 0.82f);
            if (jpeg.length <= MAX_UPLOAD_BYTES)
                return Json.obj("mime", "image/jpeg", "data", Base64.getEncoder().encodeToString(jpeg));
        }
        byte[] last = toJpeg(src, TRY_DIMENSIONS[TRY_DIMENSIONS.length - 1], 0.6f);
        if (last.length > MAX_UPLOAD_BYTES) throw new IOException("Ảnh quá lớn, hãy chọn ảnh khác");
        return Json.obj("mime", "image/jpeg", "data", Base64.getEncoder().encodeToString(last));
    }

    private static byte[] toJpeg(BufferedImage src, int maxDim, float quality) throws IOException {
        Image scaled = fit(src, maxDim, maxDim);
        BufferedImage out = new BufferedImage(scaled.getWidth(null), scaled.getHeight(null), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE); // JPEG không có kênh trong suốt -> nền trắng cho ảnh PNG
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(scaled, 0, 0, null);
        g.dispose();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IOException("Máy không hỗ trợ ghi ảnh JPEG");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(buffer)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(out, null, null), param);
        } finally {
            writer.dispose();
        }
        return buffer.toByteArray();
    }
}
