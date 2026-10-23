package com.gffh.api.repository;

import com.gffh.api.model.DeletionRequest;
import com.gffh.api.model.enums.DeletionRequestStatus;
import com.gffh.api.model.enums.DeletionTargetType;
import com.gffh.api.repository.custom.DeletionRequestRepositoryCustom;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeletionRequestRepository extends MongoRepository<DeletionRequest, String>,
        DeletionRequestRepositoryCustom {

    @Query("{ 'targetType': ?0, 'targetId': ?1, 'status': 'PENDING' }")
    Optional<DeletionRequest> findPendingFor(DeletionTargetType targetType, String targetId);

    List<DeletionRequest> findByStatusOrderByRequestedAtDesc(DeletionRequestStatus status, Limit limit);

    List<DeletionRequest> findAllByOrderByRequestedAtDesc(Limit limit);

    /** Newest first; all statuses when {@code status} is null. */
    default List<DeletionRequest> list(DeletionRequestStatus status, int limit) {
        return status == null
                ? findAllByOrderByRequestedAtDesc(Limit.of(limit))
                : findByStatusOrderByRequestedAtDesc(status, Limit.of(limit));
    }
}
