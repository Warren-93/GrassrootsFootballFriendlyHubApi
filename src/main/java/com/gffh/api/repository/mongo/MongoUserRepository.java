package com.gffh.api.repository.mongo;

import com.gffh.api.model.User;
import com.gffh.api.repository.UserRepository;
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
public class MongoUserRepository implements UserRepository {

    private static final String COLLECTION = "users";

    private final MongoTemplate mongoTemplate;

    public MongoUserRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<User> findById(String id) {
        User doc = mongoTemplate.findById(id, User.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        Query query = new Query(Criteria.where("email").is(email.toLowerCase()));
        User doc = mongoTemplate.findOne(query, User.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public User save(User user) {
        User toSave = user.getId() != null ? user : withGeneratedId(user);
        User doc = toSave;
        doc.setEmail(doc.getEmail().toLowerCase());
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    private User withGeneratedId(User user) {
        return new User(UUID.randomUUID().toString(), user.getEmail(), user.getPasswordHash(),
                user.getDisplayName(), user.isEmailVerified(),
                user.getCreatedAt() != null ? user.getCreatedAt() : Instant.now(), user.isSuspended());
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), User.class, COLLECTION);
    }

    @Override
    public List<User> search(String query, int limit) {
        String pattern = Pattern.quote(query);
        Query q = new Query(new Criteria().orOperator(
                Criteria.where("email").regex(pattern, "i"),
                Criteria.where("displayName").regex(pattern, "i"))).limit(limit);
        return mongoTemplate.find(q, User.class, COLLECTION);
    }
}
