package com.gffh.api.repository;

import com.gffh.api.model.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("{ '_id': ?0 }")
    @Update("{ $set: { 'revoked': true }, $unset: { 'rotatedAt': '' } }")
    void revoke(String id);

    /** Revokes a token because it was exchanged for a new one at {@code at}. */
    @Query("{ '_id': ?0 }")
    @Update("{ $set: { 'revoked': true, 'rotatedAt': ?1 } }")
    void markRotated(String id, Instant at);

    @Query("{ 'userId': ?0 }")
    @Update("{ $set: { 'revoked': true }, $unset: { 'rotatedAt': '' } }")
    void revokeAllForUser(String userId);

    @Query("{ 'userId': ?0, '_id': { $ne: ?1 } }")
    @Update("{ $set: { 'revoked': true }, $unset: { 'rotatedAt': '' } }")
    void revokeAllForUserExcept(String userId, String keepTokenId);
}
