package com.gffh.api.model;

import com.gffh.api.model.enums.PitchSurface;
import com.gffh.api.model.enums.VenueFacility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pitch a club can offer for home fixtures. Club-scoped and shared across
 * every squad the club runs (SCR-PR-05), which is why this hangs off
 * {@code clubId} rather than {@code teamId}.
 *
 * <p>{@code postcode} is optional: venues saved before it existed, and
 * clients that don't send it, fall back to a postcode found in the address
 * (see {@link #displayPostcode()}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Venue {

    private String id;
    private String clubId;
    private String name;
    private String address;
    private String postcode;
    private GeoPoint location;
    private PitchSurface pitchSurface;
    private List<VenueFacility> facilities;
    private String accessNotes;
    private String parkingNotes;
    private String pitchNumber;
    private boolean isDefault;
    private Instant createdAt;

    private static final String DIRECTIONS = "https://www.google.com/maps/dir/?api=1&destination=";

    /** A UK postcode anywhere in a string, e.g. "FK2 7ZX", "sw1a1aa". */
    private static final Pattern UK_POSTCODE = Pattern.compile(
            "\\b([A-Z]{1,2}[0-9][A-Z0-9]?)\\s*([0-9][A-Z]{2})\\b", Pattern.CASE_INSENSITIVE);

    /**
     * The postcode to show: the one entered for the venue, else the last
     * postcode in its address. Null when neither has one.
     */
    public String displayPostcode() {
        if (postcode != null && !postcode.isBlank()) return postcode.trim().toUpperCase(Locale.ROOT);
        return postcodeIn(address);
    }

    /**
     * Google Maps directions to the venue: by coordinates when it has them
     * (every venue created through the API does), else by postcode. Null when
     * there is nothing to route to. Clients build the same link.
     */
    public String directionsUrl() {
        if (location != null) {
            return DIRECTIONS + plain(location.latitude()) + "," + plain(location.longitude());
        }
        String pc = displayPostcode();
        return pc == null ? null : DIRECTIONS + URLEncoder.encode(pc, StandardCharsets.UTF_8);
    }

    /** The last UK postcode in {@code text}, normalised to "OUTWARD INWARD" in capitals; null if none. */
    static String postcodeIn(String text) {
        if (text == null) return null;
        Matcher m = UK_POSTCODE.matcher(text);
        String found = null;
        while (m.find()) {
            found = (m.group(1) + " " + m.group(2)).toUpperCase(Locale.ROOT);
        }
        return found;
    }

    private static String plain(double degrees) {
        return BigDecimal.valueOf(degrees).stripTrailingZeros().toPlainString();
    }
}
