package com.gffh.api.repository;

import com.gffh.api.model.Message;

import java.time.Instant;
import java.util.List;

public interface MessageRepository {

    Message save(Message message);

    List<Message> findByConversationId(String conversationId);

    /** A conversation's messages created at or after {@code since}, oldest first. */
    List<Message> findByConversationIdSince(String conversationId, Instant since);
}
