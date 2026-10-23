package com.gffh.api.repository.custom;

import com.gffh.api.model.Fixture;
import com.gffh.api.model.enums.FixtureStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FixtureRepositoryCustomImpl implements FixtureRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public FixtureRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
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
        if (status != null) filters.add(Criteria.where("status").is(status));
        Query query = filters.isEmpty() ? new Query() : new Query(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        query.with(Sort.by(Sort.Direction.DESC, "date", "startTime")).limit(limit);
        return mongoTemplate.find(query, Fixture.class);
    }
}
