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
        return Optional.ofNullable(mongoTemplate.findById(ChatFilterSettings.SINGLETON_ID,
                ChatFilterSettings.class, COLLECTION));
    }

    @Override
    public ChatFilterSettings save(ChatFilterSettings settings) {
        ChatFilterSettings doc = settings;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    @Override
    public void delete() {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(ChatFilterSettings.SINGLETON_ID)), COLLECTION);
    }
}
