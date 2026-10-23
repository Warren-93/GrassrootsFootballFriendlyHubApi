package com.gffh.api.repository;

import com.gffh.api.model.AvailabilitySlot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface AvailabilityRepository extends MongoRepository<AvailabilitySlot, String> {

    /** Bookable slots only - reserved and booked slots must not be re-proposed. */
    @Query("{ 'teamId': ?0, 'date': { $gte: ?1, $lte: ?2 }, 'status': 'AVAILABLE' }")
    List<AvailabilitySlot> findBookableForTeamBetween(String teamId, LocalDate from, LocalDate to);

    /**
     * Batched equivalent for candidate sets. Issuing one query per candidate
     * turns a search into hundreds of round trips.
     */
    @Query("{ 'teamId': { $in: ?0 }, 'date': { $gte: ?1, $lte: ?2 }, 'status': 'AVAILABLE' }")
    List<AvailabilitySlot> findBookableForTeamsBetween(List<String> teamIds, LocalDate from, LocalDate to);

    /** Every slot this team has on this date, whatever its status. */
    List<AvailabilitySlot> findByTeamIdAndDate(String teamId, LocalDate date);
}
