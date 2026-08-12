package com.gffh.api.service;

import com.gffh.api.domain.Membership;
import com.gffh.api.domain.Role;
import com.gffh.api.domain.Team;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

/**
 * A user manages a team either through a membership scoped directly to that
 * team, or through a club-admin membership scoped to the club the team belongs
 * to - the cascade the interface javadoc describes.
 */
@Service
public class MembershipServiceImpl implements MembershipService {

    private final MembershipRepository memberships;
    private final TeamRepository teams;

    public MembershipServiceImpl(MembershipRepository memberships, TeamRepository teams) {
        this.memberships = memberships;
        this.teams = teams;
    }

    @Override
    public Role roleFor(String userId, String teamId) {
        var direct = memberships.findTeamMembership(userId, teamId);
        if (direct.isPresent()) return direct.get().role();

        Team team = teams.findById(teamId).orElse(null);
        if (team == null) return null;

        return memberships.findClubMembership(userId, team.clubId())
                .map(Membership::role)
                .filter(Role::canManageClub)
                .orElse(null);
    }

    @Override
    public Role roleForClub(String userId, String clubId) {
        return memberships.findClubMembership(userId, clubId).map(Membership::role).orElse(null);
    }

    @Override
    public List<String> managerUserIdsFor(String teamId) {
        Team team = teams.findById(teamId).orElse(null);
        if (team == null) return List.of();

        Stream<Membership> teamScoped = memberships.findByTeamId(teamId).stream();
        Stream<Membership> clubScoped = memberships.findByClubId(team.clubId()).stream();
        return Stream.concat(teamScoped, clubScoped)
                .filter(m -> m.role().canManageTeam())
                .map(Membership::userId)
                .distinct()
                .toList();
    }
}
