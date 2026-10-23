package com.gffh.api.repository.custom;

import com.gffh.api.model.Fixture;
import com.gffh.api.model.enums.FixtureStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Fixture queries that a derived query method can't express. */
public interface FixtureRepositoryCustom {

    /**
     * The admin fixture list: newest date first. Every filter is optional -
     * {@code teamIds} null means any team (an empty set matches nothing),
     * {@code from}/{@code to} are inclusive, {@code status} null means any.
     */
    List<Fixture> search(Set<String> teamIds, LocalDate from, LocalDate to, FixtureStatus status, int limit);
}
