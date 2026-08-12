package com.gffh.api.service;

import com.gffh.api.domain.*;
import com.gffh.api.repository.AvailabilityRepository;
import com.gffh.api.repository.BlockRepository;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.request.RequestStateMachine;
import com.gffh.api.request.RequestStateMachine.Actor;
import com.gffh.api.web.FriendlyRequestDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Drives friendly requests through {@link RequestStateMachine}. This class
 * owns the side effects the state machine itself is deliberately silent on:
 * reserving and releasing availability slots, and creating the {@link Fixture}
 * the moment a request is accepted.
 */
@Service
public class FriendlyRequestService {

    private final FriendlyRequestRepository requests;
    private final TeamRepository teams;
    private final AvailabilityRepository availability;
    private final BlockRepository blocks;
    private final FixtureRepository fixtures;
    private final MembershipService memberships;
    private final UserRepository users;

    public FriendlyRequestService(FriendlyRequestRepository requests, TeamRepository teams,
                                  AvailabilityRepository availability, BlockRepository blocks,
                                  FixtureRepository fixtures, MembershipService memberships,
                                  UserRepository users) {
        this.requests = requests;
        this.teams = teams;
        this.availability = availability;
        this.blocks = blocks;
        this.fixtures = fixtures;
        this.memberships = memberships;
        this.users = users;
    }

    public FriendlyRequest send(String userId, FriendlyRequestDtos.SendRequest req) {
        memberships.requireCanManageTeam(userId, req.senderTeamId());

        Team sender = teams.findById(req.senderTeamId()).orElseThrow(BusinessRuleException::blocked);
        Team recipient = teams.findById(req.recipientTeamId()).orElseThrow(BusinessRuleException::blocked);

        // Team verification is a trust badge, not a gate (spec SCR-PR-07) - the
        // real gate is the acting user's own email verification.
        User senderUser = users.findById(userId).orElseThrow(BusinessRuleException::blocked);
        if (!senderUser.emailVerified()) throw BusinessRuleException.emailNotVerified();

        int completeness = sender.completenessPercent(sender.defaultVenueId() != null);
        if (completeness < 80) throw BusinessRuleException.profileIncomplete(completeness);

        if (blocks.blockedTeamIdsEitherDirection(req.senderTeamId()).contains(req.recipientTeamId())) {
            throw BusinessRuleException.blocked();
        }
        if (requests.existsOpenBetween(req.senderTeamId(), req.recipientTeamId())) {
            throw BusinessRuleException.duplicateRequest();
        }
        if (!req.homeTeamId().equals(req.senderTeamId()) && !req.homeTeamId().equals(req.recipientTeamId())) {
            throw new BusinessRuleException("INVALID_HOME_TEAM", HttpStatus.BAD_REQUEST,
                    "The home team must be one of the two teams in the request.");
        }

        AvailabilitySlot senderSlot = bookableSlot(req.senderSlotId(), req.senderTeamId());
        AvailabilitySlot recipientSlot = bookableSlot(req.recipientSlotId(), req.recipientTeamId());
        if (!senderSlot.date().equals(recipientSlot.date())
                || senderSlot.overlapMinutesWith(recipientSlot) <= 0) {
            throw BusinessRuleException.notAvailable();
        }

        RequestStateMachine.requirePermitted(RequestStatus.DRAFT, RequestStatus.SENT, Actor.SENDER);

        Instant now = Instant.now();
        FriendlyRequest saved = requests.save(new FriendlyRequest(
                null, req.senderTeamId(), req.recipientTeamId(), req.senderSlotId(), req.recipientSlotId(),
                RequestStatus.SENT, req.date(), req.startTime(), req.endTime(), req.venueId(),
                req.homeTeamId(), req.costShare(), req.refereeArrangement(), req.message(),
                userId, now, now, null));

        reserve(senderSlot);
        reserve(recipientSlot);
        return saved;
    }

    public FriendlyRequest act(String userId, String requestId, String action, String reason) {
        FriendlyRequest fr = requireVisible(userId, requestId);
        Actor actor = actorFor(userId, fr);

        RequestStateMachine.Transition transition = RequestStateMachine.transitionsFrom(fr.status()).stream()
                .filter(t -> t.action().equals(action))
                .findFirst()
                .orElseThrow(() -> new RequestStateMachine.IllegalRequestTransition(
                        "REQUEST_TRANSITION_NOT_ALLOWED",
                        "'" + action + "' is not a valid action from " + fr.status().name().toLowerCase() + "."));

        RequestStateMachine.requirePermitted(fr.status(), transition.to(), actor);

        FriendlyRequest updated = withStatus(fr, transition.to(), reason);
        updated = requests.save(updated);

        if (transition.to() == RequestStatus.ACCEPTED) {
            updated = confirmAndCreateFixture(updated);
        } else if (transition.to() == RequestStatus.DECLINED || transition.to() == RequestStatus.CANCELLED) {
            releaseSlots(updated);
            cancelFixtureIfAny(updated);
        }

        return updated;
    }

    public List<FriendlyRequest> list(String userId, String teamId) {
        memberships.requireCanManageTeam(userId, teamId);
        return requests.findByTeamId(teamId);
    }

    public FriendlyRequest get(String userId, String requestId) {
        return requireVisible(userId, requestId);
    }

    // ---- Helpers --------------------------------------------------------

    private FriendlyRequest requireVisible(String userId, String requestId) {
        FriendlyRequest fr = requests.findById(requestId)
                .orElseThrow(() -> new BusinessRuleException("FRIENDLY_REQUEST_NOT_FOUND",
                        HttpStatus.NOT_FOUND, "That friendly request could not be found."));
        if (!canManage(userId, fr.senderTeamId()) && !canManage(userId, fr.recipientTeamId())) {
            throw new BusinessRuleException("FRIENDLY_REQUEST_NOT_FOUND",
                    HttpStatus.NOT_FOUND, "That friendly request could not be found.");
        }
        return fr;
    }

    private Actor actorFor(String userId, FriendlyRequest fr) {
        if (canManage(userId, fr.senderTeamId())) return Actor.SENDER;
        return Actor.RECIPIENT;
    }

    public boolean canManage(String userId, String teamId) {
        Role role = memberships.roleFor(userId, teamId);
        return role != null && role.canManageTeam();
    }

    private AvailabilitySlot bookableSlot(String slotId, String teamId) {
        return availability.findById(slotId)
                .filter(s -> s.teamId().equals(teamId) && s.isBookable())
                .orElseThrow(BusinessRuleException::notAvailable);
    }

    private void reserve(AvailabilitySlot slot) {
        availability.save(withStatus(slot, AvailabilityStatus.RESERVED));
    }

    private void releaseSlots(FriendlyRequest fr) {
        releaseSlot(fr.senderSlotId());
        releaseSlot(fr.recipientSlotId());
    }

    private void releaseSlot(String slotId) {
        availability.findById(slotId).ifPresent(slot -> {
            if (slot.status() != AvailabilityStatus.WITHDRAWN) {
                availability.save(withStatus(slot, AvailabilityStatus.AVAILABLE));
            }
        });
    }

    private AvailabilitySlot withStatus(AvailabilitySlot slot, AvailabilityStatus status) {
        return new AvailabilitySlot(slot.id(), slot.teamId(), slot.date(), slot.startTime(), slot.endTime(),
                slot.homeAwayPreference(), slot.venueId(), slot.format(), slot.notes(), status);
    }

    private FriendlyRequest withStatus(FriendlyRequest fr, RequestStatus status) {
        return withStatus(fr, status, fr.actionReason());
    }

    private FriendlyRequest withStatus(FriendlyRequest fr, RequestStatus status, String reason) {
        return new FriendlyRequest(fr.id(), fr.senderTeamId(), fr.recipientTeamId(),
                fr.senderSlotId(), fr.recipientSlotId(), status, fr.date(), fr.startTime(), fr.endTime(),
                fr.venueId(), fr.homeTeamId(), fr.costShare(), fr.refereeArrangement(), fr.message(),
                fr.createdByUserId(), fr.createdAt(), Instant.now(), reason);
    }

    /**
     * ACCEPTED is transient (see {@link RequestStateMachine}): the moment it is
     * reached, the system confirms it and books the slots in the same step.
     */
    private FriendlyRequest confirmAndCreateFixture(FriendlyRequest fr) {
        RequestStateMachine.requirePermitted(RequestStatus.ACCEPTED, RequestStatus.CONFIRMED, Actor.SYSTEM);

        availability.findById(fr.senderSlotId()).ifPresent(s -> availability.save(withStatus(s, AvailabilityStatus.BOOKED)));
        availability.findById(fr.recipientSlotId()).ifPresent(s -> availability.save(withStatus(s, AvailabilityStatus.BOOKED)));

        fixtures.save(new Fixture(null, fr.id(), fr.homeTeamId(), fr.awayTeamId(), fr.date(),
                fr.startTime(), fr.endTime(), fr.venueId(), FixtureStatus.CONFIRMED,
                fr.costShare(), fr.refereeArrangement(), Instant.now()));

        return requests.save(withStatus(fr, RequestStatus.CONFIRMED));
    }

    private void cancelFixtureIfAny(FriendlyRequest fr) {
        fixtures.findByTeamId(fr.homeTeamId()).stream()
                .filter(f -> f.friendlyRequestId().equals(fr.id()))
                .findFirst()
                .ifPresent(f -> fixtures.save(new Fixture(f.id(), f.friendlyRequestId(), f.homeTeamId(),
                        f.awayTeamId(), f.date(), f.startTime(), f.endTime(), f.venueId(),
                        FixtureStatus.CANCELLED, f.costShare(), f.refereeArrangement(), f.createdAt())));
    }

    public Optional<Team> team(String teamId) {
        return teams.findById(teamId);
    }
}
