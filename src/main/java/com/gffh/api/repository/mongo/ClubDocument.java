package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Club;
import com.gffh.api.domain.GeoPoint;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("clubs")
public class ClubDocument {

    @Id
    public String id;
    public String name;
    public String badgeUrl;
    public String postcode;
    public GeoJsonPoint location;
    public String website;
    public String contactEmail;
    public Instant createdAt;

    public static ClubDocument from(Club c) {
        ClubDocument d = new ClubDocument();
        d.id = c.id();
        d.name = c.name();
        d.badgeUrl = c.badgeUrl();
        d.postcode = c.postcode();
        d.location = c.location() == null ? null : new GeoJsonPoint(c.location().longitude(), c.location().latitude());
        d.website = c.website();
        d.contactEmail = c.contactEmail();
        d.createdAt = c.createdAt();
        return d;
    }

    public Club toDomain() {
        return new Club(id, name, badgeUrl, postcode,
                location == null ? null : new GeoPoint(location.getX(), location.getY()),
                website, contactEmail, createdAt);
    }
}
