package com.gffh.api.repository.mongo;

import com.gffh.api.model.Message;
import com.gffh.api.model.MessageGif;
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
    public MessageGif gif;

    public static MessageDocument from(Message m) {
        MessageDocument d = new MessageDocument();
        d.id = m.getId();
        d.conversationId = m.getConversationId();
        d.senderTeamId = m.getSenderTeamId();
        d.senderUserId = m.getSenderUserId();
        d.body = m.getBody();
        d.createdAt = m.getCreatedAt();
        d.gif = m.getGif();
        return d;
    }

    public Message toDomain() {
        return new Message(id, conversationId, senderTeamId, senderUserId, body, createdAt, gif);
    }
}
