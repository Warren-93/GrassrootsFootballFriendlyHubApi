package com.gffh.api.repository;

import com.gffh.api.model.VerificationRequest;
import com.gffh.api.model.enums.VerificationRequestStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VerificationRequestRepository extends MongoRepository<VerificationRequest, String> {

    /** The request still waiting on an admin, if any. */
    @Query("{ 'teamId': ?0, 'status': { $in: [ 'PENDING', 'AWAITING_SECOND_REJECTION' ] } }")
    Optional<VerificationRequest> findPendingByTeamId(String teamId);

    /** Most recently submitted request for this team, regardless of status - for the manager's own status view. */
    Optional<VerificationRequest> findFirstByTeamIdOrderBySubmittedAtDesc(String teamId);

    List<VerificationRequest> findByStatus(VerificationRequestStatus status);
}
