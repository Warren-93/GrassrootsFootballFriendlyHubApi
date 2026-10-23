package com.gffh.api.service;

import com.gffh.api.model.Membership;
import com.gffh.api.model.Team;
import com.gffh.api.model.enums.Role;
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
        var direct = memberships.findByUserIdAndTeamId(userId, teamId);
        if (direct.isPresent()) return direct.get().getRole();

        Team team = teams.findById(teamId).orElse(null);
        if (team == null) return null;

        return memberships.findByUserIdAndClubId(userId, team.getClubId())
                .map(Membership::getRole)
                .filter(Role::canManageClub)
                .orElse(null);
    }

    @Override
    public Role roleForClub(String userId, String clubId) {
        return memberships.findByUserIdAndClubId(userId, clubId).map(Membership::getRole).orElse(null);
    }

    @Override
    public boolean canReadTeam(String userId, String teamId) {
        if (memberships.findByUserIdAndTeamId(userId, teamId).isPresent()) return true;

        Team team = teams.findById(teamId).orElse(null);
        return team != null && memberships.findByUserIdAndClubId(userId, team.getClubId()).isPresent();
    }

    @Override
    public boolean canManageAnyTeamInClub(String userId, String clubId) {
        Role clubRole = roleForClub(userId, clubId);
        if (clubRole != null && clubRole.canManageClub()) return true;

        return memberships.findByUserId(userId).stream()
                .filter(m -> m.getTeamId() != null && m.getRole().canManageTeam())
                .map(m -> teams.findById(m.getTeamId()).orElse(null))
                .anyMatch(t -> t != null && clubId.equals(t.getClubId()));
    }

    @Override
    public List<String> managerUserIdsFor(String teamId) {
        Team team = teams.findById(teamId).orElse(null);
        if (team == null) return List.of();

        Stream<Membership> teamScoped = memberships.findByTeamId(teamId).stream();
        Stream<Membership> clubScoped = memberships.findByClubId(team.getClubId()).stream();
        return Stream.concat(teamScoped, clubScoped)
                .filter(m -> m.getRole().canManageTeam())
                .map(Membership::getUserId)
                .distinct()
                .toList();
    }
}
