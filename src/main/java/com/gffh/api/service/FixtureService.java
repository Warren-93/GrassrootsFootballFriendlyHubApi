package com.gffh.api.service;

import com.gffh.api.dto.FriendlyRequestDtos;
import com.gffh.api.dto.admin.AdminFixtureInspectorDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.Fixture;
import com.gffh.api.model.Team;
import com.gffh.api.model.Venue;
import com.gffh.api.model.enums.FixtureStatus;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FixtureService {

    private final FixtureRepository fixtures;
    private final TeamRepository teams;
    private final MembershipService memberships;
    private final FriendlyRequestService friendlyRequestService;
    private final VenueService venues;

    public FixtureService(FixtureRepository fixtures, TeamRepository teams, MembershipService memberships,
                          FriendlyRequestService friendlyRequestService, VenueService venues) {
        this.fixtures = fixtures;
        this.teams = teams;
        this.memberships = memberships;
        this.friendlyRequestService = friendlyRequestService;
        this.venues = venues;
    }

    /**
     * Anyone on the team can see its fixtures - a member who joined with a
     * code included - but only the two teams' managers see contact details
     * (see {@link #managesEitherTeam}) or can cancel one.
     */
    public List<Fixture> list(String userId, String teamId) {
        memberships.requireCanReadTeam(userId, teamId);
        return fixtures.findByTeamId(teamId);
    }

    public Fixture get(String userId, String fixtureId) {
        Fixture fixture = find(fixtureId);
        boolean visible = memberships.canReadTeam(userId, fixture.getHomeTeamId())
                || memberships.canReadTeam(userId, fixture.getAwayTeamId());
        if (!visible) throw fixtureNotFound();
        return fixture;
    }

    /**
     * SCR-FX-06. A fixture has no state of its own to drive - cancelling one is
     * really cancelling the {@link com.gffh.api.model.FriendlyRequest} that
     * confirmed it, so this delegates to the same state machine
     * {@link FriendlyRequestService#act} already uses for every other
     * transition, rather than duplicating the CONFIRMED-to-CANCELLED rules here.
     */
    public Fixture cancel(String userId, String fixtureId, String reason) {
        Fixture fixture = find(fixtureId);
        // A member who can open the fixture still gets the answer a stranger does.
        if (!managesEitherTeam(userId, fixture)) throw fixtureNotFound();
        friendlyRequestService.act(userId, fixture.getFriendlyRequestId(), "cancel",
                new FriendlyRequestDtos.ActionRequest(reason, null, null, null));
        return fixtures.findById(fixtureId).orElseThrow(BusinessRuleException::blocked);
    }

    public Team team(String teamId) {
        return teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);
    }

    /**
     * Where the fixture is being played: its own venue, else the home team's
     * default venue as it stands now (fixtures confirmed before venues were
     * filled in on creation have none of their own). Null when neither exists.
     */
    public Venue venueFor(Fixture fixture) {
        if (fixture.getVenueId() != null) return venues.findById(fixture.getVenueId()).orElse(null);
        return teams.findById(fixture.getHomeTeamId()).flatMap(venues::homeVenueFor).orElse(null);
    }

    /** ADM-07's read-only fixture inspector: no team-management check, already gated by PLATFORM_ADMIN at the controller. */
    public Fixture getForAdmin(String fixtureId) {
        return find(fixtureId);
    }

    /**
     * The admin fixture list. {@code teamQuery} matches team names (part of a
     * name, any case); a query that matches no team returns no fixtures.
     */
    public List<AdminFixtureInspectorDtos.FixtureListItem> searchForAdmin(String teamQuery, LocalDate from, LocalDate to,
                                                                          FixtureStatus status, int limit) {
        Set<String> teamIds = teamQuery == null || teamQuery.isBlank() ? null
                : teams.searchByName(teamQuery.trim(), 500).stream().map(Team::getId).collect(Collectors.toSet());
        Map<String, String> names = new HashMap<>();
        Map<String, String> venueNames = new HashMap<>();
        return fixtures.search(teamIds, from, to, status, Math.min(Math.max(limit, 1), 500)).stream()
                .map(f -> new AdminFixtureInspectorDtos.FixtureListItem(f.getId(), f.getDate(), f.getStartTime(), f.getEndTime(),
                        f.getStatus().name(), f.getHomeTeamId(), teamName(names, f.getHomeTeamId()), f.getAwayTeamId(),
                        teamName(names, f.getAwayTeamId()),
                        f.getVenueId() == null ? null : venueNames.computeIfAbsent(f.getVenueId(),
                                id -> venues.findById(id).map(Venue::getName).orElse(null)),
                        f.getCreatedAt()))
                .toList();
    }

    private String teamName(Map<String, String> cache, String teamId) {
        return cache.computeIfAbsent(teamId, id -> teams.findById(id).map(Team::getName).orElse("(deleted team)"));
    }

    /** Whether the caller manages either side - the reach that discloses contact details and allows cancelling. */
    public boolean managesEitherTeam(String userId, Fixture fixture) {
        return canManage(userId, fixture.getHomeTeamId()) || canManage(userId, fixture.getAwayTeamId());
    }

    private Fixture find(String fixtureId) {
        return fixtures.findById(fixtureId).orElseThrow(FixtureService::fixtureNotFound);
    }

    private static BusinessRuleException fixtureNotFound() {
        return new BusinessRuleException("FIXTURE_NOT_FOUND", HttpStatus.NOT_FOUND, "That fixture could not be found.");
    }

    private boolean canManage(String userId, String teamId) {
        var role = memberships.roleFor(userId, teamId);
        return role != null && role.canManageTeam();
    }
}
