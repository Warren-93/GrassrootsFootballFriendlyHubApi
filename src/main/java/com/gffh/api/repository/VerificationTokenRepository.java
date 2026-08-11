package com.gffh.api.repository;

import com.gffh.api.domain.VerificationToken;
import com.gffh.api.domain.VerificationTokenPurpose;

import java.util.Optional;

public interface VerificationTokenRepository {

    Optional<VerificationToken> findByToken(String token, VerificationTokenPurpose purpose);

    VerificationToken save(VerificationToken token);

    void markUsed(String id);
}
