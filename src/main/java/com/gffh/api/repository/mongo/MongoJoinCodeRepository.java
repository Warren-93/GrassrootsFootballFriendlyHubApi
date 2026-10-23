package com.gffh.api.repository.mongo;

import com.gffh.api.model.JoinCode;
import com.gffh.api.repository.JoinCodeRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoJoinCodeRepository implements JoinCodeRepository {

    private static final String COLLECTION = "joinCodes";

    private final MongoTemplate mongoTemplate;

    public MongoJoinCodeRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<JoinCode> findByTeamId(String teamId) {
        Query query = new Query(Criteria.where("teamId").is(teamId));
        JoinCode doc = mongoTemplate.findOne(query, JoinCode.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public Optional<JoinCode> findByCode(String code) {
        Query query = new Query(Criteria.where("code").is(code));
        JoinCode doc = mongoTemplate.findOne(query, JoinCode.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public JoinCode save(JoinCode joinCode) {
        JoinCode toSave = joinCode.getId() != null ? joinCode : withGeneratedId(joinCode);
        JoinCode doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    @Override
    public void deleteByTeamId(String teamId) {
        mongoTemplate.remove(new Query(Criteria.where("teamId").is(teamId)), JoinCode.class, COLLECTION);
    }

    private JoinCode withGeneratedId(JoinCode j) {
        return new JoinCode(UUID.randomUUID().toString(), j.getTeamId(), j.getCode(), j.getCreatedByUserId(),
                j.getCreatedAt() != null ? j.getCreatedAt() : Instant.now());
    }
}
