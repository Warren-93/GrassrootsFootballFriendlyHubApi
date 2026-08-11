package com.gffh.api.repository;

import com.gffh.api.domain.Fixture;

import java.util.List;
import java.util.Optional;

public interface FixtureRepository {

    Optional<Fixture> findById(String id);

    List<Fixture> findByTeamId(String teamId);

    Fixture save(Fixture fixture);
}
