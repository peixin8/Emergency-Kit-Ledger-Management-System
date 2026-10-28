package com.emergency.ledger;

/** Approximate inverse of the commonly used GCJ-02 offset for display on WGS84 map tiles. */
final class Gcj02 {
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private Gcj02() {}

    static double[] toWgs84(double longitude, double latitude) {
        if (longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271) {
            return new double[] {longitude, latitude};
        }
        double guessLon = longitude;
        double guessLat = latitude;
        for (int i = 0; i < 10; i++) {
            double[] forward = toGcj02(guessLon, guessLat);
            double errorLon = forward[0] - longitude;
            double errorLat = forward[1] - latitude;
            guessLon -= errorLon;
            guessLat -= errorLat;
            if (Math.abs(errorLon) < 1e-9 && Math.abs(errorLat) < 1e-9) break;
        }
        return new double[] {guessLon, guessLat};
    }

    private static double[] toGcj02(double longitude, double latitude) {
        double x = longitude - 105.0;
        double y = latitude - 35.0;
        double dLat = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        dLat += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        dLat += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        dLat += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        double dLon = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        dLon += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        dLon += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        dLon += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        double radLat = latitude / 180.0 * Math.PI;
        double magic = 1 - EE * Math.pow(Math.sin(radLat), 2);
        double sqrtMagic = Math.sqrt(magic);
        dLat = dLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        dLon = dLon * 180.0 / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[] {longitude + dLon, latitude + dLat};
    }
}
