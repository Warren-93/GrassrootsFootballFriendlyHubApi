package com.gffh.api.repository;

import com.gffh.api.model.RefreshToken;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken token);

    void revoke(String id);

    /** Revokes a token because it was exchanged for a new one at {@code at}. */
    void markRotated(String id, Instant at);

    void revokeAllForUser(String userId);

    void revokeAllForUserExcept(String userId, String keepTokenId);
}
