package com.gffh.api.repository.mongo;

import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
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
    public String postcode;
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
        d.id = v.getId();
        d.clubId = v.getClubId();
        d.name = v.getName();
        d.address = v.getAddress();
        d.postcode = v.getPostcode();
        d.location = v.getLocation() == null ? null : new GeoJsonPoint(v.getLocation().longitude(), v.getLocation().latitude());
        d.pitchSurface = v.getPitchSurface() == null ? null : v.getPitchSurface().name();
        d.facilities = v.getFacilities() == null ? List.of() : v.getFacilities().stream().map(Enum::name).toList();
        d.accessNotes = v.getAccessNotes();
        d.parkingNotes = v.getParkingNotes();
        d.pitchNumber = v.getPitchNumber();
        d.isDefault = v.isDefault();
        d.createdAt = v.getCreatedAt();
        return d;
    }

    public Venue toDomain() {
        return new Venue(id, clubId, name, address, postcode,
                location == null ? null : new GeoPoint(location.getX(), location.getY()),
                pitchSurface == null ? null : PitchSurface.valueOf(pitchSurface),
                facilities == null ? List.of() : facilities.stream().map(VenueFacility::valueOf).toList(),
                accessNotes, parkingNotes, pitchNumber, isDefault, createdAt);
    }
}
