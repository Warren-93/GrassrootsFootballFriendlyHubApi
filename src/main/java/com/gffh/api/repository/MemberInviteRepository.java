package com.gffh.api.repository;

import com.gffh.api.model.MemberInvite;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Invite emails are stored trimmed and lower-cased (see
 * {@code EmailNormalisingCallback}), so the email lookups here normalise the
 * address they are given the same way before querying.
 */
public interface MemberInviteRepository extends MongoRepository<MemberInvite, String> {

    Optional<MemberInvite> findOneByTeamIdAndEmail(String teamId, String normalisedEmail);

    /** Unexpired invites for one team, newest first. */
    @Query(value = "{ 'teamId': ?0, 'expiresAt': { $gt: ?1 } }", sort = "{ 'createdAt': -1 }")
    List<MemberInvite> findPendingByTeamId(String teamId, Instant now);

    @Query(value = "{ 'email': ?0, 'expiresAt': { $gt: ?1 } }", sort = "{ 'createdAt': 1 }")
    List<MemberInvite> findPendingByNormalisedEmail(String normalisedEmail, Instant now);

    void deleteAllByEmail(String normalisedEmail);

    /** At most one per team and address; {@code email} is matched case-insensitively. */
    default Optional<MemberInvite> findByTeamIdAndEmail(String teamId, String email) {
        return findOneByTeamIdAndEmail(teamId, normalised(email));
    }

    /** Unexpired invites addressed to one email, across every team, oldest first. */
    default List<MemberInvite> findPendingByEmail(String email, Instant now) {
        return findPendingByNormalisedEmail(normalised(email), now);
    }

    default void deleteByEmail(String email) {
        deleteAllByEmail(normalised(email));
    }

    static String normalised(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
