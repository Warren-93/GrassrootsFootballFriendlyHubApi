package com.gffh.api.repository.mongo;

import com.gffh.api.model.Club;
import com.gffh.api.repository.ClubRepository;
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
public class MongoClubRepository implements ClubRepository {

    private static final String COLLECTION = "clubs";

    private final MongoTemplate mongoTemplate;

    public MongoClubRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Club> findById(String id) {
        Club doc = mongoTemplate.findById(id, Club.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public Club save(Club club) {
        Club toSave = club.getId() != null ? club : withGeneratedId(club);
        Club doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    private Club withGeneratedId(Club club) {
        return new Club(UUID.randomUUID().toString(), club.getName(), club.getBadgeUrl(), club.getPostcode(),
                club.getLocation(), club.getWebsite(), club.getContactEmail(),
                club.getCreatedAt() != null ? club.getCreatedAt() : Instant.now());
    }

    @Override
    public List<Club> searchByName(String query, int limit) {
        Query q = new Query(Criteria.where("name").regex(Pattern.quote(query), "i")).limit(limit);
        return mongoTemplate.find(q, Club.class, COLLECTION);
    }
}
