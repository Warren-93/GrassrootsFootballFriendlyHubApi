package com.gffh.api.repository.mongo;

import com.gffh.api.domain.TestAccount;
import com.gffh.api.repository.TestAccountRepository;
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
public class MongoTestAccountRepository implements TestAccountRepository {

    private static final String COLLECTION = "test_accounts";

    private final MongoTemplate mongoTemplate;

    public MongoTestAccountRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<TestAccount> findById(String id) {
        TestAccountDocument doc = mongoTemplate.findById(id, TestAccountDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(TestAccountDocument::toDomain);
    }

    @Override
    public List<TestAccount> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, TestAccountDocument.class, COLLECTION).stream()
                .map(TestAccountDocument::toDomain).toList();
    }

    @Override
    public TestAccount save(TestAccount account) {
        TestAccount toSave = account.id() != null ? account : new TestAccount(UUID.randomUUID().toString(),
                account.userId(), account.email(), account.displayName(), account.kind(), account.clubId(),
                account.teamId(), account.createdByAdminId(),
                account.createdAt() != null ? account.createdAt() : Instant.now());
        TestAccountDocument doc = TestAccountDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), COLLECTION);
    }
}
