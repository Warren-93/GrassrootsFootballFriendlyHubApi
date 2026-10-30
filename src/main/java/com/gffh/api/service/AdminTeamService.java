package com.gffh.api.service;

import com.gffh.api.dto.admin.AdminTeamDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.Membership;
import com.gffh.api.model.Team;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminTeamService {

    private final TeamRepository teams;
    private final MembershipRepository memberships;
    private final FriendlyRequestService friendlyRequests;
    private final AuditLogService auditLogService;

    public AdminTeamService(TeamRepository teams, MembershipRepository memberships,
                            FriendlyRequestService friendlyRequests, AuditLogService auditLogService) {
        this.teams = teams;
        this.memberships = memberships;
        this.friendlyRequests = friendlyRequests;
        this.auditLogService = auditLogService;
    }

    public List<AdminTeamDtos.AdminTeamView> search(String query) {
        return teams.searchByName(query, 500).stream().map(AdminTeamDtos.AdminTeamView::from).toList();
    }

    public AdminTeamDtos.AdminTeamView get(String teamId) {
        return AdminTeamDtos.AdminTeamView.from(find(teamId));
    }

    public AdminTeamDtos.AdminTeamView suspend(String adminId, String teamId, String reason) {
        Team team = find(teamId);
        teams.save(withSuspended(team, true));
        friendlyRequests.cancelOpenForTeam(teamId);
        auditLogService.record(adminId, "TEAM_SUSPENDED", "TEAM", teamId, reason);
        return AdminTeamDtos.AdminTeamView.from(find(teamId));
    }

    public AdminTeamDtos.AdminTeamView unsuspend(String adminId, String teamId, String reason) {
        Team team = find(teamId);
        teams.save(withSuspended(team, false));
        auditLogService.record(adminId, "TEAM_UNSUSPENDED", "TEAM", teamId, reason);
        return AdminTeamDtos.AdminTeamView.from(find(teamId));
    }

    /**
     * Reassigns the duplicate's memberships (who manages it) to the primary
     * team and suspends the duplicate. Availability, requests and fixtures
     * already tied to the duplicate's ID are left as historical record
     * rather than migrated - a fuller data migration is real future work,
     * out of scope for this pass.
     *
     * <p>Someone with a role on both teams (usually the manager who created
     * the duplicate) keeps one membership of the primary, with the stronger
     * of the two roles: a second row for the same user and team would make
     * every later lookup of their role on it fail.
     */
    public void merge(String adminId, AdminTeamDtos.MergeRequest request) {
        if (request.primaryTeamId().equals(request.duplicateTeamId())) {
            throw new BusinessRuleException("CANNOT_MERGE_TEAM_INTO_ITSELF", HttpStatus.BAD_REQUEST,
                    "Choose two different teams to merge.");
        }
        Team primary = find(request.primaryTeamId());
        Team duplicate = find(request.duplicateTeamId());

        for (Membership m : memberships.findByTeamId(duplicate.getId())) {
            Membership existing = memberships.findByUserIdAndTeamId(m.getUserId(), primary.getId()).orElse(null);
            if (existing == null) {
                memberships.save(new Membership(m.getId(), m.getUserId(), primary.getId(), null, m.getRole(), m.getCreatedAt()));
                continue;
            }
            // Role is declared weakest first.
            if (m.getRole().ordinal() > existing.getRole().ordinal()) {
                memberships.save(new Membership(existing.getId(), existing.getUserId(), primary.getId(), null,
                        m.getRole(), existing.getCreatedAt()));
            }
            memberships.deleteById(m.getId());
        }

        teams.save(withSuspended(duplicate, true));
        auditLogService.record(adminId, "TEAMS_MERGED", "TEAM", primary.getId(),
                "Merged duplicate " + duplicate.getId() + " into " + primary.getId());
    }

    private Team find(String teamId) {
        return teams.findById(teamId).orElseThrow(() -> new BusinessRuleException(
                "TEAM_NOT_FOUND", HttpStatus.NOT_FOUND, "That team could not be found."));
    }

    private static Team withSuspended(Team team, boolean suspended) {
        return new Team(team.getId(), team.getClubId(), team.getName(), team.getClubName(), team.getBadgeUrl(),
                team.getAgeGroup(), team.getGender(), team.getFormat(), team.getAbilityLevel(), team.getOrganisation(), team.getLeague(),
                team.getPostcode(), team.getLocation(), team.getTravelRadiusMiles(), team.getHomeAwayPreference(),
                team.getManagerName(), team.getContactPhone(), team.getDescription(), team.getVerification(),
                team.getDefaultVenueId(), team.getFirstFixtureConfirmedAt(), team.getCreatedAt(), team.getUpdatedAt(),
                suspended, team.isArchived(), team.isSearchVisible(), team.isShareContactDetails());
    }
}
