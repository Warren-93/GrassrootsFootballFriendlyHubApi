package com.gffh.api.repository.mongo;

import com.gffh.api.model.MemberInvite;
import com.gffh.api.repository.MemberInviteRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Emails are stored lower-cased, like {@link MongoUserRepository}, so lookups are case-insensitive. */
@Repository
public class MongoMemberInviteRepository implements MemberInviteRepository {

    private static final String COLLECTION = "memberInvites";

    private final MongoTemplate mongoTemplate;

    public MongoMemberInviteRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<MemberInvite> findById(String id) {
        MemberInvite doc = mongoTemplate.findById(id, MemberInvite.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public Optional<MemberInvite> findByTeamIdAndEmail(String teamId, String email) {
        Query query = new Query(Criteria.where("teamId").is(teamId).and("email").is(normalised(email)));
        MemberInvite doc = mongoTemplate.findOne(query, MemberInvite.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<MemberInvite> findPendingByTeamId(String teamId, Instant now) {
        Query query = new Query(Criteria.where("teamId").is(teamId).and("expiresAt").gt(now))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, MemberInvite.class, COLLECTION);
    }

    @Override
    public List<MemberInvite> findPendingByEmail(String email, Instant now) {
        Query query = new Query(Criteria.where("email").is(normalised(email)).and("expiresAt").gt(now))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        return mongoTemplate.find(query, MemberInvite.class, COLLECTION);
    }

    @Override
    public MemberInvite save(MemberInvite invite) {
        MemberInvite toSave = invite.getId() != null ? invite : withGeneratedId(invite);
        MemberInvite doc = toSave;
        doc.setEmail(normalised(doc.getEmail()));
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), MemberInvite.class, COLLECTION);
    }

    @Override
    public void deleteByEmail(String email) {
        mongoTemplate.remove(new Query(Criteria.where("email").is(normalised(email))),
                MemberInvite.class, COLLECTION);
    }

    private static String normalised(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private MemberInvite withGeneratedId(MemberInvite i) {
        return new MemberInvite(UUID.randomUUID().toString(), i.getEmail(), i.getTeamId(), i.getRole(), i.getInvitedByUserId(),
                i.getCreatedAt() != null ? i.getCreatedAt() : Instant.now(), i.getExpiresAt());
    }
}
