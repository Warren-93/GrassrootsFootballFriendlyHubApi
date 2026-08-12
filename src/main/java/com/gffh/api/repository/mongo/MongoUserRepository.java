package com.gffh.api.repository.mongo;

import com.gffh.api.domain.User;
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
        UserDocument doc = mongoTemplate.findById(id, UserDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(UserDocument::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        Query query = new Query(Criteria.where("email").is(email.toLowerCase()));
        UserDocument doc = mongoTemplate.findOne(query, UserDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(UserDocument::toDomain);
    }

    @Override
    public User save(User user) {
        User toSave = user.id() != null ? user : withGeneratedId(user);
        UserDocument doc = UserDocument.from(toSave);
        doc.email = doc.email.toLowerCase();
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    private User withGeneratedId(User user) {
        return new User(UUID.randomUUID().toString(), user.email(), user.passwordHash(),
                user.displayName(), user.emailVerified(),
                user.createdAt() != null ? user.createdAt() : Instant.now(), user.suspended());
    }

    @Override
    public List<User> search(String query, int limit) {
        String pattern = Pattern.quote(query);
        Query q = new Query(new Criteria().orOperator(
                Criteria.where("email").regex(pattern, "i"),
                Criteria.where("displayName").regex(pattern, "i"))).limit(limit);
        return mongoTemplate.find(q, UserDocument.class, COLLECTION).stream()
                .map(UserDocument::toDomain).toList();
    }
}
