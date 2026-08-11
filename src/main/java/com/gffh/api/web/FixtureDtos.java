package com.gffh.api.web;

import com.gffh.api.domain.Fixture;
import com.gffh.api.domain.Team;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The fuller team projection {@link MatchDtos.TeamSummary}'s javadoc refers to
 * as existing "only on the confirmed-fixture endpoint": manager name, phone
 * and venue are safe to disclose here because a fixture only exists once a
 * request has reached CONFIRMED, the state {@link
 * com.gffh.api.request.RequestStateMachine#disclosesContactDetails} allows it.
 */
public final class FixtureDtos {

    private FixtureDtos() {}

    public record FixtureTeamView(String id, String name, String clubName, String managerName,
                                   String contactPhone, String venueId) {
        public static FixtureTeamView from(Team t) {
            return new FixtureTeamView(t.id(), t.name(), t.clubName(), t.managerName(),
                    t.contactPhone(), t.defaultVenueId());
        }
    }

    public record FixtureView(
            String id, String friendlyRequestId, FixtureTeamView homeTeam, FixtureTeamView awayTeam,
            LocalDate date, LocalTime startTime, LocalTime endTime, String venueId,
            String status, String costShare, String refereeArrangement) {

        public static FixtureView from(Fixture f, Team home, Team away) {
            return new FixtureView(f.id(), f.friendlyRequestId(),
                    FixtureTeamView.from(home), FixtureTeamView.from(away),
                    f.date(), f.startTime(), f.endTime(), f.venueId(),
                    f.status().name(), f.costShare().name(), f.refereeArrangement().name());
        }
    }
}
