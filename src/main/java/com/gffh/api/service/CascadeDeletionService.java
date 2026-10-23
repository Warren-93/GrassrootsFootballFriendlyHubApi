package com.gffh.api.service;

import com.gffh.api.dto.admin.AdminDeletionDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.Club;
import com.gffh.api.model.FriendlyRequest;
import com.gffh.api.model.Membership;
import com.gffh.api.model.Team;
import com.gffh.api.model.User;
import com.gffh.api.model.enums.DeletionTargetType;
import com.gffh.api.model.enums.RequestStatus;
import com.gffh.api.model.enums.Role;
import com.gffh.api.repository.CascadeDeletionRepository;
import com.gffh.api.repository.ClubRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Works out, and then carries out, an admin's permanent delete of a user
 * account or a team and everything tied to it. The same plan backs the
 * preview and the delete, so what an admin approves is what runs.
 *
 * <ul>
 *   <li>A <b>team</b> goes with its availability, requests, fixtures, chats,
 *   blocks, memberships, join code, invites, verification requests, reports
 *   and notifications. Its club goes too when that leaves the club with no
 *   teams and no club members.</li>
 *   <li>A <b>user</b> goes with their memberships, sessions, push devices,
 *   email links, notifications and settings. Any club where nobody else is a
 *   member (at club level or on any of its teams) is deleted with all its
 *   teams. Clubs and teams other people still belong to stay, and so do the
 *   requests and chat messages the user made for them - those are the team's
 *   history.</li>
 * </ul>
 *
 * Before anything is removed, every open request and confirmed fixture with
 * a team outside the delete is cancelled and that team notified, the same as
 * when a team is suspended, so nobody turns up for a fixture that no longer
 * exists.
 */
@Service
public class CascadeDeletionService {

    private final UserRepository users;
    private final TeamRepository teams;
    private final ClubRepository clubs;
    private final MembershipRepository memberships;
    private final FriendlyRequestRepository requests;
    private final FriendlyRequestService friendlyRequests;
    private final CascadeDeletionRepository cascade;

    public CascadeDeletionService(UserRepository users, TeamRepository teams, ClubRepository clubs,
                                  MembershipRepository memberships, FriendlyRequestRepository requests,
                                  FriendlyRequestService friendlyRequests, CascadeDeletionRepository cascade) {
        this.users = users;
        this.teams = teams;
        this.clubs = clubs;
        this.memberships = memberships;
        this.requests = requests;
        this.friendlyRequests = friendlyRequests;
        this.cascade = cascade;
    }

    /** What the delete covers. Ordered maps so previews list things in a stable order. */
    record Plan(DeletionTargetType targetType, String targetId, String label, User user,
                Map<String, Team> teamsDeleted, Map<String, Club> clubsDeleted, List<String> warnings) {

        CascadeDeletionRepository.Scope scope(Instant notificationsBefore) {
            return new CascadeDeletionRepository.Scope(teamsDeleted.keySet(), clubsDeleted.keySet(),
                    user == null ? null : user.getId(), user == null ? null : user.getEmail(), notificationsBefore);
        }
    }

    public AdminDeletionDtos.DeletionPreview preview(DeletionTargetType type, String targetId) {
        Plan plan = plan(type, targetId);
        List<AdminDeletionDtos.CountLine> records = cascade.count(plan.scope(Instant.now())).entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> new AdminDeletionDtos.CountLine(e.getKey(), e.getValue()))
                .toList();
        return new AdminDeletionDtos.DeletionPreview(type.name(), targetId, plan.label(),
                plan.teamsDeleted().values().stream().map(t -> new AdminDeletionDtos.NamedItem(t.getId(), t.getName())).toList(),
                plan.clubsDeleted().values().stream().map(c -> new AdminDeletionDtos.NamedItem(c.getId(), c.getName())).toList(),
                otherTeamsAffected(plan.teamsDeleted().keySet()),
                records, plan.warnings());
    }

    /** Runs the delete. Returns what was removed, by kind, leaving out kinds with nothing to remove. */
    public LinkedHashMap<String, Long> delete(DeletionTargetType type, String targetId) {
        Plan plan = plan(type, targetId);
        Instant startedAt = Instant.now();
        friendlyRequests.cancelOpenForDeletedTeams(plan.teamsDeleted().keySet());
        LinkedHashMap<String, Long> removed = new LinkedHashMap<>();
        cascade.delete(plan.scope(startedAt)).forEach((label, n) -> {
            if (n > 0) removed.put(label, n);
        });
        return removed;
    }

    /** A one-line label for the target, or empty if it no longer exists. */
    public Optional<String> labelFor(DeletionTargetType type, String targetId) {
        return switch (type) {
            case USER -> users.findById(targetId).map(u -> u.getDisplayName() + " (" + u.getEmail() + ")");
            case TEAM -> teams.findById(targetId).map(CascadeDeletionService::teamLabel);
        };
    }

    Plan plan(DeletionTargetType type, String targetId) {
        return switch (type) {
            case USER -> planUser(targetId);
            case TEAM -> planTeam(targetId);
        };
    }

    private Plan planUser(String userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessRuleException(
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND, "That account could not be found."));
        List<Membership> own = memberships.findByUserId(userId);

        Set<String> clubIds = new LinkedHashSet<>();
        for (Membership m : own) {
            if (m.isClubScoped()) clubIds.add(m.getClubId());
            else teams.findById(m.getTeamId()).ifPresent(t -> clubIds.add(t.getClubId()));
        }

        Map<String, Team> teamsDeleted = new LinkedHashMap<>();
        Map<String, Club> clubsDeleted = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (String clubId : clubIds) {
            List<Team> clubTeams = teams.findByClubId(clubId);
            List<Membership> others = membersOf(clubId, clubTeams).stream()
                    .filter(m -> !m.getUserId().equals(userId))
                    .toList();
            String clubName = clubs.findById(clubId).map(Club::getName).orElse("A club");
            if (others.isEmpty()) {
                clubs.findById(clubId).ifPresent(c -> clubsDeleted.put(c.getId(), c));
                clubTeams.forEach(t -> teamsDeleted.put(t.getId(), t));
                continue;
            }
            boolean wasClubAdmin = own.stream().anyMatch(m -> clubId.equals(m.getClubId()) && m.getRole() == Role.CLUB_ADMIN);
            boolean otherClubAdmin = others.stream().anyMatch(m -> m.getRole() == Role.CLUB_ADMIN);
            if (wasClubAdmin && !otherClubAdmin) {
                warnings.add(clubName + " stays (" + others.size() + " other member"
                        + (others.size() == 1 ? "" : "s") + ") but will have no club admin left.");
            }
            for (Team t : clubTeams) {
                boolean member = own.stream().anyMatch(m -> t.getId().equals(m.getTeamId()));
                boolean othersOnTeam = others.stream().anyMatch(m -> t.getId().equals(m.getTeamId()));
                if (member && !othersOnTeam && !otherClubAdmin) {
                    warnings.add(t.getName() + " stays, but nobody will be able to manage it.");
                }
            }
        }
        return new Plan(DeletionTargetType.USER, userId, user.getDisplayName() + " (" + user.getEmail() + ")", user,
                teamsDeleted, clubsDeleted, warnings);
    }

    private Plan planTeam(String teamId) {
        Team team = teams.findById(teamId).orElseThrow(() -> new BusinessRuleException(
                "TEAM_NOT_FOUND", HttpStatus.NOT_FOUND, "That team could not be found."));
        Map<String, Team> teamsDeleted = new LinkedHashMap<>();
        teamsDeleted.put(team.getId(), team);

        Map<String, Club> clubsDeleted = new LinkedHashMap<>();
        boolean lastTeam = teams.findByClubId(team.getClubId()).stream().allMatch(t -> t.getId().equals(teamId));
        if (lastTeam && memberships.findByClubId(team.getClubId()).isEmpty()) {
            clubs.findById(team.getClubId()).ifPresent(c -> clubsDeleted.put(c.getId(), c));
        }

        List<String> warnings = new ArrayList<>();
        int members = memberships.findByTeamId(teamId).size();
        if (members > 0) {
            warnings.add(members + " member" + (members == 1 ? "" : "s")
                    + " lose access to this team. Their accounts are kept.");
        }
        if (lastTeam && clubsDeleted.isEmpty()) {
            warnings.add(Optional.ofNullable(team.getClubName()).orElse("The club")
                    + " is kept with no teams, because it still has club members.");
        }
        return new Plan(DeletionTargetType.TEAM, teamId, teamLabel(team), null, teamsDeleted, clubsDeleted, warnings);
    }

    private List<Membership> membersOf(String clubId, List<Team> clubTeams) {
        List<Membership> all = new ArrayList<>(memberships.findByClubId(clubId));
        clubTeams.forEach(t -> all.addAll(memberships.findByTeamId(t.getId())));
        return all;
    }

    /** e.g. "Riverside FC - fixture on 2026-11-01 will be cancelled". */
    private List<String> otherTeamsAffected(Set<String> teamIds) {
        List<String> lines = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String teamId : teamIds) {
            for (FriendlyRequest fr : requests.findByTeamId(teamId)) {
                if (fr.getStatus().isTerminal() || !seen.add(fr.getId())) continue;
                String otherId = fr.getSenderTeamId().equals(teamId) ? fr.getRecipientTeamId() : fr.getSenderTeamId();
                if (teamIds.contains(otherId)) continue;
                String other = teams.findById(otherId).map(Team::getName).orElse("Another team");
                String what = fr.getStatus() == RequestStatus.CONFIRMED || fr.getStatus() == RequestStatus.ACCEPTED
                        ? "confirmed fixture on " + fr.getDate() : "open request for " + fr.getDate();
                lines.add(other + " - " + what + " will be cancelled and they'll be notified");
            }
        }
        return lines;
    }

    private static String teamLabel(Team t) {
        return t.getClubName() == null || t.getClubName().isBlank() || t.getClubName().equals(t.getName())
                ? t.getName() : t.getName() + " (" + t.getClubName() + ")";
    }
}
