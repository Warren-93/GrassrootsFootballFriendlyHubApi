package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** The organisation a team belongs to. A club may run more than one team. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Club {

    private String id;
    private String name;
    private String badgeUrl;
    private String postcode;
    private GeoPoint location;
    private String website;
    private String contactEmail;
    private Instant createdAt;
}
