package com.gffh.api.service;

import com.gffh.api.dto.TeamDtos;
import com.gffh.api.dto.admin.AdminTestAccountDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.TestAccountRepository;
import com.gffh.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Generates accounts for testing and removes them again. Each one is a normal
 * user (plus club and team for managers) so it signs in and behaves like a
 * real account, and is also recorded in the separate test_accounts
 * collection, which is the only place listing and cleanup ever look.
 */
@Service
public class TestAccountService {

    static final String EMAIL_PREFIX = "kickabout-test+";
    private static final String DEFAULT_DOMAIN = "example.com";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TestAccountRepository testAccounts;
    private final UserRepository users;
    private final TeamRepository teams;
    private final MembershipRepository memberships;
    private final TeamService teamService;
    private final PrivacyService privacyService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public TestAccountService(TestAccountRepository testAccounts, UserRepository users, TeamRepository teams,
                              MembershipRepository memberships, TeamService teamService,
                              PrivacyService privacyService, PasswordEncoder passwordEncoder,
                              AuditLogService auditLogService) {
        this.testAccounts = testAccounts;
        this.users = users;
        this.teams = teams;
        this.memberships = memberships;
        this.teamService = teamService;
        this.privacyService = privacyService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public List<AdminTestAccountDtos.TestAccountView> create(String adminId, AdminTestAccountDtos.CreateRequest request) {
        String domain = request.emailDomain() != null ? request.emailDomain() : DEFAULT_DOMAIN;
        String passwordHash = passwordEncoder.encode(request.password());
        String batch = HexFormat.of().formatHex(randomBytes(3));

        List<AdminTestAccountDtos.TestAccountView> created = new ArrayList<>();
        for (int i = 1; i <= request.count(); i++) {
            created.add(createOne(adminId, request, batch + i, domain, passwordHash));
        }
        return created;
    }

    public List<AdminTestAccountDtos.TestAccountView> list() {
        return testAccounts.findAll().stream().map(this::view).toList();
    }

    /**
     * Removes the user and archives their test team. Memberships go first so
     * the account is no longer a club's last admin, which is what blocks the
     * normal account deletion.
     */
    public void delete(String adminId, String id) {
        TestAccount account = testAccounts.findById(id).orElseThrow(() -> new BusinessRuleException(
                "TEST_ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "That test account could not be found."));

        if (account.teamId() != null) teamService.archiveTestTeam(account.teamId());
        if (users.findById(account.userId()).isPresent()) {
            memberships.findByUserId(account.userId()).forEach(m -> memberships.delete(m.id()));
            privacyService.deleteAccount(account.userId());
        }
        testAccounts.delete(account.id());
        auditLogService.record(adminId, "TEST_ACCOUNT_DELETED", "USER", account.userId(),
                "Test account removed (was " + account.email() + ")");
    }

    private AdminTestAccountDtos.TestAccountView createOne(String adminId, AdminTestAccountDtos.CreateRequest request,
                                                           String suffix, String domain, String passwordHash) {
        String email = EMAIL_PREFIX + suffix + "@" + domain;
        if (users.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("EMAIL_ALREADY_REGISTERED", HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        }
        boolean manager = request.kind() == TestAccountKind.MANAGER;
        String displayName = "Test " + (manager ? "Manager " : "User ") + suffix;
        boolean verified = request.kind() != TestAccountKind.NEW;

        User user = users.save(new User(null, email, passwordHash, displayName, verified, Instant.now(), false));
        // Recorded straight away, so a failure creating the team still leaves the user removable.
        TestAccount account = testAccounts.save(new TestAccount(null, user.id(), email, displayName,
                request.kind(), null, null, adminId, Instant.now()));

        if (manager) {
            Team team = teamService.create(user.id(), teamRequest(suffix, displayName, request.team()));
            account = testAccounts.save(new TestAccount(account.id(), account.userId(), email, displayName,
                    account.kind(), team.clubId(), team.id(), adminId, account.createdAt()));
        }
        auditLogService.record(adminId, "TEST_ACCOUNT_CREATED", "USER", user.id(),
                request.kind() + " test account " + email);
        return view(account);
    }

    private static TeamDtos.CreateTeamRequest teamRequest(String suffix, String managerName,
                                                          AdminTestAccountDtos.TeamOptions options) {
        var o = options != null ? options : new AdminTestAccountDtos.TeamOptions(null, null, null, null, null, null, null);
        AgeGroup ageGroup = o.ageGroup() != null ? o.ageGroup() : AgeGroup.U12;
        String clubName = "Test FC " + suffix;
        return new TeamDtos.CreateTeamRequest(
                null, clubName, clubName + " " + ageGroup.name(), null,
                ageGroup,
                o.gender() != null ? o.gender() : Gender.MIXED,
                o.format(),
                o.abilityLevel() != null ? o.abilityLevel() : AbilityLevel.INTERMEDIATE,
                null,
                // Denny, Falkirk: a real place, so distance-based matching has something to work with.
                o.postcode() != null ? o.postcode() : "FK6 5AA",
                o.longitude() != null ? o.longitude() : -3.9076,
                o.latitude() != null ? o.latitude() : 56.0236,
                20, HomeAwayPreference.EITHER, managerName, null,
                "Test team created by the test dashboard.", null);
    }

    private AdminTestAccountDtos.TestAccountView view(TestAccount a) {
        boolean verified = users.findById(a.userId()).map(User::emailVerified).orElse(false);
        String teamName = a.teamId() == null ? null : teams.findById(a.teamId()).map(Team::name).orElse(null);
        return new AdminTestAccountDtos.TestAccountView(a.id(), a.userId(), a.email(), a.displayName(),
                a.kind().name(), verified, a.clubId(), a.teamId(), teamName, a.createdAt());
    }

    private static byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
