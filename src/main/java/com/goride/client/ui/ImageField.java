package com.goride.client.ui;

import com.goride.client.Net;
import com.goride.common.Json;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Ô chọn ảnh: bấm "Chọn ảnh" -> thu nhỏ + gửi lên server ngay -> giữ lại image_id để gửi kèm hồ sơ.
 * Dùng cho ảnh quán và ảnh từng món.
 */
public class ImageField extends JPanel {
    private final Net net;
    private final int boxW, boxH;
    private final JLabel preview = new JLabel("", SwingConstants.CENTER);
    private final JButton pickBtn, clearBtn;
    private Long imageId;
    private boolean uploading;
    private Runnable onChange = () -> { };

    public ImageField(Net net, String hint, int boxW, int boxH) {
        super(new BorderLayout(8, 0));
        this.net = net;
        this.boxW = boxW;
        this.boxH = boxH;
        setOpaque(false);

        preview.setPreferredSize(new Dimension(boxW, boxH));
        preview.setMinimumSize(new Dimension(boxW, boxH));
        preview.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        preview.setOpaque(true);
        preview.setBackground(Theme.ROW_ALT);
        preview.setForeground(Theme.MUTED);
        preview.setFont(preview.getFont().deriveFont(11f));
        add(preview, BorderLayout.WEST);

        pickBtn = Ui.button("Chọn ảnh", Theme.SKY, this::pick);
        clearBtn = Ui.button("Bỏ ảnh", Theme.SURFACE, this::clear);
        JPanel side = new JPanel(new GridLayout(0, 1, 0, 4));
        side.setOpaque(false);
        side.add(pickBtn);
        side.add(clearBtn);
        side.add(Ui.muted("<html><span style='font-size:9px'>" + Ui.esc(hint) + "</span></html>"));
        add(side);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, boxH + 8));
        render();
    }

    public void onChange(Runnable r) { onChange = r; }

    /** id ảnh đã tải lên, null nếu chưa chọn ảnh. */
    public Long imageId() { return imageId; }

    public boolean busy() { return uploading; }

    /** Hiển thị ảnh đã có sẵn trên server (khi xem lại hồ sơ cũ). */
    public void setImageId(Long id) {
        imageId = id;
        render();
    }

    public void clear() {
        imageId = null;
        render();
        onChange.run();
    }

    private void pick() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Chọn ảnh (JPG / PNG)");
        fc.setFileFilter(new FileNameExtensionFilter("Ảnh JPG, PNG", "jpg", "jpeg", "png"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();

        uploading = true;
        render();
        new Thread(() -> {
            JsonObject payload;
            try {
                payload = ImageStore.encode(file);
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    uploading = false;
                    render();
                    Ui.info(this, "Không dùng được ảnh này: " + e.getMessage());
                });
                return;
            }
            try {
                long id = Json.lng(net.call("IMAGE_UPLOAD", payload).getAsJsonObject(), "id", 0);
                SwingUtilities.invokeLater(() -> {
                    uploading = false;
                    imageId = id > 0 ? id : null;
                    render();
                    onChange.run();
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    uploading = false;
                    render();
                    Ui.info(this, "Tải ảnh lên thất bại: " + e.getMessage());
                });
            }
        }, "image-upload").start();
    }

    private void render() {
        pickBtn.setEnabled(!uploading);
        clearBtn.setEnabled(!uploading && imageId != null);
        preview.setIcon(null);
        if (uploading) {
            preview.setText("Đang tải lên...");
        } else if (imageId == null) {
            preview.setText("<html><div style='text-align:center'>Chưa có ảnh</div></html>");
        } else {
            preview.setText("...");
            ImageStore.load(net, imageId, boxW - 4, boxH - 4, icon -> {
                preview.setText("");
                preview.setIcon(icon);
            });
        }
    }
}
