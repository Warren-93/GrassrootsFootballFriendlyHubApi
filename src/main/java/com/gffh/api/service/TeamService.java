package com.gffh.api.service;

import com.gffh.api.domain.*;
import com.gffh.api.repository.ClubRepository;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
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
    private final FixtureRepository fixtures;
    private final FriendlyRequestRepository friendlyRequests;

    public TeamService(TeamRepository teams, ClubRepository clubs, MembershipRepository memberships,
                       MembershipService membershipService, FixtureRepository fixtures,
                       FriendlyRequestRepository friendlyRequests) {
        this.teams = teams;
        this.clubs = clubs;
        this.memberships = memberships;
        this.membershipService = membershipService;
        this.fixtures = fixtures;
        this.friendlyRequests = friendlyRequests;
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
                VerificationStatus.NOT_STARTED, request.defaultVenueId(), null, null, null, false, false));

        memberships.save(new Membership(null, userId, team.id(), null, Role.TEAM_MANAGER, Instant.now()));
        return team;
    }

    /**
     * Viewing is not gated on management - matching (SCR-FF-05) and direct
     * links both need to open a team you don't manage. Contact details are
     * redacted for non-managers at the DTO layer instead; see
     * {@link com.gffh.api.web.TeamDtos.TeamView#from(Team, boolean)}.
     */
    public Team get(String userId, String teamId) {
        return teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);
    }

    public boolean canManage(String userId, String teamId) {
        Role role = membershipService.roleFor(userId, teamId);
        return role != null && role.canManageTeam();
    }

    /** Every squad under a club (SCR-PR-03's teams list) - the club's own officials only. */
    public java.util.List<Team> listByClub(String userId, String clubId) {
        membershipService.requireCanManageClub(userId, clubId);
        return teams.findByClubId(clubId);
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
                current.firstFixtureConfirmedAt(), current.createdAt(), current.updatedAt(), current.suspended(),
                current.archived());

        return teams.save(updated);
    }

    /**
     * A manager's own soft-delete for a team that's done - distinct from
     * admin suspension (a moderation action) and hard deletion (data loss).
     * Guarded the same way VenueService.delete guards against removing a
     * venue with an upcoming confirmed fixture: nothing that would strand an
     * open negotiation or a fixture someone is still counting on.
     */
    public void archive(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        Team current = teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);

        if (fixtures.existsUpcomingConfirmedForTeam(teamId)) {
            throw new BusinessRuleException("TEAM_HAS_UPCOMING_FIXTURE", HttpStatus.CONFLICT,
                    "This team has an upcoming confirmed fixture - cancel it before archiving.");
        }
        if (friendlyRequests.existsOpenForTeam(teamId)) {
            throw new BusinessRuleException("TEAM_HAS_OPEN_REQUESTS", HttpStatus.CONFLICT,
                    "This team has an open friendly request - resolve it before archiving.");
        }

        Team archived = new Team(current.id(), current.clubId(), current.name(), current.clubName(),
                current.badgeUrl(), current.ageGroup(), current.gender(), current.format(), current.abilityLevel(),
                current.league(), current.postcode(), current.location(), current.travelRadiusMiles(),
                current.homeAwayPreference(), current.managerName(), current.contactPhone(), current.description(),
                current.verification(), current.defaultVenueId(), current.firstFixtureConfirmedAt(),
                current.createdAt(), current.updatedAt(), current.suspended(), true);
        teams.save(archived);
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
