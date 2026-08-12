package com.gffh.api.service;

import com.gffh.api.domain.Fixture;
import com.gffh.api.domain.Team;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FixtureService {

    private final FixtureRepository fixtures;
    private final TeamRepository teams;
    private final MembershipService memberships;

    public FixtureService(FixtureRepository fixtures, TeamRepository teams, MembershipService memberships) {
        this.fixtures = fixtures;
        this.teams = teams;
        this.memberships = memberships;
    }

    public List<Fixture> list(String userId, String teamId) {
        memberships.requireCanManageTeam(userId, teamId);
        return fixtures.findByTeamId(teamId);
    }

    public Fixture get(String userId, String fixtureId) {
        Fixture fixture = fixtures.findById(fixtureId)
                .orElseThrow(() -> new BusinessRuleException("FIXTURE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "That fixture could not be found."));
        boolean visible = canManage(userId, fixture.homeTeamId()) || canManage(userId, fixture.awayTeamId());
        if (!visible) {
            throw new BusinessRuleException("FIXTURE_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "That fixture could not be found.");
        }
        return fixture;
    }

    public Team team(String teamId) {
        return teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);
    }

    /** ADM-07's read-only fixture inspector: no team-management check, already gated by PLATFORM_ADMIN at the controller. */
    public Fixture getForAdmin(String fixtureId) {
        return fixtures.findById(fixtureId).orElseThrow(() -> new BusinessRuleException(
                "FIXTURE_NOT_FOUND", HttpStatus.NOT_FOUND, "That fixture could not be found."));
    }

    private boolean canManage(String userId, String teamId) {
        var role = memberships.roleFor(userId, teamId);
        return role != null && role.canManageTeam();
    }
}
