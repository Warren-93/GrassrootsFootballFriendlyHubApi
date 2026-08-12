package com.gffh.api.web;

import com.gffh.api.domain.Message;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Wire types for SCR-FX-05 Fixture messages. */
public final class MessageDtos {

    private MessageDtos() {}

    public record SendMessageRequest(@NotBlank String body) {}

    public record MessageView(String id, String fixtureId, String senderTeamId, String senderUserId, String body,
                               Instant createdAt) {

        public static MessageView from(Message m) {
            return new MessageView(m.id(), m.fixtureId(), m.senderTeamId(), m.senderUserId(), m.body(), m.createdAt());
        }
    }
}
