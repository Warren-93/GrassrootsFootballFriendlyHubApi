package com.gffh.api.service;

import com.gffh.api.dto.PrivacyDtos;
import com.gffh.api.dto.TeamDtos;
import com.gffh.api.dto.VenueDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.Club;
import com.gffh.api.model.Membership;
import com.gffh.api.model.Team;
import com.gffh.api.model.User;
import com.gffh.api.model.Venue;
import com.gffh.api.model.enums.Role;
import com.gffh.api.repository.AvailabilityRepository;
import com.gffh.api.repository.BlockRepository;
import com.gffh.api.repository.ClubRepository;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.NotificationPreferenceRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.repository.VenueRepository;
import com.gffh.api.service.push.PushService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** SCR-PR-10 Privacy and data. Purpose: let a user see and remove what the platform holds on them. */
@Service
public class PrivacyService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final TeamRepository teams;
    private final ClubRepository clubs;
    private final TeamService teamService;
    private final VenueRepository venues;
    private final AvailabilityRepository availability;
    private final FriendlyRequestRepository requests;
    private final FixtureRepository fixtures;
    private final BlockRepository blocks;
    private final NotificationPreferenceRepository notificationPreferences;
    private final PushService pushService;
    private final MemberInviteService memberInvites;

    public PrivacyService(UserRepository users, MembershipRepository memberships, TeamRepository teams,
                          ClubRepository clubs, TeamService teamService, VenueRepository venues,
                          AvailabilityRepository availability, FriendlyRequestRepository requests,
                          FixtureRepository fixtures, BlockRepository blocks,
                          NotificationPreferenceRepository notificationPreferences,
                          PushService pushService, MemberInviteService memberInvites) {
        this.users = users;
        this.memberships = memberships;
        this.teams = teams;
        this.clubs = clubs;
        this.teamService = teamService;
        this.venues = venues;
        this.availability = availability;
        this.requests = requests;
        this.fixtures = fixtures;
        this.blocks = blocks;
        this.notificationPreferences = notificationPreferences;
        this.pushService = pushService;
        this.memberInvites = memberInvites;
    }

    public PrivacyDtos.AccountExport export(String userId) {
        User user = findUser(userId);
        List<PrivacyDtos.MembershipExport> views = memberships.findByUserId(userId).stream()
                .map(this::toMembershipExport)
                .toList();
        List<PrivacyDtos.TeamExport> teamExports = teamService.listMine(userId).stream()
                .filter(t -> teamService.canManage(userId, t.getId()))
                .map(this::toTeamExport)
                .toList();
        return PrivacyDtos.AccountExport.of(user, views,
                notificationPreferences.findByUserId(userId).orElse(null), teamExports);
    }

    private PrivacyDtos.TeamExport toTeamExport(Team team) {
        LocalDate from = LocalDate.now().minusYears(5);
        List<Venue> clubVenues = venues.findByClubId(team.getClubId());
        return new PrivacyDtos.TeamExport(
                TeamDtos.TeamView.from(team, true, team.getDefaultVenueId() != null || !clubVenues.isEmpty()),
                clubVenues.stream().map(VenueDtos.VenueView::from).toList(),
                availability.findBookableForTeamBetween(team.getId(), from, LocalDate.now().plusYears(2)),
                requests.findByTeamId(team.getId()),
                fixtures.findByTeamId(team.getId()),
                blocks.findByBlockingTeamId(team.getId()));
    }

    /**
     * A user who is the sole CLUB_ADMIN of any club cannot delete their account until they
     * promote someone else - otherwise that club is left with no one able to manage it,
     * the same rule SCR-PR-04's member removal enforces.
     */
    public void deleteAccount(String userId) {
        User user = findUser(userId);
        List<Membership> own = memberships.findByUserId(userId);

        for (Membership m : own) {
            if (m.getRole() == Role.CLUB_ADMIN) {
                boolean anotherAdminExists = memberships.findByClubId(m.getClubId()).stream()
                        .anyMatch(other -> other.getRole() == Role.CLUB_ADMIN && !other.getUserId().equals(userId));
                if (!anotherAdminExists) {
                    throw new BusinessRuleException("LAST_CLUB_ADMIN", HttpStatus.CONFLICT,
                            "You're the only admin for at least one club - promote someone else there before deleting your account.");
                }
            }
        }

        own.forEach(m -> memberships.delete(m.getId()));
        // The account's devices stop getting pushes, and nobody can be added
        // to a team later by an invite to the address it used.
        pushService.deleteAllFor(userId);
        memberInvites.deleteInvitesFor(user.getEmail());
        users.delete(userId);
    }

    private PrivacyDtos.MembershipExport toMembershipExport(Membership m) {
        if (m.isClubScoped()) {
            String clubName = clubs.findById(m.getClubId()).map(Club::getName).orElse(null);
            return new PrivacyDtos.MembershipExport(m.getId(), m.getRole().name(), "CLUB", null, null,
                    m.getClubId(), clubName, m.getCreatedAt());
        }
        Team team = teams.findById(m.getTeamId()).orElse(null);
        return new PrivacyDtos.MembershipExport(m.getId(), m.getRole().name(), "TEAM", m.getTeamId(),
                team != null ? team.getName() : null, team != null ? team.getClubId() : null,
                team != null ? team.getClubName() : null, m.getCreatedAt());
    }

    private User findUser(String userId) {
        return users.findById(userId).orElseThrow(BusinessRuleException::blocked);
    }
}
