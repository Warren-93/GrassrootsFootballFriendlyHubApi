package com.gffh.api.service;

import com.gffh.api.dto.TeamDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
import com.gffh.api.repository.ClubRepository;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
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
            membershipService.requireCanManageClub(userId, club.getId());
            clubName = club.getName();
        } else {
            Club club = clubs.save(new Club(null, clubName != null ? clubName : request.name(),
                    null, null, null, null, null, Instant.now()));
            clubId = club.getId();
            clubName = club.getName();
            memberships.save(new Membership(null, userId, null, clubId, Role.CLUB_ADMIN, Instant.now()));
        }

        Format format = request.format() != null ? request.format() : Format.suggestedFor(request.ageGroup());
        requireOrganisationFits(request.organisation(), request.ageGroup(), request.gender());

        Team team = teams.save(new Team(
                null, clubId, request.name(), clubName, request.badgeUrl(),
                request.ageGroup(), request.gender(), format, request.abilityLevel(), request.organisation(),
                request.league(), request.postcode(),
                new GeoPoint(request.longitude(), request.latitude()),
                request.travelRadiusMiles(), request.homeAwayPreference(),
                request.managerName(), request.contactPhone(), request.description(),
                VerificationStatus.NOT_STARTED, request.defaultVenueId(), null, null, null, false, false, true, true));

        memberships.save(new Membership(null, userId, team.getId(), null, Role.TEAM_MANAGER, Instant.now()));
        return team;
    }

    /**
     * Viewing is not gated on management - matching (SCR-FF-05) and direct
     * links both need to open a team you don't manage. Contact details are
     * redacted for non-managers at the DTO layer instead; see
     * {@link com.gffh.api.dto.TeamDtos.TeamView#from(Team, boolean)}.
     */
    public Team get(String userId, String teamId) {
        return teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);
    }

    public boolean canManage(String userId, String teamId) {
        Role role = membershipService.roleFor(userId, teamId);
        return role != null && role.canManageTeam();
    }

    /** Whether this user runs the club itself (CLUB_ADMIN) - the reach venue changes need. */
    public boolean canManageClub(String userId, String clubId) {
        Role role = membershipService.roleForClub(userId, clubId);
        return role != null && role.canManageClub();
    }

    /** Every squad under a club (SCR-PR-03's teams list) - the club's own officials only. */
    public java.util.List<Team> listByClub(String userId, String clubId) {
        membershipService.requireCanManageClub(userId, clubId);
        return teams.findByClubId(clubId);
    }

    /**
     * Every team this user manages - directly, or via a club-admin membership
     * that cascades to every team under that club (the same reach
     * {@link MembershipServiceImpl#roleFor} grants). Lets a client rebuild
     * "which team am I acting as" from scratch after sign-in, without relying
     * on a locally cached team ID that a fresh session won't have.
     */
    public java.util.List<Team> listMine(String userId) {
        var teamIds = new java.util.LinkedHashSet<String>();
        for (Membership m : memberships.findByUserId(userId)) {
            if (m.getTeamId() != null) {
                teamIds.add(m.getTeamId());
            } else if (m.getClubId() != null && m.getRole().canManageClub()) {
                teams.findByClubId(m.getClubId()).forEach(t -> teamIds.add(t.getId()));
            }
        }
        return teamIds.stream()
                .map(teams::findById)
                .flatMap(java.util.Optional::stream)
                .filter(t -> !t.isArchived())
                .toList();
    }

    public Team update(String userId, String teamId, TeamDtos.UpdateTeamRequest request) {
        membershipService.requireCanManageTeam(userId, teamId);
        Team current = teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);

        boolean changesMatchingFields =
                (request.ageGroup() != null && request.ageGroup() != current.getAgeGroup())
                        || (request.format() != null && request.format() != current.getFormat());
        if (changesMatchingFields && current.matchingFieldsLocked()) {
            throw BusinessRuleException.matchingFieldsLocked();
        }
        // Checked whenever any side changes, so a new age group or gender can't strand an organisation that no longer fits.
        if (request.organisation() != null || request.ageGroup() != null || request.gender() != null) {
            requireOrganisationFits(
                    request.organisation() != null ? request.organisation() : current.getOrganisation(),
                    request.ageGroup() != null ? request.ageGroup() : current.getAgeGroup(),
                    request.gender() != null ? request.gender() : current.getGender());
        }

        GeoPoint location = (request.longitude() != null && request.latitude() != null)
                ? new GeoPoint(request.longitude(), request.latitude())
                : current.getLocation();

        Team updated = new Team(
                current.getId(), current.getClubId(),
                orDefault(request.name(), current.getName()),
                current.getClubName(),
                orDefault(request.badgeUrl(), current.getBadgeUrl()),
                request.ageGroup() != null ? request.ageGroup() : current.getAgeGroup(),
                request.gender() != null ? request.gender() : current.getGender(),
                request.format() != null ? request.format() : current.getFormat(),
                request.abilityLevel() != null ? request.abilityLevel() : current.getAbilityLevel(),
                request.organisation() != null ? request.organisation() : current.getOrganisation(),
                orDefault(request.league(), current.getLeague()),
                orDefault(request.postcode(), current.getPostcode()),
                location,
                request.travelRadiusMiles() != null ? request.travelRadiusMiles() : current.getTravelRadiusMiles(),
                request.homeAwayPreference() != null ? request.homeAwayPreference() : current.getHomeAwayPreference(),
                orDefault(request.managerName(), current.getManagerName()),
                orDefault(request.contactPhone(), current.getContactPhone()),
                orDefault(request.description(), current.getDescription()),
                current.getVerification(),
                orDefault(request.defaultVenueId(), current.getDefaultVenueId()),
                current.getFirstFixtureConfirmedAt(), current.getCreatedAt(), current.getUpdatedAt(), current.isSuspended(),
                current.isArchived(), current.isSearchVisible(), current.isShareContactDetails());

        return teams.save(updated);
    }

    public TeamDtos.PrivacyPreferences getPrivacyPreferences(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        Team team = teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);
        return TeamDtos.PrivacyPreferences.from(team);
    }

    /**
     * {@code searchVisible} governs whether the team appears as a match
     * candidate at all (see MongoTeamRepository#findCandidates);
     * {@code shareContactDetails} governs whether a confirmed fixture's
     * manager name/phone for this team are populated at all (see
     * FixtureDtos.FixtureTeamView#from) - symmetric for both sides of the
     * fixture, not just the other team, since fixture detail is the same
     * projection either way.
     */
    public TeamDtos.PrivacyPreferences updatePrivacyPreferences(
            String userId, String teamId, TeamDtos.PrivacyPreferences request) {
        membershipService.requireCanManageTeam(userId, teamId);
        Team current = teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);

        Team updated = new Team(current.getId(), current.getClubId(), current.getName(), current.getClubName(),
                current.getBadgeUrl(), current.getAgeGroup(), current.getGender(), current.getFormat(), current.getAbilityLevel(), current.getOrganisation(),
                current.getLeague(), current.getPostcode(), current.getLocation(), current.getTravelRadiusMiles(),
                current.getHomeAwayPreference(), current.getManagerName(), current.getContactPhone(), current.getDescription(),
                current.getVerification(), current.getDefaultVenueId(), current.getFirstFixtureConfirmedAt(),
                current.getCreatedAt(), current.getUpdatedAt(), current.isSuspended(), current.isArchived(),
                request.searchVisible(), request.shareContactDetails());

        return TeamDtos.PrivacyPreferences.from(teams.save(updated));
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

        teams.save(archived(current));
    }

    /**
     * Archives a generated test team when its test account is removed. No
     * manager or open-request checks: the team only ever existed for testing,
     * and archiving (not deleting) keeps any fixtures or requests it touched
     * readable for the real teams on the other side.
     */
    public void archiveTestTeam(String teamId) {
        teams.findById(teamId).filter(t -> !t.isArchived()).ifPresent(t -> teams.save(archived(t)));
    }

    private static Team archived(Team current) {
        return new Team(current.getId(), current.getClubId(), current.getName(), current.getClubName(),
                current.getBadgeUrl(), current.getAgeGroup(), current.getGender(), current.getFormat(), current.getAbilityLevel(), current.getOrganisation(),
                current.getLeague(), current.getPostcode(), current.getLocation(), current.getTravelRadiusMiles(),
                current.getHomeAwayPreference(), current.getManagerName(), current.getContactPhone(), current.getDescription(),
                current.getVerification(), current.getDefaultVenueId(), current.getFirstFixtureConfirmedAt(),
                current.getCreatedAt(), current.getUpdatedAt(), current.isSuspended(), true,
                current.isSearchVisible(), current.isShareContactDetails());
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static void requireOrganisationFits(Organisation organisation, AgeGroup ageGroup, Gender gender) {
        if (organisation != null && ageGroup != null && !organisation.isAvailableFor(ageGroup, gender)) {
            throw BusinessRuleException.organisationNotAvailable(organisation, ageGroup, gender);
        }
    }
}
