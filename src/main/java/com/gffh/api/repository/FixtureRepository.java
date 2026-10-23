package com.gffh.api.repository;

import com.gffh.api.model.Fixture;
import com.gffh.api.model.enums.FixtureStatus;
import com.gffh.api.repository.custom.FixtureRepositoryCustom;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface FixtureRepository extends MongoRepository<Fixture, String>, FixtureRepositoryCustom {

    /** Every fixture this team plays in, home or away. */
    @Query("{ $or: [ { 'homeTeamId': ?0 }, { 'awayTeamId': ?0 } ] }")
    List<Fixture> findByTeamId(String teamId);

    boolean existsByVenueIdAndStatusAndDateGreaterThanEqual(String venueId, FixtureStatus status, LocalDate date);

    @Query(value = "{ $or: [ { 'homeTeamId': ?0 }, { 'awayTeamId': ?0 } ], 'status': ?1, 'date': { $gte: ?2 } }",
            exists = true)
    boolean existsForTeamWithStatusOnOrAfter(String teamId, FixtureStatus status, LocalDate date);

    /** Whether a not-yet-completed CONFIRMED fixture is booked at this venue - blocks deleting it out from under it. */
    default boolean existsUpcomingConfirmedAtVenue(String venueId) {
        return existsByVenueIdAndStatusAndDateGreaterThanEqual(venueId, FixtureStatus.CONFIRMED, LocalDate.now());
    }

    /** Whether this team (home or away) has a not-yet-completed CONFIRMED fixture - blocks archiving it out from under it. */
    default boolean existsUpcomingConfirmedForTeam(String teamId) {
        return existsForTeamWithStatusOnOrAfter(teamId, FixtureStatus.CONFIRMED, LocalDate.now());
    }
}
