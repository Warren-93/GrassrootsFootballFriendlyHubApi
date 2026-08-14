package com.gffh.api.domain;

import java.time.Instant;

/** A message within a team-to-team Conversation. */
public record Message(
        String id,
        String conversationId,
        String senderTeamId,
        String senderUserId,
        String body,
        Instant createdAt) {
}
