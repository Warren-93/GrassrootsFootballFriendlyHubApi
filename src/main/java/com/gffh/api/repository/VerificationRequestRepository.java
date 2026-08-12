package com.gffh.api.repository;

import com.gffh.api.domain.VerificationRequest;
import com.gffh.api.domain.VerificationRequestStatus;

import java.util.List;
import java.util.Optional;

public interface VerificationRequestRepository {

    Optional<VerificationRequest> findById(String id);

    Optional<VerificationRequest> findPendingByTeamId(String teamId);

    /** Most recently submitted request for this team, regardless of status - for the manager's own status view. */
    Optional<VerificationRequest> findLatestByTeamId(String teamId);

    List<VerificationRequest> findByStatus(VerificationRequestStatus status);

    VerificationRequest save(VerificationRequest request);
}
