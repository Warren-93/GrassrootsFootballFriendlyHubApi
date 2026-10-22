package com.gffh.api.model;

import java.time.Instant;

/**
 * A message within a team-to-team Conversation. {@code body} is empty for a
 * GIF-only message; {@code gif} is null for a plain text one.
 */
public record Message(
        String id,
        String conversationId,
        String senderTeamId,
        String senderUserId,
        String body,
        Instant createdAt,
        MessageGif gif) {
}
