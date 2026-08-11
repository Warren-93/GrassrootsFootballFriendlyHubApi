package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Venue;
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
        VenueDocument doc = mongoTemplate.findById(id, VenueDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(VenueDocument::toDomain);
    }

    @Override
    public List<Venue> findByClubId(String clubId) {
        Query query = new Query(Criteria.where("clubId").is(clubId));
        return mongoTemplate.find(query, VenueDocument.class, COLLECTION).stream()
                .map(VenueDocument::toDomain).toList();
    }

    @Override
    public Venue save(Venue venue) {
        Venue toSave = venue.id() != null ? venue : withGeneratedId(venue);
        VenueDocument doc = VenueDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), VenueDocument.class, COLLECTION);
    }

    @Override
    public void clearDefaultForClub(String clubId) {
        mongoTemplate.updateMulti(new Query(Criteria.where("clubId").is(clubId)),
                Update.update("isDefault", false), VenueDocument.class, COLLECTION);
    }

    private Venue withGeneratedId(Venue v) {
        return new Venue(UUID.randomUUID().toString(), v.clubId(), v.name(), v.address(), v.location(),
                v.pitchSurface(), v.facilities(), v.accessNotes(), v.parkingNotes(), v.pitchNumber(),
                v.isDefault(), v.createdAt() != null ? v.createdAt() : Instant.now());
    }
}
