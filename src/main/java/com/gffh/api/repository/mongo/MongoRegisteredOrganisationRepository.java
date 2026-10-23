package com.gffh.api.repository.mongo;

import com.gffh.api.model.RegisteredOrganisation;
import com.gffh.api.repository.RegisteredOrganisationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Repository
public class MongoRegisteredOrganisationRepository implements RegisteredOrganisationRepository {

    private static final String COLLECTION = "registered_organisations";

    private final MongoTemplate mongoTemplate;

    public MongoRegisteredOrganisationRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<RegisteredOrganisation> findById(String id) {
        RegisteredOrganisation doc = mongoTemplate.findById(id, RegisteredOrganisation.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public Optional<RegisteredOrganisation> findByName(String name) {
        Query query = new Query(Criteria.where("name").regex("^" + Pattern.quote(name.trim()) + "$", "i"));
        RegisteredOrganisation doc = mongoTemplate.findOne(query, RegisteredOrganisation.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<RegisteredOrganisation> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "name"));
        return mongoTemplate.find(query, RegisteredOrganisation.class, COLLECTION);
    }

    @Override
    public RegisteredOrganisation save(RegisteredOrganisation o) {
        Instant now = Instant.now();
        RegisteredOrganisation toSave = new RegisteredOrganisation(
                o.getId() != null ? o.getId() : UUID.randomUUID().toString(), o.getName(), o.getShortName(),
                o.getLinkedOrganisation(), o.getContactName(), o.getContactEmail(), o.getWebsite(), o.getNotes(), o.getLeagues(),
                o.getCreatedAt() != null ? o.getCreatedAt() : now, o.getCreatedByEmail(), now);
        RegisteredOrganisation doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), RegisteredOrganisation.class, COLLECTION);
    }
}
