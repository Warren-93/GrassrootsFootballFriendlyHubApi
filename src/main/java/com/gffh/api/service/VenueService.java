package com.gffh.api.service;

import com.gffh.api.domain.GeoPoint;
import com.gffh.api.domain.Venue;
import com.gffh.api.repository.VenueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class VenueService {

    private final VenueRepository venues;
    private final MembershipService membershipService;

    public VenueService(VenueRepository venues, MembershipService membershipService) {
        this.venues = venues;
        this.membershipService = membershipService;
    }

    public Venue create(String userId, com.gffh.api.web.VenueDtos.CreateVenueRequest request) {
        membershipService.requireCanManageClub(userId, request.clubId());

        // The club's first venue becomes the default automatically - SCR-ON-04
        // is "Add first venue" precisely because nothing else can be default yet.
        boolean isFirst = venues.findByClubId(request.clubId()).isEmpty();

        Venue venue = venues.save(new Venue(null, request.clubId(), request.name(), request.address(),
                new GeoPoint(request.longitude(), request.latitude()), request.pitchSurface(),
                request.facilities() == null ? List.of() : request.facilities(),
                request.accessNotes(), request.parkingNotes(), request.pitchNumber(),
                isFirst, Instant.now()));
        return venue;
    }

    public List<Venue> list(String userId, String clubId) {
        membershipService.requireCanManageClub(userId, clubId);
        return venues.findByClubId(clubId);
    }

    public Venue update(String userId, String venueId, com.gffh.api.web.VenueDtos.UpdateVenueRequest request) {
        Venue current = find(venueId);
        membershipService.requireCanManageClub(userId, current.clubId());

        GeoPoint location = (request.longitude() != null && request.latitude() != null)
                ? new GeoPoint(request.longitude(), request.latitude())
                : current.location();

        Venue updated = new Venue(current.id(), current.clubId(),
                orDefault(request.name(), current.name()),
                orDefault(request.address(), current.address()),
                location,
                request.pitchSurface() != null ? request.pitchSurface() : current.pitchSurface(),
                request.facilities() != null ? request.facilities() : current.facilities(),
                orDefault(request.accessNotes(), current.accessNotes()),
                orDefault(request.parkingNotes(), current.parkingNotes()),
                orDefault(request.pitchNumber(), current.pitchNumber()),
                current.isDefault(), current.createdAt());

        return venues.save(updated);
    }

    public Venue setDefault(String userId, String venueId) {
        Venue current = find(venueId);
        membershipService.requireCanManageClub(userId, current.clubId());
        venues.clearDefaultForClub(current.clubId());
        return venues.save(new Venue(current.id(), current.clubId(), current.name(), current.address(),
                current.location(), current.pitchSurface(), current.facilities(), current.accessNotes(),
                current.parkingNotes(), current.pitchNumber(), true, current.createdAt()));
    }

    public void delete(String userId, String venueId) {
        Venue current = find(venueId);
        membershipService.requireCanManageClub(userId, current.clubId());
        venues.delete(venueId);
    }

    private Venue find(String venueId) {
        return venues.findById(venueId).orElseThrow(() -> new BusinessRuleException(
                "VENUE_NOT_FOUND", HttpStatus.NOT_FOUND, "That venue could not be found."));
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
