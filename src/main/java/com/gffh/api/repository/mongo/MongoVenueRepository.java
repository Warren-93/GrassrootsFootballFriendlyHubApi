package com.gffh.api.repository.mongo;

import com.gffh.api.model.Venue;
import com.gffh.api.repository.VenueRepository;
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
public class MongoVenueRepository implements VenueRepository {

    private static final String COLLECTION = "venues";

    private final MongoTemplate mongoTemplate;

    public MongoVenueRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Venue> findById(String id) {
        Venue doc = mongoTemplate.findById(id, Venue.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<Venue> findByClubId(String clubId) {
        Query query = new Query(Criteria.where("clubId").is(clubId));
        return mongoTemplate.find(query, Venue.class, COLLECTION);
    }

    @Override
    public Venue save(Venue venue) {
        Venue toSave = venue.getId() != null ? venue : withGeneratedId(venue);
        Venue doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), Venue.class, COLLECTION);
    }

    @Override
    public void clearDefaultForClub(String clubId) {
        mongoTemplate.updateMulti(new Query(Criteria.where("clubId").is(clubId)),
                Update.update("isDefault", false), Venue.class, COLLECTION);
    }

    private Venue withGeneratedId(Venue v) {
        return new Venue(UUID.randomUUID().toString(), v.getClubId(), v.getName(), v.getAddress(), v.getPostcode(), v.getLocation(),
                v.getPitchSurface(), v.getFacilities(), v.getAccessNotes(), v.getParkingNotes(), v.getPitchNumber(),
                v.isDefault(), v.getCreatedAt() != null ? v.getCreatedAt() : Instant.now());
    }
}
