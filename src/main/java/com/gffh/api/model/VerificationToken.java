package com.gffh.api.model;

import com.gffh.api.model.enums.VerificationTokenPurpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A single-use token backing the email-verification and password-reset flows
 * (SCR-AU-05, SCR-AU-06). Delivery is out of scope here - no email provider is
 * configured yet, so the token is logged rather than emailed; see AuthService.
 *
 * <p>{@code email} is the address the token was sent to. An email-verification
 * token proves ownership of that address only, so it stops applying once the
 * account changes to another one (see AuthService#confirmVerification).
 */
@Document("verificationTokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationToken {

    @Id
    private String id;
    private String userId;
    private String token;
    private VerificationTokenPurpose purpose;
    private String email;
    private Instant expiresAt;
    private boolean used;
    private Instant createdAt;

    public boolean isUsable(Instant now) {
        return !used && now.isBefore(expiresAt);
    }

    /** Whether this token was sent to {@code address}. Tokens saved before addresses were recorded match nothing. */
    public boolean wasSentTo(String address) {
        return email != null && address != null && email.trim().equalsIgnoreCase(address.trim());
    }
}
