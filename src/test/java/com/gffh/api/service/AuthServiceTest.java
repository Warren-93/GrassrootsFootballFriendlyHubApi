package com.gffh.api.service;

import com.gffh.api.config.RateLimitProperties;
import com.gffh.api.domain.User;
import com.gffh.api.domain.VerificationToken;
import com.gffh.api.domain.VerificationTokenPurpose;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.web.AuthDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link AuthService#confirmVerification}: what a verification link proves, and what it unlocks. */
class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final VerificationTokenService verificationTokens = mock(VerificationTokenService.class);
    private final MemberInviteService memberInvites = mock(MemberInviteService.class);
    private final AuthService service = new AuthService(users, mock(PasswordEncoder.class), mock(JwtService.class),
            mock(RefreshTokenService.class), verificationTokens, mock(RateLimiter.class), new RateLimitProperties(),
            memberInvites, mock(OAuthTokenVerifier.class));

    private final User unverified = new User("u1", "player@example.com", "hash", "Player", false, Instant.now(), false);

    @BeforeEach
    void setUp() {
        when(users.findById("u1")).thenReturn(Optional.of(unverified));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("an emailed link verifies the address and accepts the invites waiting for it")
    void emailedLinkAcceptsInvites() {
        linkSentTo("Player@Example.com");
        when(verificationTokens.emailsTokens()).thenReturn(true);

        service.confirmVerification(new AuthDtos.VerifyConfirmRequest("raw"));

        verify(users).save(verifiedPlayer());
        verify(memberInvites).acceptPendingInvites(verifiedPlayer());
    }

    @Test
    @DisplayName("without an email provider the link still verifies, but accepts no invites")
    void unemailedLinkAcceptsNoInvites() {
        linkSentTo("player@example.com");
        when(verificationTokens.emailsTokens()).thenReturn(false);

        service.confirmVerification(new AuthDtos.VerifyConfirmRequest("raw"));

        verify(users).save(verifiedPlayer());
        verify(memberInvites, never()).acceptPendingInvites(any());
    }

    @Test
    @DisplayName("a link sent to an address the account has since changed from verifies nothing")
    void linkForAnotherAddressIsRejected() {
        linkSentTo("old-address@example.com");
        when(verificationTokens.emailsTokens()).thenReturn(true);

        assertThatThrownBy(() -> service.confirmVerification(new AuthDtos.VerifyConfirmRequest("raw")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).code())
                .isEqualTo("INVALID_VERIFICATION_TOKEN");
        verify(users, never()).save(any());
        verify(memberInvites, never()).acceptPendingInvites(any());
    }

    @Test
    @DisplayName("tokens saved before addresses were recorded match no address")
    void tokenWithoutAddressMatchesNothing() {
        VerificationToken legacy = new VerificationToken("t1", "u1", "raw", VerificationTokenPurpose.EMAIL_VERIFY,
                null, Instant.now().plusSeconds(60), false, Instant.now());

        assertThat(legacy.wasSentTo("player@example.com")).isFalse();
    }

    @Test
    @DisplayName("losing a race to register an address is a conflict, not a server error")
    void registerRaceIsAConflict() {
        when(users.findByEmail("racer@example.com")).thenReturn(Optional.empty());
        when(users.save(any())).thenThrow(new DuplicateKeyException("users.email"));

        assertThatThrownBy(() -> service.register(
                new AuthDtos.RegisterRequest("racer@example.com", "TestPass123!", "Racer")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).code())
                .isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    private void linkSentTo(String address) {
        when(verificationTokens.consumeToken("raw", VerificationTokenPurpose.EMAIL_VERIFY))
                .thenReturn(Optional.of(new VerificationToken("t1", "u1", "raw", VerificationTokenPurpose.EMAIL_VERIFY,
                        address, Instant.now().plusSeconds(60), false, Instant.now())));
    }

    private User verifiedPlayer() {
        return new User("u1", "player@example.com", "hash", "Player", true, unverified.createdAt(), false);
    }
}
