package com.gffh.api.model;

import com.gffh.api.model.enums.Organisation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredOrganisation {

    private String id;
    private String name;
    private String shortName;
    private Organisation linkedOrganisation;
    private String contactName;
    private String contactEmail;
    private String website;
    private String notes;
    private List<League> leagues;
    private Instant createdAt;
    private String createdByEmail;
    private Instant updatedAt;

    /** A league the association runs, with its divisions in display order. */
    public record League(String id, String name, List<String> divisions) {
        public League {
            divisions = divisions == null ? List.of() : divisions;
        }
    }
}
