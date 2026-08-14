package com.gffh.api.web;

import com.gffh.api.domain.Team;
import com.gffh.api.matching.FactorResult;
import com.gffh.api.matching.MatchResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.List;

/**
 * Wire types for POST /api/v1/matches/search.
 *
 * <p>{@link TeamSummary} is the only projection of a team used in discovery. It
 * deliberately has no field for the manager's phone number or a venue address:
 * the privacy rule is enforced by the shape of the type, not by remembering to
 * null a field. The fuller projection carrying contact details exists only on
 * the confirmed-fixture endpoint.
 */
public final class MatchDtos {

    private MatchDtos() {}

    public record SearchRequest(
            String teamId,
            LocalDate fromDate,
            LocalDate toDate,
            List<String> formats,
            List<String> abilityLevels,
            @Min(1) @Max(50) Integer maxDistanceMiles,
            Boolean verifiedOnly,
            Boolean venueRequired,
            /** Search beyond both teams' configured travel radius - the radius is a preference, not a hard wall. */
            Boolean ignoreTravelRadius,
            @Min(1) @Max(100) Integer limit,
            String cursor) {

        public int limitOrDefault() { return limit == null ? 20 : limit; }
    }

    public record SearchResponse(
            String searchId,
            int totalResults,
            List<MatchSummary> results,
            WeightsView weights,
            String nextCursor) {}

    /** One ranked opponent, as rendered on SCR-FF-03. */
    public record MatchSummary(
            TeamSummary team,
            int score,
            String band,
            double milesApart,
            List<String> reasons,
            List<FactorView> factors,
            OverlapView earliestOverlap) {

        public static MatchSummary from(MatchResult result, Team team) {
            MatchResult.SlotOverlap first = result.earliestOverlap();
            return new MatchSummary(
                    TeamSummary.from(team),
                    result.score(),
                    result.band(),
                    result.milesApart(),
                    result.summaryReasons(),
                    result.factors().stream().map(FactorView::from).toList(),
                    first == null ? null : new OverlapView(
                            first.ours().date().toString(),
                            first.ours().overlapStartWith(first.theirs()).toString(),
                            first.overlapMinutes(),
                            first.ours().id(),
                            first.theirs().id()));
        }
    }

    /**
     * Discovery projection of a team. No contact details, no exact location -
     * only a general area and a distance.
     */
    public record TeamSummary(
            String id,
            String name,
            String clubName,
            String badgeUrl,
            String ageGroup,
            String gender,
            String format,
            String abilityLevel,
            String league,
            String description,
            String generalArea,
            boolean verified) {

        public static TeamSummary from(Team team) {
            return new TeamSummary(
                    team.id(),
                    team.name(),
                    team.clubName(),
                    team.badgeUrl(),
                    team.ageGroup().label(),
                    team.gender().label(),
                    team.format().label(),
                    team.abilityLevel().label(),
                    team.league(),
                    team.description(),
                    outwardCode(team.postcode()),
                    team.isVerified());
        }

        /**
         * Only the outward code is published - "G74", not "G74 3AB". Enough to
         * convey the area, not enough to identify a training ground.
         */
        private static String outwardCode(String postcode) {
            if (postcode == null || postcode.isBlank()) return null;
            String trimmed = postcode.trim();
            int space = trimmed.indexOf(' ');
            return space > 0 ? trimmed.substring(0, space) : trimmed;
        }
    }

    /** One factor's contribution, for the explanation screen (SCR-FF-06). */
    public record FactorView(
            String factor,
            String label,
            int weight,
            double ratio,
            double points,
            String ourValue,
            String theirValue,
            String reason) {

        public static FactorView from(FactorResult f) {
            return new FactorView(
                    f.factor().name(),
                    f.factor().label(),
                    f.weight(),
                    Math.round(f.ratio() * 100) / 100.0,
                    Math.round(f.points() * 10) / 10.0,
                    f.ourValue(),
                    f.theirValue(),
                    f.reason());
        }
    }

    /** The overlapping window, used to pre-fill the invitation composer. */
    public record OverlapView(
            String date,
            String startTime,
            int overlapMinutes,
            String ourSlotId,
            String theirSlotId) {}

    /**
     * The weights actually applied. Returned on every search so the client
     * renders the live configuration rather than a hard-coded copy.
     */
    public record WeightsView(
            int age, int availability, int format,
            int distance, int homeAway, int ability) {}
}
