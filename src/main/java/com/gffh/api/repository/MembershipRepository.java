package com.gffh.api.repository;

import com.gffh.api.model.Membership;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends MongoRepository<Membership, String> {

    Optional<Membership> findByUserIdAndTeamId(String userId, String teamId);

    Optional<Membership> findByUserIdAndClubId(String userId, String clubId);

    List<Membership> findByUserId(String userId);

    /** Every membership scoped to this exact team (not the club it belongs to). Admin-only use (ADM-05 merge). */
    List<Membership> findByTeamId(String teamId);

    /** Every membership scoped to this club (SCR-PR-04's cascading club-admin rows, and the last-admin guard). */
    List<Membership> findByClubId(String clubId);
}
