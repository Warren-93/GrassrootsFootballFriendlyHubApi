package com.gffh.api.repository.mongo;

import com.gffh.api.domain.MatchingWeightsVersion;
import com.gffh.api.repository.MatchingWeightsVersionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoMatchingWeightsVersionRepository implements MatchingWeightsVersionRepository {

    private static final String COLLECTION = "matching_weights_versions";

    private final MongoTemplate mongoTemplate;

    public MongoMatchingWeightsVersionRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<MatchingWeightsVersion> findActive() {
        Query query = new Query(Criteria.where("active").is(true));
        MatchingWeightsVersionDocument doc = mongoTemplate.findOne(query, MatchingWeightsVersionDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(MatchingWeightsVersionDocument::toDomain);
    }

    @Override
    public List<MatchingWeightsVersion> listAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "publishedAt"));
        return mongoTemplate.find(query, MatchingWeightsVersionDocument.class, COLLECTION).stream()
                .map(MatchingWeightsVersionDocument::toDomain).toList();
    }

    @Override
    public MatchingWeightsVersion save(MatchingWeightsVersion version) {
        MatchingWeightsVersion toSave = version.id() != null ? version : withGeneratedId(version);
        mongoTemplate.save(MatchingWeightsVersionDocument.from(toSave), COLLECTION);
        return toSave;
    }

    @Override
    public void deactivateAll() {
        mongoTemplate.updateMulti(new Query(), Update.update("active", false),
                MatchingWeightsVersionDocument.class, COLLECTION);
    }

    private MatchingWeightsVersion withGeneratedId(MatchingWeightsVersion v) {
        return new MatchingWeightsVersion(UUID.randomUUID().toString(), v.weights(), v.versionNote(),
                v.publishedByAdminId(), v.publishedAt() != null ? v.publishedAt() : Instant.now(), v.active());
    }
}
