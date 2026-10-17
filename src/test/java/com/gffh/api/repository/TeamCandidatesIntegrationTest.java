package com.gffh.api.repository;

import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link TeamRepository#findCandidates} admits exactly the age groups it is
 * given - one at the default tolerance, more when the tolerance is raised -
 * and gender always matches exactly.
 */
class TeamCandidatesIntegrationTest extends AbstractIntegrationTest {

    // Somewhere no other test puts a team, so nearest-first results are ours.
    private static final GeoPoint HERE = new GeoPoint(-6.2603, 53.3498);

    @Test
    @DisplayName("candidates are limited to the given age groups and the same gender")
    void candidatesFollowTheAgeBandTolerance() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        Team u10 = save(run, AgeGroup.U10, Gender.MIXED);
        Team u11 = save(run, AgeGroup.U11, Gender.MIXED);
        Team u12 = save(run, AgeGroup.U12, Gender.MIXED);
        Team u13 = save(run, AgeGroup.U13, Gender.MIXED);
        Team u12Girls = save(run, AgeGroup.U12, Gender.GIRLS);

        assertEquals(Set.of(u12.id()), candidateIds(run, AgeGroup.U12.withinBands(0), Gender.MIXED));
        assertEquals(Set.of(u11.id(), u12.id(), u13.id()),
                candidateIds(run, AgeGroup.U12.withinBands(1), Gender.MIXED));
        assertEquals(Set.of(u10.id(), u11.id(), u12.id(), u13.id()),
                candidateIds(run, AgeGroup.U11.withinBands(2), Gender.MIXED));
        assertEquals(Set.of(u12Girls.id()), candidateIds(run, AgeGroup.U12.withinBands(1), Gender.GIRLS));
    }

    private Set<String> candidateIds(String run, Set<AgeGroup> ageGroups, Gender gender) {
        List<Team> found = teamRepository.findCandidates(HERE, 5, ageGroups, gender, null, null, false,
                "searching-club-" + run, 500);
        return found.stream().filter(t -> t.name().startsWith(run)).map(Team::id)
                .collect(Collectors.toSet());
    }

    private Team save(String run, AgeGroup ageGroup, Gender gender) {
        return teamRepository.save(new Team(null, "club-" + UUID.randomUUID(), run + " " + ageGroup + " " + gender,
                "Club", null, ageGroup, gender, Format.NINE_A_SIDE, AbilityLevel.DEVELOPMENT, null, "D01 F5P2",
                HERE, 10, HomeAwayPreference.EITHER, null, null, null, VerificationStatus.VERIFIED, null, null,
                Instant.now(), Instant.now(), false, false, true, true));
    }
}
