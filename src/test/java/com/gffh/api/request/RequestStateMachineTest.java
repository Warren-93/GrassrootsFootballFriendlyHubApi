package com.gffh.api.request;

import com.gffh.api.domain.RequestStatus;
import com.gffh.api.request.RequestStateMachine.Actor;
import com.gffh.api.request.RequestStateMachine.IllegalRequestTransition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The request state-machine scenarios required by section 22 of the
 * Technical Specification. Ported from the original dependency-free
 * {@code CoreRulesHarness} - same scenarios, now run by {@code mvn test}.
 */
class RequestStateMachineTest {

    @Test
    @DisplayName("recipient may accept or counter a sent request; sender may withdraw it")
    void allowsRecipientToActOnASentRequest() {
        assertTrue(RequestStateMachine.isPermitted(
                RequestStatus.SENT, RequestStatus.ACCEPTED, Actor.RECIPIENT),
                "recipient may accept a sent request");
        assertTrue(RequestStateMachine.isPermitted(
                RequestStatus.SENT, RequestStatus.CHANGES_REQUESTED, Actor.RECIPIENT),
                "recipient may counter a sent request");
        assertTrue(RequestStateMachine.isPermitted(
                RequestStatus.SENT, RequestStatus.CANCELLED, Actor.SENDER),
                "sender may withdraw a sent request");
    }

    @Test
    @DisplayName("a sender cannot accept their own proposal")
    void forbidsSenderAcceptingOwnProposal() {
        assertFalse(RequestStateMachine.isPermitted(
                RequestStatus.SENT, RequestStatus.ACCEPTED, Actor.SENDER),
                "sender may not accept their own proposal");

        IllegalRequestTransition thrown = assertThrows(IllegalRequestTransition.class,
                () -> RequestStateMachine.requirePermitted(
                        RequestStatus.SENT, RequestStatus.ACCEPTED, Actor.SENDER),
                "self-acceptance throws");
        assertEquals("REQUEST_ACTION_NOT_PERMITTED", thrown.code(),
                "self-acceptance is reported as a permission error");
    }

    @Test
    @DisplayName("a declined (terminal) request cannot be transitioned again")
    void rejectsTransitionFromTerminalState() {
        IllegalRequestTransition thrown = assertThrows(IllegalRequestTransition.class,
                () -> RequestStateMachine.requirePermitted(
                        RequestStatus.DECLINED, RequestStatus.ACCEPTED, Actor.RECIPIENT),
                "a declined request cannot be accepted");
        assertEquals("REQUEST_TRANSITION_NOT_ALLOWED", thrown.code(),
                "a stale-state failure is distinguishable from a permission failure");
        assertTrue(RequestStateMachine.transitionsFrom(RequestStatus.COMPLETED).isEmpty(),
                "terminal states expose no transitions");
    }

    @Test
    @DisplayName("available actions differ by actor and are empty while waiting on the other side")
    void drivesActionSetsByActorAndStatus() {
        var recipientActions = RequestStateMachine.transitionsFor(RequestStatus.SENT, false);
        var senderActions = RequestStateMachine.transitionsFor(RequestStatus.SENT, true);

        assertEquals(3, recipientActions.size(), "recipient sees three actions on a sent request");
        assertEquals(1, senderActions.size(), "sender sees only one action on a sent request");
        assertEquals("withdraw", senderActions.get(0).action(), "sender sees only withdraw on a sent request");

        var awaitingSender = RequestStateMachine.transitionsFor(RequestStatus.CHANGES_REQUESTED, true);
        assertEquals(4, awaitingSender.size(), "sender can act once changes are requested");
        assertTrue(RequestStateMachine.transitionsFor(RequestStatus.CHANGES_REQUESTED, false).isEmpty(),
                "recipient waits once changes are requested");
    }

    @Test
    @DisplayName("contact details are withheld until CONFIRMED, then remain available")
    void guardsContactDisclosure() {
        assertFalse(RequestStateMachine.disclosesContactDetails(RequestStatus.SENT),
                "contact details are withheld while sent");
        assertFalse(RequestStateMachine.disclosesContactDetails(RequestStatus.ACCEPTED),
                "contact details are withheld while accepted but unconfirmed");
        assertTrue(RequestStateMachine.disclosesContactDetails(RequestStatus.CONFIRMED),
                "contact details are disclosed once confirmed");
        assertTrue(RequestStateMachine.disclosesContactDetails(RequestStatus.COMPLETED),
                "contact details remain available after completion");
    }
}
