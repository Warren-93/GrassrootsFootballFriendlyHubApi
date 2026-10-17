package com.gffh.api.model;

import com.gffh.api.model.enums.PitchSurface;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VenueTest {

    @Test
    void anEnteredPostcodeWinsAndIsTidied() {
        assertEquals("FK2 7ZX", venue("1 Pitch Lane, G1 1AA", " fk2 7zx ", null).displayPostcode());
    }

    @Test
    void withoutOneThePostcodeComesFromTheEndOfTheAddress() {
        assertEquals("SW1A 1AA", venue("10 Downing Street, London sw1a1aa", null, null).displayPostcode());
        assertEquals("M1 1AE", venue("Unit 5, 12 High St, Manchester M1 1AE", "", null).displayPostcode());
    }

    @Test
    void anAddressWithNoPostcodeHasNone() {
        assertNull(venue("Rovers Park, Pitch Lane", null, null).displayPostcode());
        assertNull(venue(null, null, null).displayPostcode());
    }

    @Test
    void directionsUseCoordinatesWhenKnown() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=56.014582,-3.790261",
                venue("1 Pitch Lane", "FK2 7ZX", new GeoPoint(-3.790261, 56.014582)).directionsUrl());
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=51.5,0",
                venue("Somewhere", null, new GeoPoint(0.0, 51.5)).directionsUrl());
    }

    @Test
    void directionsFallBackToTheEncodedPostcodeThenToNothing() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=FK2+7ZX",
                venue("1 Pitch Lane", "FK2 7ZX", null).directionsUrl());
        assertNull(venue("1 Pitch Lane", null, null).directionsUrl());
    }

    private static Venue venue(String address, String postcode, GeoPoint location) {
        return new Venue("v1", "c1", "Rovers Park", address, postcode, location, PitchSurface.GRASS,
                List.of(), null, null, null, true, Instant.now());
    }
}
