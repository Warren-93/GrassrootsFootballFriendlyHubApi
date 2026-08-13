package com.gffh.api.repository.mongo;

import com.gffh.api.domain.JoinCode;
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
        JoinCodeDocument doc = mongoTemplate.findOne(query, JoinCodeDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(JoinCodeDocument::toDomain);
    }

    @Override
    public Optional<JoinCode> findByCode(String code) {
        Query query = new Query(Criteria.where("code").is(code));
        JoinCodeDocument doc = mongoTemplate.findOne(query, JoinCodeDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(JoinCodeDocument::toDomain);
    }

    @Override
    public JoinCode save(JoinCode joinCode) {
        JoinCode toSave = joinCode.id() != null ? joinCode : withGeneratedId(joinCode);
        JoinCodeDocument doc = JoinCodeDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public void deleteByTeamId(String teamId) {
        mongoTemplate.remove(new Query(Criteria.where("teamId").is(teamId)), JoinCodeDocument.class, COLLECTION);
    }

    private JoinCode withGeneratedId(JoinCode j) {
        return new JoinCode(UUID.randomUUID().toString(), j.teamId(), j.code(), j.createdByUserId(),
                j.createdAt() != null ? j.createdAt() : Instant.now());
    }
}
