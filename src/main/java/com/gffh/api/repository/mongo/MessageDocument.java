package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Message;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("messages")
public class MessageDocument {

    @Id
    public String id;
    public String conversationId;
    public String senderTeamId;
    public String senderUserId;
    public String body;
    public Instant createdAt;

    public static MessageDocument from(Message m) {
        MessageDocument d = new MessageDocument();
        d.id = m.id();
        d.conversationId = m.conversationId();
        d.senderTeamId = m.senderTeamId();
        d.senderUserId = m.senderUserId();
        d.body = m.body();
        d.createdAt = m.createdAt();
        return d;
    }

    public Message toDomain() {
        return new Message(id, conversationId, senderTeamId, senderUserId, body, createdAt);
    }
}
