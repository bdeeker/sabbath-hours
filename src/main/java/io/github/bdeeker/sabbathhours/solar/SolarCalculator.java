package io.github.bdeeker.sabbathhours.solar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

/**
 * Computes sunset times using the NOAA Solar Calculator algorithm (after Jean Meeus,
 * <i>Astronomical Algorithms</i>). Accuracy is about one minute for latitudes between
 * 72 degrees north and south, and degrades closer to the poles where the sun grazes the horizon.
 *
 * <p>"Sunset" means the moment the sun's upper edge touches a sea-level horizon, using the
 * standard 0.833 degree allowance for atmospheric refraction and the sun's radius. Observer
 * elevation is not modeled.
 *
 * <p>This class is stateless and thread-safe.
 */
@Component
public class SolarCalculator {

    /** Zenith angle of the sun's center at official sunset: 90 degrees + 50 arcminutes. */
    private static final double SUNSET_ZENITH_DEG = 90.833;

    private static final double JULIAN_UNIX_EPOCH = 2440587.5;
    private static final double JULIAN_J2000 = 2451545.0;
    private static final double MILLIS_PER_DAY = 86_400_000.0;
    private static final double MINUTES_PER_DAY = 1440.0;

    /** Refinement passes; the time estimate converges to well under a second after three. */
    private static final int ITERATIONS = 3;

    /**
     * Returns the sunset that ends the daylight of {@code civilDate} in {@code zone}: the first
     * sunset after that date's solar noon.
     *
     * <p>This is deliberately not "the sunset whose clock time falls on {@code civilDate}". In
     * Fairbanks in June, Friday's sunset happens after midnight, at about 00:47 on Saturday's
     * clock, and on Kiritimati (UTC+14 at 157 degrees west) the civil calendar runs a full day
     * ahead of the sun. Anchoring on solar noon gives the right answer in both cases.
     */
    public SunsetOutcome sunsetEndingDay(GeoPoint point, LocalDate civilDate, ZoneId zone) {
        LocalDate solarDate = null;
        for (int offset = -1; offset <= 1; offset++) {
            LocalDate candidate = civilDate.plusDays(offset);
            Instant noon = solarNoon(point, candidate);
            if (noon.atZone(zone).toLocalDate().equals(civilDate)) {
                solarDate = candidate;
                break;
            }
        }
        if (solarDate == null) {
            return new SunsetOutcome.NoSunset(SunsetOutcome.Reason.DATE_NOT_IN_ZONE);
        }
        return sunsetOnSolarDate(point, solarDate);
    }

    /**
     * Solar noon (upper transit) for the solar day that is anchored to {@code solarDate}
     * at 00:00 UTC. Package-private for tests.
     */
    Instant solarNoon(GeoPoint point, LocalDate solarDate) {
        double baseJd = julianDay(solarDate);
        double minutes = 720.0 - 4.0 * point.longitude();
        for (int i = 0; i < ITERATIONS; i++) {
            SolarPosition sun = SolarPosition.at(baseJd + minutes / MINUTES_PER_DAY);
            minutes = 720.0 - 4.0 * point.longitude() - sun.equationOfTimeMinutes();
        }
        return toInstant(baseJd, minutes);
    }

    /** Sunset following the solar noon of {@code solarDate}. Package-private for tests. */
    SunsetOutcome sunsetOnSolarDate(GeoPoint point, LocalDate solarDate) {
        double baseJd = julianDay(solarDate);
        double latRad = Math.toRadians(point.latitude());
        // First estimate: solar noon with no hour angle; each pass moves the estimate to sunset.
        double minutes = 720.0 - 4.0 * point.longitude();
        for (int i = 0; i < ITERATIONS; i++) {
            SolarPosition sun = SolarPosition.at(baseJd + minutes / MINUTES_PER_DAY);
            double cosHourAngle = cosSunsetHourAngle(latRad, sun.declinationRad());
            if (cosHourAngle > 1.0) {
                return new SunsetOutcome.NoSunset(SunsetOutcome.Reason.POLAR_NIGHT);
            }
            if (cosHourAngle < -1.0) {
                return new SunsetOutcome.NoSunset(SunsetOutcome.Reason.POLAR_DAY);
            }
            double hourAngleDeg = Math.toDegrees(Math.acos(cosHourAngle));
            minutes = 720.0 - 4.0 * point.longitude() - sun.equationOfTimeMinutes() + 4.0 * hourAngleDeg;
        }
        return new SunsetOutcome.Sunset(toInstant(baseJd, minutes));
    }

    /**
     * Cosine of the hour angle at which the sun reaches the sunset zenith. A value above 1 means
     * the sun never climbs that high (polar night); below -1 means it never sinks that low (polar day).
     */
    private static double cosSunsetHourAngle(double latRad, double declinationRad) {
        double numerator = Math.cos(Math.toRadians(SUNSET_ZENITH_DEG)) - Math.sin(latRad) * Math.sin(declinationRad);
        double denominator = Math.cos(latRad) * Math.cos(declinationRad);
        if (Math.abs(denominator) < 1e-12) {
            // At a pole the sun's altitude is the same all day; only the sign of the numerator matters.
            return numerator > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        return numerator / denominator;
    }

    private static double julianDay(LocalDate utcDate) {
        long epochMillis = utcDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        return epochMillis / MILLIS_PER_DAY + JULIAN_UNIX_EPOCH;
    }

    private static Instant toInstant(double baseJd, double minutes) {
        double jd = baseJd + minutes / MINUTES_PER_DAY;
        long epochMillis = Math.round((jd - JULIAN_UNIX_EPOCH) * MILLIS_PER_DAY);
        return Instant.ofEpochMilli(epochMillis);
    }

    /** The sun's declination and the equation of time at one moment. */
    private record SolarPosition(double declinationRad, double equationOfTimeMinutes) {

        static SolarPosition at(double julianDay) {
            double t = (julianDay - JULIAN_J2000) / 36525.0; // Julian centuries since J2000.0

            double meanLongitudeDeg = normalizeDegrees(280.46646 + t * (36000.76983 + t * 0.0003032));
            double meanAnomalyDeg = 357.52911 + t * (35999.05029 - 0.0001537 * t);
            double eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);

            double m = Math.toRadians(meanAnomalyDeg);
            double equationOfCenterDeg = Math.sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t))
                    + Math.sin(2 * m) * (0.019993 - 0.000101 * t)
                    + Math.sin(3 * m) * 0.000289;
            double trueLongitudeDeg = meanLongitudeDeg + equationOfCenterDeg;

            double omega = Math.toRadians(125.04 - 1934.136 * t);
            double apparentLongitudeRad = Math.toRadians(trueLongitudeDeg - 0.00569 - 0.00478 * Math.sin(omega));

            double meanObliquityDeg = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0;
            double obliquityRad = Math.toRadians(meanObliquityDeg + 0.00256 * Math.cos(omega));

            double declination = Math.asin(Math.sin(obliquityRad) * Math.sin(apparentLongitudeRad));

            double y = Math.pow(Math.tan(obliquityRad / 2.0), 2);
            double l0 = Math.toRadians(meanLongitudeDeg);
            double equationOfTimeRad = y * Math.sin(2 * l0)
                    - 2 * eccentricity * Math.sin(m)
                    + 4 * eccentricity * y * Math.sin(m) * Math.cos(2 * l0)
                    - 0.5 * y * y * Math.sin(4 * l0)
                    - 1.25 * eccentricity * eccentricity * Math.sin(2 * m);

            return new SolarPosition(declination, 4.0 * Math.toDegrees(equationOfTimeRad));
        }

        private static double normalizeDegrees(double degrees) {
            double result = degrees % 360.0;
            return result < 0 ? result + 360.0 : result;
        }
    }
}
