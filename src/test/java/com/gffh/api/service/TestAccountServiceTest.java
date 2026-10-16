package com.gffh.api.service;

import com.gffh.api.domain.*;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.TestAccountRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.web.AdminTestAccountDtos;
import com.gffh.api.web.TeamDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TestAccountServiceTest {

    private final TestAccountRepository testAccounts = mock(TestAccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TeamRepository teams = mock(TeamRepository.class);
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final TeamService teamService = mock(TeamService.class);
    private final PrivacyService privacyService = mock(PrivacyService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditLogService audit = mock(AuditLogService.class);

    private final TestAccountService service = new TestAccountService(testAccounts, users, teams, memberships,
            teamService, privacyService, passwordEncoder, audit);

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("TestPass123!")).thenReturn("hash");
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            return new User("u-" + UUID.randomUUID(), u.email(), u.passwordHash(), u.displayName(),
                    u.emailVerified(), u.createdAt(), u.suspended());
        });
        when(testAccounts.save(any())).thenAnswer(i -> {
            TestAccount a = i.getArgument(0);
            return a.id() != null ? a : new TestAccount("t1", a.userId(), a.email(), a.displayName(), a.kind(),
                    a.clubId(), a.teamId(), a.createdByAdminId(), a.createdAt());
        });
    }

    @Test
    @DisplayName("a verified account is saved as a verified user and recorded in test_accounts")
    void verifiedAccount() {
        var result = service.create("admin", request(TestAccountKind.VERIFIED, 1, null));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().emailVerified()).isTrue();
        assertThat(user.getValue().passwordHash()).isEqualTo("hash");
        assertThat(user.getValue().email()).startsWith("kickabout-test+").endsWith("@example.com");
        verify(testAccounts).save(argThatKind(TestAccountKind.VERIFIED));
        verifyNoInteractions(teamService);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("a manager gets a team built from the chosen options, and the record keeps its ids")
    void managerAccount() {
        Team created = team("team-1", "club-1");
        when(teamService.create(anyString(), any())).thenReturn(created);

        service.create("admin", request(TestAccountKind.MANAGER, 1,
                new AdminTestAccountDtos.TeamOptions(AgeGroup.U9, Gender.BOYS, null, null, null, null, null)));

        ArgumentCaptor<TeamDtos.CreateTeamRequest> team = ArgumentCaptor.forClass(TeamDtos.CreateTeamRequest.class);
        verify(teamService).create(anyString(), team.capture());
        assertThat(team.getValue().ageGroup()).isEqualTo(AgeGroup.U9);
        assertThat(team.getValue().gender()).isEqualTo(Gender.BOYS);
        assertThat(team.getValue().abilityLevel()).isEqualTo(AbilityLevel.INTERMEDIATE);
        assertThat(team.getValue().postcode()).isEqualTo("FK6 5AA");

        ArgumentCaptor<TestAccount> saved = ArgumentCaptor.forClass(TestAccount.class);
        verify(testAccounts, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).teamId()).isEqualTo("team-1");
        assertThat(saved.getAllValues().get(1).clubId()).isEqualTo("club-1");
    }

    @Test
    @DisplayName("delete archives the team and drops memberships before deleting the user")
    void deleteManager() {
        when(testAccounts.findById("t1")).thenReturn(Optional.of(new TestAccount("t1", "u1",
                "kickabout-test+a@example.com", "Test", TestAccountKind.MANAGER, "c1", "team-1", "admin", Instant.now())));
        when(users.findById("u1")).thenReturn(Optional.of(
                new User("u1", "kickabout-test+a@example.com", "h", "Test", true, Instant.now(), false)));
        when(memberships.findByUserId("u1")).thenReturn(List.of(
                new Membership("m1", "u1", null, "c1", Role.CLUB_ADMIN, Instant.now())));

        service.delete("admin", "t1");

        InOrder order = inOrder(teamService, memberships, privacyService, testAccounts);
        order.verify(teamService).archiveTestTeam("team-1");
        order.verify(memberships).delete("m1");
        order.verify(privacyService).deleteAccount("u1");
        order.verify(testAccounts).delete("t1");
    }

    @Test
    @DisplayName("deleting an unknown id is a 404, and touches nothing")
    void deleteUnknown() {
        when(testAccounts.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete("admin", "nope"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("could not be found");
        verifyNoInteractions(privacyService);
    }

    private static AdminTestAccountDtos.CreateRequest request(TestAccountKind kind, int count,
                                                              AdminTestAccountDtos.TeamOptions team) {
        return new AdminTestAccountDtos.CreateRequest(kind, count, "TestPass123!", null, team);
    }

    private static TestAccount argThatKind(TestAccountKind kind) {
        return argThat(a -> a.kind() == kind);
    }

    private static Team team(String id, String clubId) {
        Team team = mock(Team.class);
        when(team.id()).thenReturn(id);
        when(team.clubId()).thenReturn(clubId);
        return team;
    }
}
