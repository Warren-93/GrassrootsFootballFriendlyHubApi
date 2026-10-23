package com.gffh.api.repository.mongo;

import com.gffh.api.model.TestAccount;
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
        TestAccount doc = mongoTemplate.findById(id, TestAccount.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<TestAccount> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, TestAccount.class, COLLECTION);
    }

    @Override
    public TestAccount save(TestAccount account) {
        TestAccount toSave = account.getId() != null ? account : new TestAccount(UUID.randomUUID().toString(),
                account.getUserId(), account.getEmail(), account.getDisplayName(), account.getKind(), account.getClubId(),
                account.getTeamId(), account.getCreatedByAdminId(),
                account.getCreatedAt() != null ? account.getCreatedAt() : Instant.now());
        TestAccount doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), COLLECTION);
    }
}
