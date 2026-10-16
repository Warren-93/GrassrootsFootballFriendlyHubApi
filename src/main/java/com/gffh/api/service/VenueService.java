package com.gffh.api.service;

import com.gffh.api.domain.GeoPoint;
import com.gffh.api.domain.Team;
import com.gffh.api.domain.Venue;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.VenueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class VenueService {

    private final VenueRepository venues;
    private final MembershipService membershipService;
    private final FixtureRepository fixtures;

    public VenueService(VenueRepository venues, MembershipService membershipService, FixtureRepository fixtures) {
        this.venues = venues;
        this.membershipService = membershipService;
        this.fixtures = fixtures;
    }

    public Venue create(String userId, com.gffh.api.web.VenueDtos.CreateVenueRequest request) {
        membershipService.requireCanManageClub(userId, request.clubId());

        // The club's first venue becomes the default automatically - SCR-ON-04
        // is "Add first venue" precisely because nothing else can be default yet.
        boolean isFirst = venues.findByClubId(request.clubId()).isEmpty();

        Venue venue = venues.save(new Venue(null, request.clubId(), request.name(), request.address(),
                blankToNull(request.postcode()),
                new GeoPoint(request.longitude(), request.latitude()), request.pitchSurface(),
                request.facilities() == null ? List.of() : request.facilities(),
                request.accessNotes(), request.parkingNotes(), request.pitchNumber(),
                isFirst, Instant.now()));
        return venue;
    }

    /**
     * Any of the club's managers can see its venues - a team manager picks one
     * when arranging a game - but only its club admins add, change or remove them.
     */
    public List<Venue> list(String userId, String clubId) {
        membershipService.requireCanManageAnyTeamInClub(userId, clubId);
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
                request.postcode() != null ? blankToNull(request.postcode()) : current.postcode(),
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
                current.postcode(), current.location(), current.pitchSurface(), current.facilities(),
                current.accessNotes(), current.parkingNotes(), current.pitchNumber(), true, current.createdAt()));
    }

    public void delete(String userId, String venueId) {
        Venue current = find(venueId);
        membershipService.requireCanManageClub(userId, current.clubId());
        if (fixtures.existsUpcomingConfirmedAtVenue(venueId)) {
            throw new BusinessRuleException("VENUE_HAS_UPCOMING_FIXTURE", HttpStatus.CONFLICT,
                    "This venue has a confirmed fixture booked - it can't be removed until that's played or cancelled.");
        }
        venues.delete(venueId);
    }

    /**
     * Where a team plays at home when nothing more specific was agreed: its own
     * default venue, else its club's default (venues are club-scoped, and a
     * club's first venue becomes its default automatically). Empty if neither
     * exists. No permission check - only used to fill in or describe a request
     * or fixture the caller is already part of.
     */
    public Optional<Venue> homeVenueFor(Team team) {
        if (team.defaultVenueId() != null) {
            Optional<Venue> own = venues.findById(team.defaultVenueId());
            if (own.isPresent()) return own;
        }
        if (team.clubId() == null) return Optional.empty();
        return venues.findByClubId(team.clubId()).stream().filter(Venue::isDefault).findFirst();
    }

    /** A venue by id, with no permission check - for the same read-only uses as {@link #homeVenueFor}. */
    public Optional<Venue> findById(String venueId) {
        return venueId == null || venueId.isBlank() ? Optional.empty() : venues.findById(venueId);
    }

    private Venue find(String venueId) {
        return venues.findById(venueId).orElseThrow(() -> new BusinessRuleException(
                "VENUE_NOT_FOUND", HttpStatus.NOT_FOUND, "That venue could not be found."));
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
