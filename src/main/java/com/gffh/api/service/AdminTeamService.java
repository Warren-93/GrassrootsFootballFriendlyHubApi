package com.gffh.api.service;

import com.gffh.api.domain.Membership;
import com.gffh.api.domain.Team;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.web.AdminTeamDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminTeamService {

    private final TeamRepository teams;
    private final MembershipRepository memberships;
    private final FriendlyRequestRepository friendlyRequests;
    private final AuditLogService auditLogService;

    public AdminTeamService(TeamRepository teams, MembershipRepository memberships,
                            FriendlyRequestRepository friendlyRequests, AuditLogService auditLogService) {
        this.teams = teams;
        this.memberships = memberships;
        this.friendlyRequests = friendlyRequests;
        this.auditLogService = auditLogService;
    }

    public List<AdminTeamDtos.AdminTeamView> search(String query) {
        return teams.searchByName(query, 50).stream().map(AdminTeamDtos.AdminTeamView::from).toList();
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
     */
    public void merge(String adminId, AdminTeamDtos.MergeRequest request) {
        Team primary = find(request.primaryTeamId());
        Team duplicate = find(request.duplicateTeamId());

        for (Membership m : memberships.findByTeamId(duplicate.id())) {
            memberships.save(new Membership(m.id(), m.userId(), primary.id(), null, m.role(), m.createdAt()));
        }

        teams.save(withSuspended(duplicate, true));
        auditLogService.record(adminId, "TEAMS_MERGED", "TEAM", primary.id(),
                "Merged duplicate " + duplicate.id() + " into " + primary.id());
    }

    private Team find(String teamId) {
        return teams.findById(teamId).orElseThrow(() -> new BusinessRuleException(
                "TEAM_NOT_FOUND", HttpStatus.NOT_FOUND, "That team could not be found."));
    }

    private static Team withSuspended(Team team, boolean suspended) {
        return new Team(team.id(), team.clubId(), team.name(), team.clubName(), team.badgeUrl(),
                team.ageGroup(), team.gender(), team.format(), team.abilityLevel(), team.league(),
                team.postcode(), team.location(), team.travelRadiusMiles(), team.homeAwayPreference(),
                team.managerName(), team.contactPhone(), team.description(), team.verification(),
                team.defaultVenueId(), team.firstFixtureConfirmedAt(), team.createdAt(), team.updatedAt(),
                suspended);
    }
}
