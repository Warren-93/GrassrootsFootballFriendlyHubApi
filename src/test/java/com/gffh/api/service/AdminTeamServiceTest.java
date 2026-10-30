package com.gffh.api.service;

import com.gffh.api.dto.admin.AdminTeamDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.Membership;
import com.gffh.api.model.Team;
import com.gffh.api.model.enums.Role;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link AdminTeamService#merge}: who manages the primary team afterwards. */
class AdminTeamServiceTest {

    private final TeamRepository teams = mock(TeamRepository.class);
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final AdminTeamService service = new AdminTeamService(teams, memberships,
            mock(FriendlyRequestService.class), mock(AuditLogService.class));

    private final Instant created = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        when(teams.findById("primary")).thenReturn(Optional.of(team("primary")));
        when(teams.findById("duplicate")).thenReturn(Optional.of(team("duplicate")));
        when(memberships.findByUserIdAndTeamId(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("someone who only managed the duplicate now manages the primary")
    void movesMembership() {
        when(memberships.findByTeamId("duplicate")).thenReturn(List.of(membership("m1", "u1", "duplicate", Role.TEAM_MANAGER)));

        service.merge("admin", new AdminTeamDtos.MergeRequest("primary", "duplicate"));

        verify(memberships).save(membership("m1", "u1", "primary", Role.TEAM_MANAGER));
        verify(memberships, never()).deleteById(any());
    }

    @Test
    @DisplayName("someone on both teams keeps a single membership of the primary, with the stronger role")
    void collapsesDuplicateMembership() {
        when(memberships.findByTeamId("duplicate")).thenReturn(List.of(membership("m1", "u1", "duplicate", Role.TEAM_MANAGER)));
        when(memberships.findByUserIdAndTeamId("u1", "primary"))
                .thenReturn(Optional.of(membership("m0", "u1", "primary", Role.USER)));

        service.merge("admin", new AdminTeamDtos.MergeRequest("primary", "duplicate"));

        verify(memberships).save(membership("m0", "u1", "primary", Role.TEAM_MANAGER));
        verify(memberships).deleteById("m1");
        verify(memberships, never()).save(membership("m1", "u1", "primary", Role.TEAM_MANAGER));
    }

    @Test
    @DisplayName("a weaker role on the duplicate never downgrades the primary membership")
    void keepsStrongerExistingRole() {
        when(memberships.findByTeamId("duplicate")).thenReturn(List.of(membership("m1", "u1", "duplicate", Role.USER)));
        when(memberships.findByUserIdAndTeamId("u1", "primary"))
                .thenReturn(Optional.of(membership("m0", "u1", "primary", Role.TEAM_MANAGER)));

        service.merge("admin", new AdminTeamDtos.MergeRequest("primary", "duplicate"));

        verify(memberships).deleteById("m1");
        verify(memberships, never()).save(any(Membership.class));
    }

    @Test
    @DisplayName("a team can't be merged into itself, which would only suspend it")
    void rejectsSelfMerge() {
        assertThatThrownBy(() -> service.merge("admin", new AdminTeamDtos.MergeRequest("primary", "primary")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).code())
                .isEqualTo("CANNOT_MERGE_TEAM_INTO_ITSELF");
        verify(teams, never()).save(any());
    }

    private Membership membership(String id, String userId, String teamId, Role role) {
        return new Membership(id, userId, teamId, null, role, created);
    }

    private static Team team(String id) {
        Team team = new Team();
        team.setId(id);
        team.setClubId("club");
        return team;
    }
}
