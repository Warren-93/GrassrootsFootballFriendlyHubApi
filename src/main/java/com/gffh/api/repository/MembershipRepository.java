package com.gffh.api.repository;

import com.gffh.api.domain.Membership;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository {

    Optional<Membership> findTeamMembership(String userId, String teamId);

    Optional<Membership> findClubMembership(String userId, String clubId);

    List<Membership> findByUserId(String userId);

    /** Every membership scoped to this exact team (not the club it belongs to). Admin-only use (ADM-05 merge). */
    List<Membership> findByTeamId(String teamId);

    Membership save(Membership membership);
}
