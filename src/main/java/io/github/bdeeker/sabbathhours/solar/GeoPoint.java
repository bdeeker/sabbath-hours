package io.github.bdeeker.sabbathhours.solar;

/**
 * A point on the Earth's surface in decimal degrees (WGS 84).
 * Latitude is positive north, longitude is positive east.
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, was " + latitude);
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, was " + longitude);
        }
    }
}
