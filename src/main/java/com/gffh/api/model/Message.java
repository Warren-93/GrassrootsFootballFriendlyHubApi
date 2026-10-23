package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A message within a team-to-team Conversation. {@code body} is empty for a
 * GIF-only message; {@code gif} is null for a plain text one.
 */
@Document("messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    private String id;
    private String conversationId;
    private String senderTeamId;
    private String senderUserId;
    private String body;
    @CreatedDate
    private Instant createdAt;
    private MessageGif gif;
}
