package com.gffh.api.repository.mongo;

import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.repository.PlatformAdminRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoPlatformAdminRepository implements PlatformAdminRepository {

    private static final String COLLECTION = "platform_admins";

    private final MongoTemplate mongoTemplate;

    public MongoPlatformAdminRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<PlatformAdmin> findById(String id) {
        PlatformAdmin doc = mongoTemplate.findById(id, PlatformAdmin.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public Optional<PlatformAdmin> findByEmail(String email) {
        Query query = new Query(Criteria.where("email").is(email.toLowerCase()));
        PlatformAdmin doc = mongoTemplate.findOne(query, PlatformAdmin.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public PlatformAdmin save(PlatformAdmin admin) {
        PlatformAdmin toSave = admin.getId() != null ? admin : withGeneratedId(admin);
        PlatformAdmin doc = toSave;
        doc.setEmail(doc.getEmail().toLowerCase());
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    @Override
    public List<PlatformAdmin> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "createdAt"));
        return mongoTemplate.find(query, PlatformAdmin.class, COLLECTION);
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), PlatformAdmin.class, COLLECTION);
    }

    @Override
    public long count() {
        return mongoTemplate.getCollection(COLLECTION).countDocuments();
    }

    private PlatformAdmin withGeneratedId(PlatformAdmin admin) {
        return new PlatformAdmin(UUID.randomUUID().toString(), admin.getEmail(), admin.getPasswordHash(),
                admin.getTotpSecret(), admin.getCreatedAt() != null ? admin.getCreatedAt() : Instant.now(),
                admin.getLastLoginAt(), admin.getRole());
    }
}
