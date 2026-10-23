package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** The organisation a team belongs to. A club may run more than one team. */
@Document("clubs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Club {

    @Id
    private String id;
    private String name;
    private String badgeUrl;
    private String postcode;
    private GeoPoint location;
    private String website;
    private String contactEmail;
    @CreatedDate
    private Instant createdAt;
}
