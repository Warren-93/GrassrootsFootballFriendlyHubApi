package com.gffh.api.service;

import com.gffh.api.domain.Club;
import com.gffh.api.domain.Membership;
import com.gffh.api.domain.Role;
import com.gffh.api.domain.Team;
import com.gffh.api.domain.User;
import com.gffh.api.repository.ClubRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.web.PrivacyDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/** SCR-PR-10 Privacy and data. Purpose: let a user see and remove what the platform holds on them. */
@Service
public class PrivacyService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final TeamRepository teams;
    private final ClubRepository clubs;

    public PrivacyService(UserRepository users, MembershipRepository memberships, TeamRepository teams,
                          ClubRepository clubs) {
        this.users = users;
        this.memberships = memberships;
        this.teams = teams;
        this.clubs = clubs;
    }

    public PrivacyDtos.AccountExport export(String userId) {
        User user = findUser(userId);
        List<PrivacyDtos.MembershipExport> views = memberships.findByUserId(userId).stream()
                .map(this::toMembershipExport)
                .toList();
        return PrivacyDtos.AccountExport.of(user, views);
    }

    /**
     * A user who is the sole CLUB_ADMIN of any club cannot delete their account until they
     * promote someone else - otherwise that club is left with no one able to manage it,
     * the same rule SCR-PR-04's member removal enforces.
     */
    public void deleteAccount(String userId) {
        findUser(userId);
        List<Membership> own = memberships.findByUserId(userId);

        for (Membership m : own) {
            if (m.role() == Role.CLUB_ADMIN) {
                boolean anotherAdminExists = memberships.findByClubId(m.clubId()).stream()
                        .anyMatch(other -> other.role() == Role.CLUB_ADMIN && !other.userId().equals(userId));
                if (!anotherAdminExists) {
                    throw new BusinessRuleException("LAST_CLUB_ADMIN", HttpStatus.CONFLICT,
                            "You're the only admin for at least one club - promote someone else there before deleting your account.");
                }
            }
        }

        own.forEach(m -> memberships.delete(m.id()));
        users.delete(userId);
    }

    private PrivacyDtos.MembershipExport toMembershipExport(Membership m) {
        if (m.isClubScoped()) {
            String clubName = clubs.findById(m.clubId()).map(Club::name).orElse(null);
            return new PrivacyDtos.MembershipExport(m.id(), m.role().name(), "CLUB", null, null,
                    m.clubId(), clubName, m.createdAt());
        }
        Team team = teams.findById(m.teamId()).orElse(null);
        return new PrivacyDtos.MembershipExport(m.id(), m.role().name(), "TEAM", m.teamId(),
                team != null ? team.name() : null, team != null ? team.clubId() : null,
                team != null ? team.clubName() : null, m.createdAt());
    }

    private User findUser(String userId) {
        return users.findById(userId).orElseThrow(BusinessRuleException::blocked);
    }
}
