package com.gffh.api.repository;

import com.gffh.api.domain.Conversation;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository {

    Optional<Conversation> findById(String id);

    Optional<Conversation> findBetweenTeams(String teamAId, String teamBId);

    /** Every conversation this team is party to, most recently active first - the inbox. */
    List<Conversation> findByTeamId(String teamId);

    Conversation save(Conversation conversation);
}
