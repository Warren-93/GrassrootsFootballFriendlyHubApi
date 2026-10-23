package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A message within a team-to-team Conversation. {@code body} is empty for a
 * GIF-only message; {@code gif} is null for a plain text one.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private String id;
    private String conversationId;
    private String senderTeamId;
    private String senderUserId;
    private String body;
    private Instant createdAt;
    private MessageGif gif;
}
