package com.gffh.api.repository;

import com.gffh.api.domain.Membership;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository {

    Optional<Membership> findTeamMembership(String userId, String teamId);

    Optional<Membership> findClubMembership(String userId, String clubId);

    List<Membership> findByUserId(String userId);

    Membership save(Membership membership);
}
