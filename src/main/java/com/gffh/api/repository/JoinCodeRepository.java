package com.gffh.api.repository;

import com.gffh.api.model.JoinCode;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface JoinCodeRepository extends MongoRepository<JoinCode, String> {

    Optional<JoinCode> findByTeamId(String teamId);

    Optional<JoinCode> findByCode(String code);

    /** Called before saving a fresh code, so a team never has more than one active at once. */
    void deleteByTeamId(String teamId);
}
