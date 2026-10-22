package com.gffh.api.repository.mongo;

import com.gffh.api.model.ChatFilterSettings;
import com.gffh.api.repository.ChatFilterSettingsRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MongoChatFilterSettingsRepository implements ChatFilterSettingsRepository {

    private static final String COLLECTION = "chat_filter_settings";

    private final MongoTemplate mongoTemplate;

    public MongoChatFilterSettingsRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<ChatFilterSettings> find() {
        return Optional.ofNullable(mongoTemplate.findById(ChatFilterSettingsDocument.SINGLETON_ID,
                ChatFilterSettingsDocument.class, COLLECTION)).map(ChatFilterSettingsDocument::toDomain);
    }

    @Override
    public ChatFilterSettings save(ChatFilterSettings settings) {
        ChatFilterSettingsDocument doc = ChatFilterSettingsDocument.from(settings);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public void delete() {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(ChatFilterSettingsDocument.SINGLETON_ID)), COLLECTION);
    }
}
