package com.gffh.api.service;

import com.gffh.api.domain.Club;
import com.gffh.api.domain.GeoPoint;
import com.gffh.api.domain.Membership;
import com.gffh.api.domain.Role;
import com.gffh.api.repository.ClubRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.web.ClubDtos;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Standalone club creation - SCR-ON-02, for the "club official setting up
 * several squads" onboarding path. The other path (SCR-ON-01 "Create a
 * team") creates a club implicitly; see {@link TeamService#create}.
 */
@Service
public class ClubService {

    private final ClubRepository clubs;
    private final MembershipRepository memberships;
    private final MembershipService membershipService;

    public ClubService(ClubRepository clubs, MembershipRepository memberships,
                       MembershipService membershipService) {
        this.clubs = clubs;
        this.memberships = memberships;
        this.membershipService = membershipService;
    }

    public Club create(String userId, ClubDtos.CreateClubRequest request) {
        Club club = clubs.save(new Club(null, request.name(), request.badgeUrl(), request.postcode(),
                new GeoPoint(request.longitude(), request.latitude()),
                request.website(), request.contactEmail(), Instant.now()));
        memberships.save(new Membership(null, userId, null, club.id(), Role.CLUB_ADMIN, Instant.now()));
        return club;
    }

    public Club get(String userId, String clubId) {
        membershipService.requireCanManageClub(userId, clubId);
        return clubs.findById(clubId).orElseThrow(() -> new BusinessRuleException(
                "CLUB_NOT_FOUND", org.springframework.http.HttpStatus.NOT_FOUND, "That club could not be found."));
    }

    public Club update(String userId, String clubId, ClubDtos.UpdateClubRequest request) {
        membershipService.requireCanManageClub(userId, clubId);
        Club current = clubs.findById(clubId).orElseThrow(() -> new BusinessRuleException(
                "CLUB_NOT_FOUND", org.springframework.http.HttpStatus.NOT_FOUND, "That club could not be found."));

        GeoPoint location = (request.longitude() != null && request.latitude() != null)
                ? new GeoPoint(request.longitude(), request.latitude())
                : current.location();

        Club updated = new Club(current.id(),
                orDefault(request.name(), current.name()),
                orDefault(request.badgeUrl(), current.badgeUrl()),
                orDefault(request.postcode(), current.postcode()),
                location,
                orDefault(request.website(), current.website()),
                orDefault(request.contactEmail(), current.contactEmail()),
                current.createdAt());

        return clubs.save(updated);
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
