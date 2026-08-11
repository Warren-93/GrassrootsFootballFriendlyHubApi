package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Club;
import com.gffh.api.repository.ClubRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoClubRepository implements ClubRepository {

    private static final String COLLECTION = "clubs";

    private final MongoTemplate mongoTemplate;

    public MongoClubRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Club> findById(String id) {
        ClubDocument doc = mongoTemplate.findById(id, ClubDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(ClubDocument::toDomain);
    }

    @Override
    public Club save(Club club) {
        Club toSave = club.id() != null ? club : withGeneratedId(club);
        ClubDocument doc = ClubDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    private Club withGeneratedId(Club club) {
        return new Club(UUID.randomUUID().toString(), club.name(), club.badgeUrl(), club.postcode(),
                club.location(), club.website(), club.contactEmail(),
                club.createdAt() != null ? club.createdAt() : Instant.now());
    }
}
