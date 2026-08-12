package com.gffh.api.repository;

import com.gffh.api.domain.Club;

import java.util.List;
import java.util.Optional;

public interface ClubRepository {

    Optional<Club> findById(String id);

    Club save(Club club);

    /** Case-insensitive name search, for ADM-05's club search. */
    List<Club> searchByName(String query, int limit);
}
