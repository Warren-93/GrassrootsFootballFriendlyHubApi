package com.gffh.api.domain;

import java.time.Instant;

/** A message on a confirmed fixture (SCR-FX-05), for coordinating logistics between the two teams. */
public record Message(
        String id,
        String fixtureId,
        String senderTeamId,
        String senderUserId,
        String body,
        Instant createdAt) {
}
