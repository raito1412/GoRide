package com.goride.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Công tắc bật/tắt kiểu gạt: núm trượt qua lại trong rãnh bo tròn, kèm chữ bên phải.
 * Bấm hoặc kéo núm đều đổi trạng thái. setSelected() chỉ đổi hình, không gọi lại handler,
 * nên dùng được để phản ánh trạng thái do server trả về mà không sinh vòng lặp.
 */
public class ToggleSwitch extends JComponent {
    private static final int TRACK_W = 54, TRACK_H = 28, PAD = 3, GAP = 12;

    private final String onText, offText;
    private boolean on;
    /** Vị trí núm 0..1, có hoạt ảnh để thấy rõ động tác gạt. */
    private float pos;
    private boolean dragging;
    private Timer anim; // không final: listener bên trong tự tham chiếu tới chính nó
    private Consumer<Boolean> onToggle = b -> { };

    public ToggleSwitch(String onText, String offText, boolean initialOn) {
        this.onText = onText;
        this.offText = offText;
        this.on = initialOn;
        this.pos = initialOn ? 1 : 0;
        setFont(getFont() == null ? UIManager.getFont("defaultFont") : getFont());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);

        anim = new Timer(15, e -> {
            float target = this.on ? 1f : 0f;
            if (pos < target) pos = Math.min(target, pos + 0.15f);
            else if (pos > target) pos = Math.max(target, pos - 0.15f);
            else {
                anim.stop();
                return;
            }
            repaint();
        });

        MouseAdapter mouse = new MouseAdapter() {
            private int pressX;
            private boolean moved;

            @Override
            public void mousePressed(MouseEvent e) {
                if (!isEnabled()) return;
                pressX = e.getX();
                moved = false;
                requestFocusInWindow();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!isEnabled()) return;
                if (Math.abs(e.getX() - pressX) > 3) moved = true;
                if (!moved) return;
                dragging = true;
                anim.stop();
                pos = Math.max(0, Math.min(1, (e.getX() - PAD - knob() / 2f) / (float) (TRACK_W - 2 * PAD - knob())));
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isEnabled()) return;
                boolean want = moved ? pos > 0.5f : !ToggleSwitch.this.on;
                dragging = false;
                toggleTo(want, true);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (isEnabled() && (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER))
                    toggleTo(!ToggleSwitch.this.on, true);
            }
        });
    }

    /** Người dùng gạt tay thì mới báo ra ngoài. */
    public void onToggle(Consumer<Boolean> handler) {
        onToggle = handler == null ? b -> { } : handler;
    }

    public boolean isSelected() { return on; }

    /** Đổi trạng thái hiển thị mà không gọi handler. */
    public void setSelected(boolean value) { toggleTo(value, false); }

    private void toggleTo(boolean value, boolean notify) {
        boolean changed = value != on;
        on = value;
        anim.start();
        revalidate();
        repaint();
        if (changed && notify) onToggle.accept(on);
    }

    private int knob() { return TRACK_H - 2 * PAD; }

    private String text() { return on ? onText : offText; }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont().deriveFont(Font.BOLD, 13.5f));
        int w = TRACK_W + GAP + Math.max(fm.stringWidth(onText), fm.stringWidth(offText));
        return new Dimension(w, Math.max(TRACK_H, fm.getHeight()));
    }

    @Override
    public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override
    public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (!isEnabled()) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));

        int top = (getHeight() - TRACK_H) / 2;
        g.setColor(Theme.mix(Theme.LINE, Theme.MINT_DEEP, pos));
        g.fillRect(0, top, TRACK_W, TRACK_H);

        int travel = TRACK_W - 2 * PAD - knob();
        int x = PAD + Math.round(travel * pos);
        g.setColor(Color.WHITE);
        g.fillRect(x, top + PAD, knob(), knob());

        if (hasFocus()) {
            g.setColor(Theme.LAVENDER_DEEP);
            g.drawRect(0, top, TRACK_W - 1, TRACK_H - 1);
        }

        g.setFont(getFont().deriveFont(Font.BOLD, 13.5f));
        g.setColor(on ? Theme.MINT_DEEP : Theme.MUTED);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text(), TRACK_W + GAP, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }
}
