package com.gffh.api.repository;

import com.gffh.api.domain.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken token);

    void revoke(String id);

    void revokeAllForUser(String userId);
}
