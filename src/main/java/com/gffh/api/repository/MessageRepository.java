package com.gffh.api.repository;

import com.gffh.api.domain.Message;

import java.util.List;

public interface MessageRepository {

    Message save(Message message);

    List<Message> findByConversationId(String conversationId);
}
