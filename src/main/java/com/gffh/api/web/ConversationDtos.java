package com.gffh.api.web;

import com.gffh.api.domain.Conversation;
import com.gffh.api.domain.Message;
import com.gffh.api.domain.Team;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Wire types for team-to-team messaging. */
public final class ConversationDtos {

    private ConversationDtos() {}

    public record StartConversationRequest(@NotBlank String teamId, @NotBlank String otherTeamId) {}

    public record SendMessageRequest(@NotBlank String body) {}

    /** One row in a team's inbox - the other side plus a preview of where things left off. */
    public record ConversationView(
            String id,
            MatchDtos.TeamSummary otherTeam,
            String lastMessageBody,
            String lastMessageSenderTeamId,
            Instant lastMessageAt,
            Instant createdAt) {

        public static ConversationView of(Conversation c, Team other) {
            return new ConversationView(c.id(), MatchDtos.TeamSummary.from(other), c.lastMessageBody(),
                    c.lastMessageSenderTeamId(), c.lastMessageAt(), c.createdAt());
        }
    }

    public record MessageView(String id, String conversationId, String senderTeamId, String senderUserId,
                               String body, Instant createdAt) {

        public static MessageView from(Message m) {
            return new MessageView(m.id(), m.conversationId(), m.senderTeamId(), m.senderUserId(), m.body(), m.createdAt());
        }
    }
}
