package com.gffh.api.domain;

import java.time.Instant;

/**
 * A single ongoing chat between two teams, independent of any particular
 * request or fixture - a team can be messaged as soon as they've published
 * availability, well before either side has proposed anything. One
 * conversation per team pair; {@code teamAId}/{@code teamBId} carry no home/away
 * meaning, just "the two participants."
 *
 * <p>{@code relatedFixtureId} is a "what's this thread currently about"
 * pointer, not a scoping constraint - the same conversation carries on
 * regardless of how many fixtures/requests connect the pair. It's
 * (re)stamped whenever the thread is opened from a specific fixture's
 * "Message" entry point, so the most recently relevant fixture is what
 * shows in the thread's context banner.
 */
public record Conversation(
        String id,
        String teamAId,
        String teamBId,
        Instant createdAt,
        Instant lastMessageAt,
        String lastMessageBody,
        String lastMessageSenderTeamId,
        String relatedFixtureId) {

    public boolean involves(String teamId) {
        return teamAId.equals(teamId) || teamBId.equals(teamId);
    }

    public String otherTeam(String teamId) {
        return teamAId.equals(teamId) ? teamBId : teamAId;
    }
}
