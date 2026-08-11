package com.gffh.api.repository.mongo;

import com.gffh.api.domain.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("venues")
public class VenueDocument {

    @Id
    public String id;
    public String clubId;
    public String name;
    public String address;
    public GeoJsonPoint location;
    public String pitchSurface;
    public List<String> facilities;
    public String accessNotes;
    public String parkingNotes;
    public String pitchNumber;
    public boolean isDefault;
    public Instant createdAt;

    public static VenueDocument from(Venue v) {
        VenueDocument d = new VenueDocument();
        d.id = v.id();
        d.clubId = v.clubId();
        d.name = v.name();
        d.address = v.address();
        d.location = v.location() == null ? null : new GeoJsonPoint(v.location().longitude(), v.location().latitude());
        d.pitchSurface = v.pitchSurface() == null ? null : v.pitchSurface().name();
        d.facilities = v.facilities() == null ? List.of() : v.facilities().stream().map(Enum::name).toList();
        d.accessNotes = v.accessNotes();
        d.parkingNotes = v.parkingNotes();
        d.pitchNumber = v.pitchNumber();
        d.isDefault = v.isDefault();
        d.createdAt = v.createdAt();
        return d;
    }

    public Venue toDomain() {
        return new Venue(id, clubId, name, address,
                location == null ? null : new GeoPoint(location.getX(), location.getY()),
                pitchSurface == null ? null : PitchSurface.valueOf(pitchSurface),
                facilities == null ? List.of() : facilities.stream().map(VenueFacility::valueOf).toList(),
                accessNotes, parkingNotes, pitchNumber, isDefault, createdAt);
    }
}
