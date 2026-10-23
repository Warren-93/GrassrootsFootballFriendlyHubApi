package com.gffh.api.repository.mongo;

import com.gffh.api.model.Club;
import com.gffh.api.model.GeoPoint;
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
        d.id = c.getId();
        d.name = c.getName();
        d.badgeUrl = c.getBadgeUrl();
        d.postcode = c.getPostcode();
        d.location = c.getLocation() == null ? null : new GeoJsonPoint(c.getLocation().longitude(), c.getLocation().latitude());
        d.website = c.getWebsite();
        d.contactEmail = c.getContactEmail();
        d.createdAt = c.getCreatedAt();
        return d;
    }

    public Club toDomain() {
        return new Club(id, name, badgeUrl, postcode,
                location == null ? null : new GeoPoint(location.getX(), location.getY()),
                website, contactEmail, createdAt);
    }
}
