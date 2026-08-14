package com.gffh.api.service;

import com.gffh.api.domain.AvailabilitySlot;
import com.gffh.api.domain.Team;
import com.gffh.api.matching.MatchingEngine;
import com.gffh.api.matching.MatchResult;
import com.gffh.api.matching.MatchingWeights;
import com.gffh.api.repository.AvailabilityRepository;
import com.gffh.api.repository.BlockRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.web.MatchDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles candidates from persistence, delegates scoring to the engine, and
 * projects the result for the wire.
 *
 * <p>The division of labour is deliberate: this class knows about Mongo, Spring
 * and authorisation; {@link MatchingEngine} knows only football. Keeping the
 * scoring rules free of infrastructure is what makes them testable as rules and
 * retunable without touching query code.
 */
@Service
public class MatchingService {

    /** Candidate ceiling before scoring, to bound query and CPU cost. */
    private static final int CANDIDATE_LIMIT = 500;

    /**
     * Stand-in for "no distance limit" when ignoreTravelRadius is set - a
     * $centerSphere query needs a numeric radius, and any value at or beyond
     * half the Earth's circumference (~12,450 miles, the farthest two points
     * can be from each other) covers the whole globe.
     */
    private static final int UNLIMITED_RADIUS_MILES = 12500;

    private final TeamRepository teams;
    private final AvailabilityRepository availability;
    private final BlockRepository blocks;
    private final MembershipService memberships;
    private final MatchingWeightsProvider weightsProvider;

    public MatchingService(TeamRepository teams,
                           AvailabilityRepository availability,
                           BlockRepository blocks,
                           MembershipService memberships,
                           MatchingWeightsProvider weightsProvider) {
        this.teams = teams;
        this.availability = availability;
        this.blocks = blocks;
        this.memberships = memberships;
        this.weightsProvider = weightsProvider;
    }

    public MatchDtos.SearchResponse search(String userId, MatchDtos.SearchRequest request) {
        Team ourTeam = teams.findById(request.teamId())
                .orElseThrow(BusinessRuleException::blocked);

        // The caller must actually manage this team. Without this check any
        // authenticated user could search on behalf of anyone.
        memberships.requireCanManageTeam(userId, ourTeam.id());

        return score(ourTeam, request, weightsProvider.current());
    }

    /**
     * Scores with the given weights instead of the published ones, and skips
     * the per-user membership check: this is called only from ADM-08's
     * preview, which is already authorized at the controller level by
     * requiring PLATFORM_ADMIN, not by any particular user managing the team
     * being previewed against. Never touches the live configuration.
     */
    public MatchDtos.SearchResponse previewForAdmin(MatchDtos.SearchRequest request, MatchingWeights weights) {
        Team ourTeam = teams.findById(request.teamId())
                .orElseThrow(BusinessRuleException::blocked);
        return score(ourTeam, request, weights);
    }

    private MatchDtos.SearchResponse score(Team ourTeam, MatchDtos.SearchRequest request, MatchingWeights weights) {
        LocalDate from = request.fromDate() != null ? request.fromDate() : LocalDate.now();
        LocalDate to = request.toDate() != null ? request.toDate() : from.plusWeeks(8);

        List<AvailabilitySlot> ourSlots =
                availability.findBookableForTeamBetween(ourTeam.id(), from, to);
        if (ourSlots.isEmpty()) {
            // Quick match is meaningless without published availability. The
            // client blocks this, but a direct API caller must be told why.
            return empty(weights);
        }

        // Narrow in the database before scoring in memory. The 2dsphere index
        // and the ageGroup/format/gender compound index carry this query; see
        // section 10 of the Technical Specification.
        boolean ignoreTravelRadius = Boolean.TRUE.equals(request.ignoreTravelRadius());
        int radius = ignoreTravelRadius
                ? UNLIMITED_RADIUS_MILES
                : request.maxDistanceMiles() != null
                        ? Math.min(request.maxDistanceMiles(), ourTeam.travelRadiusMiles())
                        : ourTeam.travelRadiusMiles();

        List<Team> candidates = teams.findCandidates(
                ourTeam.location(),
                radius,
                ourTeam.ageGroup(),
                ourTeam.gender(),
                request.formats(),
                request.abilityLevels(),
                Boolean.TRUE.equals(request.verifiedOnly()),
                ourTeam.clubId(),
                CANDIDATE_LIMIT);

        // Blocking is bidirectional and must be invisible: a blocked team is
        // simply absent, never flagged.
        Set<String> excluded = blocks.blockedTeamIdsEitherDirection(ourTeam.id());
        candidates = candidates.stream()
                .filter(t -> !excluded.contains(t.id()))
                .toList();
        if (candidates.isEmpty()) {
            return empty(weights);
        }

        Map<String, List<AvailabilitySlot>> slotsByTeam =
                availability.findBookableForTeamsBetween(
                                candidates.stream().map(Team::id).toList(), from, to)
                        .stream()
                        .collect(Collectors.groupingBy(AvailabilitySlot::teamId));

        List<MatchingEngine.Candidate> scored = candidates.stream()
                .map(t -> new MatchingEngine.Candidate(
                        t, slotsByTeam.getOrDefault(t.id(), List.of())))
                .filter(c -> !c.slots().isEmpty())
                .toList();

        List<MatchResult> ranked = new MatchingEngine(weights).rank(ourTeam, ourSlots, scored, ignoreTravelRadius);

        Map<String, Team> byId = candidates.stream()
                .collect(Collectors.toMap(Team::id, t -> t));

        List<MatchDtos.MatchSummary> page = ranked.stream()
                .limit(request.limitOrDefault())
                .map(r -> MatchDtos.MatchSummary.from(r, byId.get(r.teamId())))
                .toList();

        return new MatchDtos.SearchResponse(
                UUID.randomUUID().toString(),
                ranked.size(),
                page,
                view(weights),
                null);
    }

    private MatchDtos.SearchResponse empty(MatchingWeights weights) {
        return new MatchDtos.SearchResponse(
                UUID.randomUUID().toString(), 0, List.of(), view(weights), null);
    }

    private MatchDtos.WeightsView view(MatchingWeights w) {
        return new MatchDtos.WeightsView(w.age(), w.availability(), w.format(),
                w.distance(), w.homeAway(), w.ability());
    }
}
