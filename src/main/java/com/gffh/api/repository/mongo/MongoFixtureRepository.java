package com.gffh.api.repository.mongo;

import com.gffh.api.model.Fixture;
import com.gffh.api.model.enums.FixtureStatus;
import com.gffh.api.repository.FixtureRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        Fixture doc = mongoTemplate.findById(id, Fixture.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<Fixture> findByTeamId(String teamId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("homeTeamId").is(teamId),
                Criteria.where("awayTeamId").is(teamId)));
        return mongoTemplate.find(query, Fixture.class, COLLECTION);
    }

    @Override
    public List<Fixture> search(Set<String> teamIds, LocalDate from, LocalDate to, FixtureStatus status, int limit) {
        List<Criteria> filters = new ArrayList<>();
        if (teamIds != null) {
            filters.add(new Criteria().orOperator(
                    Criteria.where("homeTeamId").in(teamIds), Criteria.where("awayTeamId").in(teamIds)));
        }
        if (from != null) filters.add(Criteria.where("date").gte(from));
        if (to != null) filters.add(Criteria.where("date").lte(to));
        if (status != null) filters.add(Criteria.where("status").is(status.name()));
        Query query = filters.isEmpty() ? new Query() : new Query(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        query.with(Sort.by(Sort.Direction.DESC, "date", "startTime")).limit(limit);
        return mongoTemplate.find(query, Fixture.class, COLLECTION);
    }

    @Override
    public boolean existsUpcomingConfirmedAtVenue(String venueId) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("venueId").is(venueId),
                Criteria.where("status").is("CONFIRMED"),
                Criteria.where("date").gte(java.time.LocalDate.now())));
        return mongoTemplate.exists(query, Fixture.class, COLLECTION);
    }

    @Override
    public boolean existsUpcomingConfirmedForTeam(String teamId) {
        Criteria team = new Criteria().orOperator(
                Criteria.where("homeTeamId").is(teamId),
                Criteria.where("awayTeamId").is(teamId));
        Query query = new Query(new Criteria().andOperator(
                team,
                Criteria.where("status").is("CONFIRMED"),
                Criteria.where("date").gte(java.time.LocalDate.now())));
        return mongoTemplate.exists(query, Fixture.class, COLLECTION);
    }

    @Override
    public Fixture save(Fixture fixture) {
        Fixture toSave = fixture.getId() != null ? fixture : withGeneratedId(fixture);
        Fixture doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    private Fixture withGeneratedId(Fixture f) {
        return new Fixture(UUID.randomUUID().toString(), f.getFriendlyRequestId(), f.getHomeTeamId(),
                f.getAwayTeamId(), f.getDate(), f.getStartTime(), f.getEndTime(), f.getVenueId(), f.getStatus(),
                f.getCostShare(), f.getRefereeArrangement(), f.getCreatedAt() != null ? f.getCreatedAt() : Instant.now());
    }
}
