package com.gffh.api.request;

import com.gffh.api.domain.RequestStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The friendly request state machine, per Technical Specification section 14.
 *
 * <p>This class is the single authority on what may happen to a request. The
 * API rejects any transition it disallows, and the client derives its available
 * actions from the same rules exposed over the wire, so the interface can never
 * offer a button the server would refuse.
 *
 * <p>Transitions are constrained by <em>who</em> is acting as well as by state.
 * A sender cannot accept their own proposal, and a recipient cannot withdraw
 * one - the majority of malformed requests in a negotiation flow are actor
 * errors rather than state errors.
 */
public final class RequestStateMachine {

    /** Who is permitted to perform a transition. */
    public enum Actor {
        /** The team that created the request. */
        SENDER,
        /** The team that received it. */
        RECIPIENT,
        /** Either party. */
        EITHER,
        /** The platform, for automatic transitions such as completion. */
        SYSTEM
    }

    /** A permitted transition and the party entitled to make it. */
    public record Transition(RequestStatus to, Actor actor, String action) {}

    private static final Map<RequestStatus, List<Transition>> TRANSITIONS =
            new EnumMap<>(RequestStatus.class);

    static {
        TRANSITIONS.put(RequestStatus.DRAFT, List.of(
                new Transition(RequestStatus.SENT, Actor.SENDER, "send"),
                new Transition(RequestStatus.CANCELLED, Actor.SENDER, "discard")
        ));

        TRANSITIONS.put(RequestStatus.SENT, List.of(
                new Transition(RequestStatus.ACCEPTED, Actor.RECIPIENT, "accept"),
                new Transition(RequestStatus.DECLINED, Actor.RECIPIENT, "decline"),
                new Transition(RequestStatus.CHANGES_REQUESTED, Actor.RECIPIENT, "suggestChanges"),
                new Transition(RequestStatus.CANCELLED, Actor.SENDER, "withdraw")
        ));

        // The recipient has countered; the ball is with the sender.
        TRANSITIONS.put(RequestStatus.CHANGES_REQUESTED, List.of(
                new Transition(RequestStatus.UPDATED, Actor.SENDER, "acceptChanges"),
                new Transition(RequestStatus.SENT, Actor.SENDER, "counter"),
                new Transition(RequestStatus.DECLINED, Actor.SENDER, "decline"),
                new Transition(RequestStatus.CANCELLED, Actor.SENDER, "withdraw")
        ));

        // Amended terms are back with the recipient for a final answer.
        TRANSITIONS.put(RequestStatus.UPDATED, List.of(
                new Transition(RequestStatus.ACCEPTED, Actor.RECIPIENT, "accept"),
                new Transition(RequestStatus.DECLINED, Actor.RECIPIENT, "decline"),
                new Transition(RequestStatus.CHANGES_REQUESTED, Actor.RECIPIENT, "suggestChanges"),
                new Transition(RequestStatus.CANCELLED, Actor.SENDER, "withdraw")
        ));

        // ACCEPTED is transient: the service creates the fixture and confirms.
        TRANSITIONS.put(RequestStatus.ACCEPTED, List.of(
                new Transition(RequestStatus.CONFIRMED, Actor.SYSTEM, "confirm"),
                new Transition(RequestStatus.CANCELLED, Actor.EITHER, "cancel")
        ));

        TRANSITIONS.put(RequestStatus.CONFIRMED, List.of(
                new Transition(RequestStatus.CANCELLED, Actor.EITHER, "cancel"),
                new Transition(RequestStatus.COMPLETED, Actor.SYSTEM, "complete")
        ));

        TRANSITIONS.put(RequestStatus.DECLINED, List.of());
        TRANSITIONS.put(RequestStatus.CANCELLED, List.of());
        TRANSITIONS.put(RequestStatus.COMPLETED, List.of());
    }

    /** States from which contact details may be disclosed. */
    private static final Set<RequestStatus> DISCLOSING_STATES =
            EnumSet.of(RequestStatus.CONFIRMED, RequestStatus.COMPLETED);

    private RequestStateMachine() {}

    public static List<Transition> transitionsFrom(RequestStatus status) {
        return TRANSITIONS.getOrDefault(status, List.of());
    }

    /** Transitions available to one party, used to drive the UI action set. */
    public static List<Transition> transitionsFor(RequestStatus status, boolean isSender) {
        Actor party = isSender ? Actor.SENDER : Actor.RECIPIENT;
        return transitionsFrom(status).stream()
                .filter(t -> t.actor() == party || t.actor() == Actor.EITHER)
                .toList();
    }

    public static boolean isPermitted(RequestStatus from, RequestStatus to, Actor actor) {
        return transitionsFrom(from).stream()
                .anyMatch(t -> t.to() == to && actorSatisfies(t.actor(), actor));
    }

    private static boolean actorSatisfies(Actor required, Actor actual) {
        if (actual == Actor.SYSTEM) return required == Actor.SYSTEM;
        if (required == Actor.EITHER) return actual == Actor.SENDER || actual == Actor.RECIPIENT;
        return required == actual;
    }

    /**
     * Validates a transition, throwing with a structured code the API can
     * surface directly. The two failure modes are distinguished deliberately:
     * a wrong-state error usually means the other party acted first and the
     * client should refresh, whereas a wrong-actor error is a genuine bug or an
     * attempted permission bypass.
     */
    public static void requirePermitted(RequestStatus from, RequestStatus to, Actor actor) {
        boolean stateAllows = transitionsFrom(from).stream().anyMatch(t -> t.to() == to);
        if (!stateAllows) {
            throw new IllegalRequestTransition(
                    "REQUEST_TRANSITION_NOT_ALLOWED",
                    "This request can no longer be " + to.name().toLowerCase()
                            + " because it is now " + from.name().toLowerCase() + ".");
        }
        if (!isPermitted(from, to, actor)) {
            throw new IllegalRequestTransition(
                    "REQUEST_ACTION_NOT_PERMITTED",
                    "Your team is not permitted to perform this action on this request.");
        }
    }

    /**
     * Whether contact details and the exact venue address may be disclosed at
     * this state. The product's central privacy rule, enforced here so that
     * every serialisation path consults one definition.
     */
    public static boolean disclosesContactDetails(RequestStatus status) {
        return DISCLOSING_STATES.contains(status);
    }

    /** Thrown for a rejected transition; mapped to a 409 by the web layer. */
    public static class IllegalRequestTransition extends RuntimeException {
        private final String code;

        public IllegalRequestTransition(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() { return code; }
    }
}
