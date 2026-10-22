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
        RegisteredOrganisationDocument doc = mongoTemplate.findById(id, RegisteredOrganisationDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(RegisteredOrganisationDocument::toDomain);
    }

    @Override
    public Optional<RegisteredOrganisation> findByName(String name) {
        Query query = new Query(Criteria.where("name").regex("^" + Pattern.quote(name.trim()) + "$", "i"));
        RegisteredOrganisationDocument doc = mongoTemplate.findOne(query, RegisteredOrganisationDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(RegisteredOrganisationDocument::toDomain);
    }

    @Override
    public List<RegisteredOrganisation> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "name"));
        return mongoTemplate.find(query, RegisteredOrganisationDocument.class, COLLECTION).stream()
                .map(RegisteredOrganisationDocument::toDomain).toList();
    }

    @Override
    public RegisteredOrganisation save(RegisteredOrganisation o) {
        Instant now = Instant.now();
        RegisteredOrganisation toSave = new RegisteredOrganisation(
                o.id() != null ? o.id() : UUID.randomUUID().toString(), o.name(), o.shortName(),
                o.linkedOrganisation(), o.contactName(), o.contactEmail(), o.website(), o.notes(), o.leagues(),
                o.createdAt() != null ? o.createdAt() : now, o.createdByEmail(), now);
        RegisteredOrganisationDocument doc = RegisteredOrganisationDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), RegisteredOrganisationDocument.class, COLLECTION);
    }
}
