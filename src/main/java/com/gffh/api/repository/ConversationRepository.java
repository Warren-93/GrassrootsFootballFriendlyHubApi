package com.gffh.api.repository;

import com.gffh.api.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    /** The conversation between these two teams, whichever of them started it. */
    @Query("{ $or: [ { 'teamAId': ?0, 'teamBId': ?1 }, { 'teamAId': ?1, 'teamBId': ?0 } ] }")
    Optional<Conversation> findBetweenTeams(String teamAId, String teamBId);

    /** Every conversation this team is party to, most recently active first - the inbox. */
    @Query(value = "{ $or: [ { 'teamAId': ?0 }, { 'teamBId': ?0 } ] }", sort = "{ 'lastMessageAt': -1 }")
    List<Conversation> findByTeamId(String teamId);
}
