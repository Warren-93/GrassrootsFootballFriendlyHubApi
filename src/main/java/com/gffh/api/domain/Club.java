package com.gffh.api.domain;

import java.time.Instant;

/** The organisation a team belongs to. A club may run more than one team. */
public record Club(
        String id,
        String name,
        String badgeUrl,
        String postcode,
        GeoPoint location,
        String website,
        String contactEmail,
        Instant createdAt) {
}
