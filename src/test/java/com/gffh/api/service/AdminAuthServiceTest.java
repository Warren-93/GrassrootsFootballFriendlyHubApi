package com.gffh.api.service;

import com.gffh.api.config.RateLimitProperties;
import com.gffh.api.dto.admin.AdminAuthDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.repository.PlatformAdminRepository;
import com.gffh.api.security.AdminJwtService;
import com.gffh.api.security.RateLimiter;
import com.gffh.api.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuthServiceTest {

    private final PlatformAdminRepository admins = mock(PlatformAdminRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TotpService totpService = mock(TotpService.class);
    private final AdminJwtService jwt = mock(AdminJwtService.class);
    private final PlatformAdmin admin = new PlatformAdmin("a1", "admin@test.dev", "hash", "SECRET", Instant.now(), null, null);

    @BeforeEach
    void setUp() {
        when(admins.findByEmail("admin@test.dev")).thenReturn(Optional.of(admin));
        when(admins.findById("a1")).thenReturn(Optional.of(admin));
        when(admins.save(any())).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.matches("right-password", "hash")).thenReturn(true);
        when(jwt.issuePending2faToken("a1")).thenReturn("pending");
        when(jwt.verifyPending2faToken("pending")).thenReturn("a1");
        when(jwt.issueSessionToken(anyString(), anyString())).thenReturn("session");
        when(totpService.verifyCode("SECRET", "123456")).thenReturn(true);
    }

    @Test
    @DisplayName("with authenticator codes off, the password alone signs an admin in")
    void passwordOnlyWhenTotpOff() {
        var service = service(false);

        var result = service.login(new AdminAuthDtos.LoginRequest("admin@test.dev", "right-password"));

        assertThat(result.accessToken()).isEqualTo("session");
        assertThat(result.email()).isEqualTo("admin@test.dev");
        // Older admin sites still post any code to /login/verify.
        assertThat(service.verify(new AdminAuthDtos.VerifyRequest("pending", "000000")).accessToken()).isEqualTo("session");
    }

    @Test
    @DisplayName("with authenticator codes on, login needs the code before a session is issued")
    void codeRequiredWhenTotpOn() {
        var service = service(true);

        var result = service.login(new AdminAuthDtos.LoginRequest("admin@test.dev", "right-password"));

        assertThat(result.pendingToken()).isEqualTo("pending");
        assertThat(result.accessToken()).isNull();
        assertThatThrownBy(() -> service.verify(new AdminAuthDtos.VerifyRequest("pending", "000000")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(service.verify(new AdminAuthDtos.VerifyRequest("pending", "123456")).accessToken()).isEqualTo("session");
    }

    @Test
    @DisplayName("a wrong password is still rejected with codes off")
    void wrongPasswordRejected() {
        var service = service(false);

        assertThatThrownBy(() -> service.login(new AdminAuthDtos.LoginRequest("admin@test.dev", "wrong")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("failed attempts are rate limited per email, successful ones never are")
    void failedAttemptsAreRateLimited() {
        var service = service(false);
        for (int i = 0; i < 15; i++) {
            service.login(new AdminAuthDtos.LoginRequest("admin@test.dev", "right-password"));
        }
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> service.login(new AdminAuthDtos.LoginRequest("admin@test.dev", "wrong")))
                    .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo("INVALID_CREDENTIALS");
        }

        assertThatThrownBy(() -> service.login(new AdminAuthDtos.LoginRequest("Admin@test.dev", "right-password")))
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo("RATE_LIMITED");
    }

    private AdminAuthService service(boolean totpRequired) {
        return new AdminAuthService(admins, passwordEncoder, totpService, jwt,
                new RateLimiter(), new RateLimitProperties(), totpRequired);
    }
}
