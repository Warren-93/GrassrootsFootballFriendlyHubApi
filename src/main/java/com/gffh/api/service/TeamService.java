package com.gffh.api.service;

import com.gffh.api.domain.*;
import com.gffh.api.repository.ClubRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.web.TeamDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TeamService {

    private final TeamRepository teams;
    private final ClubRepository clubs;
    private final MembershipRepository memberships;
    private final MembershipService membershipService;

    public TeamService(TeamRepository teams, ClubRepository clubs, MembershipRepository memberships,
                       MembershipService membershipService) {
        this.teams = teams;
        this.clubs = clubs;
        this.memberships = memberships;
        this.membershipService = membershipService;
    }

    public Team create(String userId, TeamDtos.CreateTeamRequest request) {
        String clubId = request.clubId();
        String clubName = request.clubName();

        if (clubId != null) {
            Club club = clubs.findById(clubId)
                    .orElseThrow(() -> new BusinessRuleException("CLUB_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "That club could not be found."));
            membershipService.requireCanManageClub(userId, club.id());
            clubName = club.name();
        } else {
            Club club = clubs.save(new Club(null, clubName != null ? clubName : request.name(),
                    null, null, null, null, null, Instant.now()));
            clubId = club.id();
            clubName = club.name();
            memberships.save(new Membership(null, userId, null, clubId, Role.CLUB_ADMIN, Instant.now()));
        }

        Format format = request.format() != null ? request.format() : Format.suggestedFor(request.ageGroup());

        Team team = teams.save(new Team(
                null, clubId, request.name(), clubName, request.badgeUrl(),
                request.ageGroup(), request.gender(), format, request.abilityLevel(),
                request.league(), request.postcode(),
                new GeoPoint(request.longitude(), request.latitude()),
                request.travelRadiusMiles(), request.homeAwayPreference(),
                request.managerName(), request.contactPhone(), request.description(),
                VerificationStatus.NOT_STARTED, request.defaultVenueId(), null, null, null, false));

        memberships.save(new Membership(null, userId, team.id(), null, Role.TEAM_MANAGER, Instant.now()));
        return team;
    }

    public Team get(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        return teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);
    }

    public Team update(String userId, String teamId, TeamDtos.UpdateTeamRequest request) {
        membershipService.requireCanManageTeam(userId, teamId);
        Team current = teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);

        boolean changesMatchingFields =
                (request.ageGroup() != null && request.ageGroup() != current.ageGroup())
                        || (request.format() != null && request.format() != current.format());
        if (changesMatchingFields && current.matchingFieldsLocked()) {
            throw BusinessRuleException.matchingFieldsLocked();
        }

        GeoPoint location = (request.longitude() != null && request.latitude() != null)
                ? new GeoPoint(request.longitude(), request.latitude())
                : current.location();

        Team updated = new Team(
                current.id(), current.clubId(),
                orDefault(request.name(), current.name()),
                current.clubName(),
                orDefault(request.badgeUrl(), current.badgeUrl()),
                request.ageGroup() != null ? request.ageGroup() : current.ageGroup(),
                request.gender() != null ? request.gender() : current.gender(),
                request.format() != null ? request.format() : current.format(),
                request.abilityLevel() != null ? request.abilityLevel() : current.abilityLevel(),
                orDefault(request.league(), current.league()),
                orDefault(request.postcode(), current.postcode()),
                location,
                request.travelRadiusMiles() != null ? request.travelRadiusMiles() : current.travelRadiusMiles(),
                request.homeAwayPreference() != null ? request.homeAwayPreference() : current.homeAwayPreference(),
                orDefault(request.managerName(), current.managerName()),
                orDefault(request.contactPhone(), current.contactPhone()),
                orDefault(request.description(), current.description()),
                current.verification(),
                orDefault(request.defaultVenueId(), current.defaultVenueId()),
                current.firstFixtureConfirmedAt(), current.createdAt(), current.updatedAt(), current.suspended());

        return teams.save(updated);
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
