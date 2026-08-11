package com.gffh.api.domain;

/**
 * A point on the earth's surface.
 *
 * <p>Longitude is declared first, matching the GeoJSON ordering that MongoDB's
 * 2dsphere index requires. Reversing the pair is the most common cause of
 * nonsensical distance results, so the ordering is fixed here once and never
 * re-derived at a call site.
 */
public record GeoPoint(double longitude, double latitude) {

    private static final double EARTH_RADIUS_MILES = 3958.8;

    public GeoPoint {
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
    }

    /** Great-circle distance in miles. */
    public double milesTo(GeoPoint other) {
        double dLat = Math.toRadians(other.latitude - latitude);
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_MILES * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** GeoJSON representation for MongoDB persistence. */
    public double[] toGeoJsonCoordinates() {
        return new double[]{longitude, latitude};
    }
}
