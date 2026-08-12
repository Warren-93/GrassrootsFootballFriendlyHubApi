package com.gffh.api.repository;

import com.gffh.api.domain.VerificationRequest;
import com.gffh.api.domain.VerificationRequestStatus;

import java.util.List;
import java.util.Optional;

public interface VerificationRequestRepository {

    Optional<VerificationRequest> findById(String id);

    Optional<VerificationRequest> findPendingByTeamId(String teamId);

    List<VerificationRequest> findByStatus(VerificationRequestStatus status);

    VerificationRequest save(VerificationRequest request);
}
