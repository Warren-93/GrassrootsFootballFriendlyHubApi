package com.gffh.api.repository.mongo;

import com.gffh.api.model.AvailabilitySlot;
import com.gffh.api.model.enums.AvailabilityStatus;
import com.gffh.api.repository.AvailabilityRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoAvailabilityRepository implements AvailabilityRepository {

    private static final String COLLECTION = "availability";

    private final MongoTemplate mongoTemplate;

    public MongoAvailabilityRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<AvailabilitySlot> findById(String id) {
        AvailabilitySlot doc = mongoTemplate.findById(id, AvailabilitySlot.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<AvailabilitySlot> findBookableForTeamBetween(String teamId, LocalDate from, LocalDate to) {
        Query query = new Query(Criteria.where("teamId").is(teamId)
                .and("date").gte(from).lte(to)
                .and("status").is(AvailabilityStatus.AVAILABLE.name()));
        return mongoTemplate.find(query, AvailabilitySlot.class, COLLECTION);
    }

    @Override
    public List<AvailabilitySlot> findBookableForTeamsBetween(List<String> teamIds, LocalDate from, LocalDate to) {
        if (teamIds.isEmpty()) return List.of();
        Query query = new Query(Criteria.where("teamId").in(teamIds)
                .and("date").gte(from).lte(to)
                .and("status").is(AvailabilityStatus.AVAILABLE.name()));
        return mongoTemplate.find(query, AvailabilitySlot.class, COLLECTION);
    }

    @Override
    public List<AvailabilitySlot> findOverlapping(String teamId, LocalDate date) {
        Query query = new Query(Criteria.where("teamId").is(teamId).and("date").is(date));
        return mongoTemplate.find(query, AvailabilitySlot.class, COLLECTION);
    }

    @Override
    public AvailabilitySlot save(AvailabilitySlot slot) {
        AvailabilitySlot toSave = slot.getId() != null ? slot : withGeneratedId(slot);
        AvailabilitySlot doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    private AvailabilitySlot withGeneratedId(AvailabilitySlot slot) {
        return new AvailabilitySlot(UUID.randomUUID().toString(), slot.getTeamId(), slot.getDate(),
                slot.getStartTime(), slot.getEndTime(), slot.getHomeAwayPreference(), slot.getVenueId(),
                slot.getFormat(), slot.getNotes(), slot.getStatus());
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), AvailabilitySlot.class, COLLECTION);
    }
}
