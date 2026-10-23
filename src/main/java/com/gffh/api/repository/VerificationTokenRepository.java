package com.gffh.api.repository;

import com.gffh.api.model.VerificationToken;
import com.gffh.api.model.enums.VerificationTokenPurpose;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.Optional;

public interface VerificationTokenRepository extends MongoRepository<VerificationToken, String> {

    Optional<VerificationToken> findByTokenAndPurpose(String token, VerificationTokenPurpose purpose);

    @Query("{ '_id': ?0 }")
    @Update("{ $set: { 'used': true } }")
    void markUsed(String id);
}
