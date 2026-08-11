package com.gffh.api.repository;

import com.gffh.api.domain.AvailabilitySlot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AvailabilityRepository {

    Optional<AvailabilitySlot> findById(String id);

    /** Bookable slots only - reserved and booked slots must not be re-proposed. */
    List<AvailabilitySlot> findBookableForTeamBetween(String teamId, LocalDate from, LocalDate to);

    /**
     * Batched equivalent for candidate sets. Issuing one query per candidate
     * turns a search into hundreds of round trips.
     */
    List<AvailabilitySlot> findBookableForTeamsBetween(List<String> teamIds,
                                                       LocalDate from, LocalDate to);

    List<AvailabilitySlot> findOverlapping(String teamId, LocalDate date);

    AvailabilitySlot save(AvailabilitySlot slot);

    void delete(String id);
}
