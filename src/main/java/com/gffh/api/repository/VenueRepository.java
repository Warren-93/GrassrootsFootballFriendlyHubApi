package com.gffh.api.repository;

import com.gffh.api.domain.Venue;

import java.util.List;
import java.util.Optional;

public interface VenueRepository {

    Optional<Venue> findById(String id);

    List<Venue> findByClubId(String clubId);

    Venue save(Venue venue);

    void delete(String id);

    /** Unsets every other venue's default flag for this club, so exactly one default holds (SCR-PR-05). */
    void clearDefaultForClub(String clubId);
}
