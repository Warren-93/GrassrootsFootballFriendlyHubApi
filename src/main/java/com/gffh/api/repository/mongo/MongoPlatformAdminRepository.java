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
        PlatformAdminDocument doc = mongoTemplate.findById(id, PlatformAdminDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(PlatformAdminDocument::toDomain);
    }

    @Override
    public Optional<PlatformAdmin> findByEmail(String email) {
        Query query = new Query(Criteria.where("email").is(email.toLowerCase()));
        PlatformAdminDocument doc = mongoTemplate.findOne(query, PlatformAdminDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(PlatformAdminDocument::toDomain);
    }

    @Override
    public PlatformAdmin save(PlatformAdmin admin) {
        PlatformAdmin toSave = admin.id() != null ? admin : withGeneratedId(admin);
        PlatformAdminDocument doc = PlatformAdminDocument.from(toSave);
        doc.email = doc.email.toLowerCase();
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public List<PlatformAdmin> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "createdAt"));
        return mongoTemplate.find(query, PlatformAdminDocument.class, COLLECTION).stream()
                .map(PlatformAdminDocument::toDomain).toList();
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), PlatformAdminDocument.class, COLLECTION);
    }

    @Override
    public long count() {
        return mongoTemplate.getCollection(COLLECTION).countDocuments();
    }

    private PlatformAdmin withGeneratedId(PlatformAdmin admin) {
        return new PlatformAdmin(UUID.randomUUID().toString(), admin.email(), admin.passwordHash(),
                admin.totpSecret(), admin.createdAt() != null ? admin.createdAt() : Instant.now(),
                admin.lastLoginAt(), admin.role());
    }
}
