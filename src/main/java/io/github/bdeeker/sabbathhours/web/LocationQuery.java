package io.github.bdeeker.sabbathhours.web;

import java.time.ZoneId;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.bdeeker.sabbathhours.solar.GeoPoint;

/** The {@code latitude}, {@code longitude}, and {@code timeZone} query parameters shared by every Sabbath endpoint. */
public record LocationQuery(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotBlank @IanaTimeZone String timeZone) {

    public GeoPoint point() {
        return new GeoPoint(latitude, longitude);
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
