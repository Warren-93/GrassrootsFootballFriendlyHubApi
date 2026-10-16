package com.gffh.api.web;

import com.gffh.api.domain.Fixture;
import com.gffh.api.domain.Team;
import com.gffh.api.domain.Venue;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The fuller team projection {@link MatchDtos.TeamSummary}'s javadoc refers to
 * as existing "only on the confirmed-fixture endpoint": manager name, phone
 * and venue are safe to disclose here because a fixture only exists once a
 * request has reached CONFIRMED, the state {@link
 * com.gffh.api.request.RequestStateMachine#disclosesContactDetails} allows it.
 * The manager name and phone still only go to the two teams' managers - see
 * {@link FixtureTeamView#from}.
 */
public final class FixtureDtos {

    private FixtureDtos() {}

    public record CancelFixtureRequest(String reason) {}

    public record FixtureTeamView(String id, String name, String clubName, String managerName,
                                   String contactPhone, String venueId, String badgeUrl) {
        /**
         * A team that's turned off "share contact details" still discloses
         * everything else about itself once a fixture is confirmed - only the
         * manager name/phone the OTHER side would see are withheld.
         * {@code includeContact} is false when the caller manages neither team
         * (a member who joined with a code): they see the fixture, not the
         * managers' details.
         */
        public static FixtureTeamView from(Team t, boolean includeContact) {
            boolean share = includeContact && t.shareContactDetails();
            return new FixtureTeamView(t.id(), t.name(), t.clubName(), share ? t.managerName() : null,
                    share ? t.contactPhone() : null, t.defaultVenueId(), t.badgeUrl());
        }
    }

    /**
     * {@code venue} is the fixture's own venue, else the home team's default
     * venue at read time (see {@link com.gffh.api.service.FixtureService#venueFor});
     * null when neither exists.
     */
    public record FixtureView(
            String id, String friendlyRequestId, FixtureTeamView homeTeam, FixtureTeamView awayTeam,
            LocalDate date, LocalTime startTime, LocalTime endTime, String venueId,
            String status, String costShare, String refereeArrangement, VenueDtos.VenueSummary venue) {

        public static FixtureView from(Fixture f, Team home, Team away, Venue venue, boolean includeContact) {
            return new FixtureView(f.id(), f.friendlyRequestId(),
                    FixtureTeamView.from(home, includeContact), FixtureTeamView.from(away, includeContact),
                    f.date(), f.startTime(), f.endTime(), f.venueId(),
                    f.status().name(), f.costShare().name(), f.refereeArrangement().name(),
                    VenueDtos.VenueSummary.from(venue));
        }
    }
}
