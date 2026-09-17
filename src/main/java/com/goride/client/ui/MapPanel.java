package com.goride.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCursor;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Bản đồ thật (tile OpenStreetMap) + marker + đường đi.
 * Tuyến đường lấy từ Mapbox Directions do server trả về (mảng points [[lat,lng],...]).
 */
public class MapPanel extends JPanel {
    public record Marker(double lat, double lng, Color color, String label) {}

    private final JXMapViewer map = new JXMapViewer();
    private final Map<String, Marker> markers = new LinkedHashMap<>();
    private final List<GeoPosition> route = new ArrayList<>();
    private Color routeColor = Theme.LAVENDER_DEEP;
    private BiConsumer<Double, Double> onPick;
    private final JLabel hint = new JLabel();

    public MapPanel(double lat, double lng) {
        super(new BorderLayout());
        DefaultTileFactory tiles = new DefaultTileFactory(new OSMTileFactoryInfo("OpenStreetMap", "https://tile.openstreetmap.org"));
        tiles.setThreadPoolSize(4);
        tiles.setUserAgent("GoRide-Student-Project/1.0");
        map.setTileFactory(tiles);
        map.setZoom(4);
        map.setAddressLocation(new GeoPosition(lat, lng));

        PanMouseInputListener pan = new PanMouseInputListener(map);
        map.addMouseListener(pan);
        map.addMouseMotionListener(pan);
        map.addMouseWheelListener(new ZoomMouseWheelListenerCursor(map));
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onPick != null && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    GeoPosition p = map.convertPointToGeoPosition(e.getPoint());
                    onPick.accept(p.getLatitude(), p.getLongitude());
                }
            }
        });
        map.setOverlayPainter((g, m, w, h) -> paintOverlay(g));

        JLayeredPane layers = new JLayeredPane() {
            @Override
            public void doLayout() {
                map.setBounds(0, 0, getWidth(), getHeight());
                Dimension z = zoomBar.getPreferredSize();
                zoomBar.setBounds(getWidth() - z.width - 12, 12, z.width, z.height);
                Dimension hs = hint.getPreferredSize();
                hint.setBounds(12, getHeight() - hs.height - 12, hs.width, hs.height);
            }
        };
        layers.add(map, JLayeredPane.DEFAULT_LAYER);
        layers.add(zoomBar, JLayeredPane.PALETTE_LAYER);
        hint.setOpaque(true);
        hint.setBackground(new Color(255, 255, 255, 230));
        hint.setForeground(Theme.MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        hint.setVisible(false);
        layers.add(hint, JLayeredPane.PALETTE_LAYER);
        add(layers);

        JLabel credit = Ui.muted("  © OpenStreetMap contributors · Tuyến đường: Mapbox");
        credit.setFont(credit.getFont().deriveFont(11f));
        add(credit, BorderLayout.SOUTH);
        setBorder(BorderFactory.createLineBorder(Theme.LINE));
    }

    private final JPanel zoomBar = buildZoomBar();

    private JPanel buildZoomBar() {
        JPanel p = new JPanel(new GridLayout(3, 1, 0, 4));
        p.setOpaque(false);
        p.add(mapButton("+", "Phóng to", () -> map.setZoom(Math.max(0, map.getZoom() - 1))));
        p.add(mapButton("-", "Thu nhỏ", () -> map.setZoom(Math.min(15, map.getZoom() + 1))));
        p.add(mapButton("◎", "Căn vừa các điểm", this::fit));
        return p;
    }

    private static JButton mapButton(String text, String tip, Runnable action) {
        JButton b = Ui.button(text, Theme.SURFACE, action);
        b.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        b.setPreferredSize(new Dimension(34, 32));
        b.setToolTipText(tip);
        return b;
    }

    // ---------- API ----------

    /** Bật chế độ bấm lên bản đồ để chọn toạ độ (null để tắt). */
    public void onPick(String hintText, BiConsumer<Double, Double> handler) {
        onPick = handler;
        hint.setText(hintText == null ? "" : hintText);
        hint.setVisible(handler != null && hintText != null);
        revalidate();
        repaint();
    }

    public void setMarker(String key, double lat, double lng, Color color, String label) {
        markers.put(key, new Marker(lat, lng, color, label));
        map.repaint();
    }

    public void removeMarker(String key) {
        if (markers.remove(key) != null) map.repaint();
    }

    public boolean hasMarker(String key) { return markers.containsKey(key); }

    /** points: [[lat,lng],...] */
    public void setRoute(JsonElement points, Color color) {
        route.clear();
        routeColor = color;
        if (points != null && points.isJsonArray()) {
            for (JsonElement e : points.getAsJsonArray()) {
                JsonArray p = e.getAsJsonArray();
                route.add(new GeoPosition(p.get(0).getAsDouble(), p.get(1).getAsDouble()));
            }
        }
        map.repaint();
    }

    public void clearRoute() {
        route.clear();
        map.repaint();
    }

    public void clearAll() {
        markers.clear();
        route.clear();
        map.repaint();
    }

    public void center(double lat, double lng) {
        map.setAddressLocation(new GeoPosition(lat, lng));
    }

    /** Thu phóng vừa tất cả marker + tuyến đường. */
    public void fit() {
        Set<GeoPosition> all = new HashSet<>(route);
        for (Marker m : markers.values()) all.add(new GeoPosition(m.lat, m.lng));
        if (all.isEmpty()) return;
        if (all.size() == 1) {
            map.setZoom(4);
            map.setAddressLocation(all.iterator().next());
        } else {
            map.zoomToBestFit(all, 0.75);
        }
    }

    // ---------- Vẽ ----------

    private void paintOverlay(Graphics2D g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Rectangle view = map.getViewportBounds();
        g.translate(-view.x, -view.y);

        if (route.size() > 1) {
            Path2D path = new Path2D.Double();
            for (int i = 0; i < route.size(); i++) {
                Point2D pt = map.getTileFactory().geoToPixel(route.get(i), map.getZoom());
                if (i == 0) path.moveTo(pt.getX(), pt.getY());
                else path.lineTo(pt.getX(), pt.getY());
            }
            g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 220));
            g.draw(path);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(routeColor);
            g.draw(path);
        }

        Font font = getFont().deriveFont(Font.BOLD, 12f);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        for (Marker m : markers.values()) {
            Point2D pt = map.getTileFactory().geoToPixel(new GeoPosition(m.lat, m.lng), map.getZoom());
            int x = (int) pt.getX(), y = (int) pt.getY();
            // ghim
            g.setColor(new Color(0, 0, 0, 40));
            g.fillOval(x - 7, y - 3, 14, 6);
            Path2D pin = new Path2D.Double();
            pin.moveTo(x, y);
            pin.curveTo(x - 4, y - 10, x - 12, y - 14, x - 12, y - 24);
            pin.curveTo(x - 12, y - 32, x - 6, y - 37, x, y - 37);
            pin.curveTo(x + 6, y - 37, x + 12, y - 32, x + 12, y - 24);
            pin.curveTo(x + 12, y - 14, x + 4, y - 10, x, y);
            g.setColor(m.color);
            g.fill(pin);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f));
            g.draw(pin);
            g.fillOval(x - 4, y - 28, 8, 8);
            // nhãn
            if (m.label != null && !m.label.isEmpty()) {
                int w = fm.stringWidth(m.label) + 14, h = fm.getHeight() + 4;
                int lx = x - w / 2, ly = y - 42 - h;
                g.setColor(Color.WHITE);
                g.fillRoundRect(lx, ly, w, h, h, h);
                g.setColor(m.color.darker());
                g.drawRoundRect(lx, ly, w, h, h, h);
                g.setColor(Theme.INK);
                g.drawString(m.label, lx + 7, ly + fm.getAscent() + 2);
            }
        }
        g.dispose();
    }
}
