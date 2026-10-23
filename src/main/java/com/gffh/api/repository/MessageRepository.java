package com.gffh.api.repository;

import com.gffh.api.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    List<Message> findByConversationIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(String conversationId, Instant since);

    /** A conversation's messages created at or after {@code since}, oldest first. */
    default List<Message> findByConversationIdSince(String conversationId, Instant since) {
        // Mongo stores milliseconds, so a finer "since" could skip a message
        // saved in that same millisecond.
        return findByConversationIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                conversationId, since.truncatedTo(ChronoUnit.MILLIS));
    }
}
