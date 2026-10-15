package com.gffh.api.repository.mongo;

import com.gffh.api.domain.PushToken;
import com.gffh.api.repository.PushTokenRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MongoPushTokenRepository implements PushTokenRepository {

    private static final String COLLECTION = "pushTokens";

    private final MongoTemplate mongoTemplate;

    public MongoPushTokenRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public PushToken save(PushToken token) {
        PushTokenDocument doc = PushTokenDocument.from(token);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public List<PushToken> findByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        return mongoTemplate.find(query, PushTokenDocument.class, COLLECTION).stream()
                .map(PushTokenDocument::toDomain).toList();
    }

    @Override
    public void deleteForUser(String userId, String token) {
        Query query = new Query(Criteria.where("_id").is(token).and("userId").is(userId));
        mongoTemplate.remove(query, PushTokenDocument.class, COLLECTION);
    }

    @Override
    public void delete(String token) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(token)), PushTokenDocument.class, COLLECTION);
    }

    @Override
    public void deleteByUserId(String userId) {
        mongoTemplate.remove(new Query(Criteria.where("userId").is(userId)), PushTokenDocument.class, COLLECTION);
    }
}
