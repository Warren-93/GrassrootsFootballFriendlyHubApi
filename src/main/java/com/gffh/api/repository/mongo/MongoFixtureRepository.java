package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Fixture;
import com.gffh.api.repository.FixtureRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoFixtureRepository implements FixtureRepository {

    private static final String COLLECTION = "fixtures";

    private final MongoTemplate mongoTemplate;

    public MongoFixtureRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Fixture> findById(String id) {
        FixtureDocument doc = mongoTemplate.findById(id, FixtureDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(FixtureDocument::toDomain);
    }

    @Override
    public List<Fixture> findByTeamId(String teamId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("homeTeamId").is(teamId),
                Criteria.where("awayTeamId").is(teamId)));
        return mongoTemplate.find(query, FixtureDocument.class, COLLECTION).stream()
                .map(FixtureDocument::toDomain).toList();
    }

    @Override
    public Fixture save(Fixture fixture) {
        Fixture toSave = fixture.id() != null ? fixture : withGeneratedId(fixture);
        FixtureDocument doc = FixtureDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    private Fixture withGeneratedId(Fixture f) {
        return new Fixture(UUID.randomUUID().toString(), f.friendlyRequestId(), f.homeTeamId(),
                f.awayTeamId(), f.date(), f.startTime(), f.endTime(), f.venueId(), f.status(),
                f.costShare(), f.refereeArrangement(), f.createdAt() != null ? f.createdAt() : Instant.now());
    }
}
