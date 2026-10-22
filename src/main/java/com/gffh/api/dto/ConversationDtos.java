package com.gffh.api.dto;

import com.gffh.api.model.Conversation;
import com.gffh.api.model.Message;
import com.gffh.api.model.Team;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Wire types for team-to-team messaging. */
public final class ConversationDtos {

    private ConversationDtos() {}

    /** {@code fixtureId} is optional - present when opened from a fixture's "Message" entry point. */
    public record StartConversationRequest(@NotBlank String teamId, @NotBlank String otherTeamId, String fixtureId) {}

    /**
     * Text, a GIF, or both. {@code gifId} is the id from a GIF search
     * ({@code /api/v1/gifs/...}); the API looks it up itself rather than
     * trusting a URL from the client.
     */
    public record SendMessageRequest(@Size(max = MAX_BODY_LENGTH) String body, @Size(max = 64) String gifId) {}

    public static final int MAX_BODY_LENGTH = 2000;

    /** One row in a team's inbox - the other side plus a preview of where things left off. */
    public record ConversationView(
            String id,
            MatchDtos.TeamSummary otherTeam,
            String lastMessageBody,
            String lastMessageSenderTeamId,
            Instant lastMessageAt,
            Instant createdAt,
            String relatedFixtureId) {

        public static ConversationView of(Conversation c, Team other) {
            return new ConversationView(c.id(), MatchDtos.TeamSummary.from(other), c.lastMessageBody(),
                    c.lastMessageSenderTeamId(), c.lastMessageAt(), c.createdAt(), c.relatedFixtureId());
        }
    }

    /** {@code body} is empty for a GIF-only message; {@code gif} is absent for a text-only one. */
    public record MessageView(String id, String conversationId, String senderTeamId, String senderUserId,
                               String body, Instant createdAt, GifDtos.GifView gif) {

        public static MessageView from(Message m) {
            return new MessageView(m.id(), m.conversationId(), m.senderTeamId(), m.senderUserId(), m.body(),
                    m.createdAt(), GifDtos.GifView.from(m.gif()));
        }
    }
}
