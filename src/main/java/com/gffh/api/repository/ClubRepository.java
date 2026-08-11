package com.gffh.api.repository;

import com.gffh.api.domain.Club;

import java.util.Optional;

public interface ClubRepository {

    Optional<Club> findById(String id);

    Club save(Club club);
}
