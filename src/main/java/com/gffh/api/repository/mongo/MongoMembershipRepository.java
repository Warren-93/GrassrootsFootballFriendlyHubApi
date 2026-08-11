package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Membership;
import com.gffh.api.repository.MembershipRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoMembershipRepository implements MembershipRepository {

    private static final String COLLECTION = "memberships";

    private final MongoTemplate mongoTemplate;

    public MongoMembershipRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Membership> findTeamMembership(String userId, String teamId) {
        Query query = new Query(Criteria.where("userId").is(userId).and("teamId").is(teamId));
        MembershipDocument doc = mongoTemplate.findOne(query, MembershipDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(MembershipDocument::toDomain);
    }

    @Override
    public Optional<Membership> findClubMembership(String userId, String clubId) {
        Query query = new Query(Criteria.where("userId").is(userId).and("clubId").is(clubId));
        MembershipDocument doc = mongoTemplate.findOne(query, MembershipDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(MembershipDocument::toDomain);
    }

    @Override
    public List<Membership> findByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        return mongoTemplate.find(query, MembershipDocument.class, COLLECTION).stream()
                .map(MembershipDocument::toDomain).toList();
    }

    @Override
    public Membership save(Membership membership) {
        Membership toSave = membership.id() != null ? membership : withGeneratedId(membership);
        MembershipDocument doc = MembershipDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    private Membership withGeneratedId(Membership m) {
        return new Membership(UUID.randomUUID().toString(), m.userId(), m.teamId(), m.clubId(),
                m.role(), m.createdAt() != null ? m.createdAt() : Instant.now());
    }
}
