package com.gffh.api.repository;

import com.gffh.api.model.Fixture;

import com.gffh.api.model.enums.FixtureStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FixtureRepository {

    Optional<Fixture> findById(String id);

    List<Fixture> findByTeamId(String teamId);

    Fixture save(Fixture fixture);

    /**
     * The admin fixture list: newest date first. Every filter is optional -
     * {@code teamIds} null means any team (an empty set matches nothing),
     * {@code from}/{@code to} are inclusive, {@code status} null means any.
     */
    List<Fixture> search(Set<String> teamIds, LocalDate from, LocalDate to, FixtureStatus status, int limit);

    /** Whether a not-yet-completed CONFIRMED fixture is booked at this venue - blocks deleting it out from under it. */
    boolean existsUpcomingConfirmedAtVenue(String venueId);

    /** Whether this team (home or away) has a not-yet-completed CONFIRMED fixture - blocks archiving it out from under it. */
    boolean existsUpcomingConfirmedForTeam(String teamId);
}
