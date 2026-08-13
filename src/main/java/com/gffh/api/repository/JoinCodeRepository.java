package com.gffh.api.repository;

import com.gffh.api.domain.JoinCode;

import java.util.Optional;

public interface JoinCodeRepository {

    Optional<JoinCode> findByTeamId(String teamId);

    Optional<JoinCode> findByCode(String code);

    JoinCode save(JoinCode joinCode);

    /** Called before saving a fresh code, so a team never has more than one active at once. */
    void deleteByTeamId(String teamId);
}
