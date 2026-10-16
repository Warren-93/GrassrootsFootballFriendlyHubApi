package com.gffh.api.repository;

import com.gffh.api.domain.MemberInvite;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemberInviteRepository {

    Optional<MemberInvite> findById(String id);

    /** At most one per team and address; {@code email} is matched case-insensitively. */
    Optional<MemberInvite> findByTeamIdAndEmail(String teamId, String email);

    /** Unexpired invites for one team, newest first. */
    List<MemberInvite> findPendingByTeamId(String teamId, Instant now);

    /** Unexpired invites addressed to one email, across every team. */
    List<MemberInvite> findPendingByEmail(String email, Instant now);

    MemberInvite save(MemberInvite invite);

    void delete(String id);

    void deleteByEmail(String email);
}
