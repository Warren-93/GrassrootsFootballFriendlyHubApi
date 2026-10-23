package com.gffh.api.repository.mongo;

import com.gffh.api.model.Conversation;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("conversations")
public class ConversationDocument {

    @Id
    public String id;
    public String teamAId;
    public String teamBId;
    public Instant createdAt;
    public Instant lastMessageAt;
    public String lastMessageBody;
    public String lastMessageSenderTeamId;
    public String relatedFixtureId;

    public static ConversationDocument from(Conversation c) {
        ConversationDocument d = new ConversationDocument();
        d.id = c.getId();
        d.teamAId = c.getTeamAId();
        d.teamBId = c.getTeamBId();
        d.createdAt = c.getCreatedAt();
        d.lastMessageAt = c.getLastMessageAt();
        d.lastMessageBody = c.getLastMessageBody();
        d.lastMessageSenderTeamId = c.getLastMessageSenderTeamId();
        d.relatedFixtureId = c.getRelatedFixtureId();
        return d;
    }

    public Conversation toDomain() {
        return new Conversation(id, teamAId, teamBId, createdAt, lastMessageAt, lastMessageBody,
                lastMessageSenderTeamId, relatedFixtureId);
    }
}
