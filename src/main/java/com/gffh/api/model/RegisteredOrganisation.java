package com.gffh.api.model;

import com.gffh.api.model.enums.Organisation;

import java.time.Instant;
import java.util.List;

/**
 * An association that has signed up with Kickabout and been added by an
 * admin as verified - with the leagues it runs and the divisions in each.
 * Associations don't sign in; admins keep this list. It is the intended
 * future source of the Organisation / League / Division options on create
 * club, but nothing reads it there yet: create team still uses the fixed
 * {@link Organisation} list. {@code linkedOrganisation} says which of those
 * fixed entries it corresponds to, if any, for when the two are joined up.
 */
public record RegisteredOrganisation(
        String id,
        String name,
        String shortName,
        Organisation linkedOrganisation,
        String contactName,
        String contactEmail,
        String website,
        String notes,
        List<League> leagues,
        Instant createdAt,
        String createdByEmail,
        Instant updatedAt) {

    /** A league the association runs, with its divisions in display order. */
    public record League(String id, String name, List<String> divisions) {}
}
