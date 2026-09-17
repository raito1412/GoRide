package com.goride.common;

import java.util.ArrayList;
import java.util.List;

public final class Geo {
    private Geo() {}

    /** Khoảng cách đường chim bay (km). */
    public static double km(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Giải mã encoded polyline (định dạng Google, Mapbox geometries=polyline). Trả về [lat, lng]. */
    public static List<double[]> decodePolyline(String encoded) {
        List<double[]> out = new ArrayList<>();
        int index = 0, lat = 0, lng = 0;
        while (index < encoded.length()) {
            int[] r = next(encoded, index);
            lat += r[0];
            index = r[1];
            r = next(encoded, index);
            lng += r[0];
            index = r[1];
            out.add(new double[]{lat / 1e5, lng / 1e5});
        }
        return out;
    }

    private static int[] next(String s, int index) {
        int result = 0, shift = 0, b;
        do {
            b = s.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20 && index < s.length());
        int value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[]{value, index};
    }
}
