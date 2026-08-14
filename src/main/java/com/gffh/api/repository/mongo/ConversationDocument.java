package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Conversation;
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

    public static ConversationDocument from(Conversation c) {
        ConversationDocument d = new ConversationDocument();
        d.id = c.id();
        d.teamAId = c.teamAId();
        d.teamBId = c.teamBId();
        d.createdAt = c.createdAt();
        d.lastMessageAt = c.lastMessageAt();
        d.lastMessageBody = c.lastMessageBody();
        d.lastMessageSenderTeamId = c.lastMessageSenderTeamId();
        return d;
    }

    public Conversation toDomain() {
        return new Conversation(id, teamAId, teamBId, createdAt, lastMessageAt, lastMessageBody, lastMessageSenderTeamId);
    }
}
