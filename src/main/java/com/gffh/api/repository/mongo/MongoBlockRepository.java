package com.gffh.api.repository.mongo;

import com.gffh.api.model.Block;
import com.gffh.api.repository.BlockRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class MongoBlockRepository implements BlockRepository {

    private static final String COLLECTION = "blocks";

    private final MongoTemplate mongoTemplate;

    public MongoBlockRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Set<String> blockedTeamIdsEitherDirection(String teamId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("blockingTeamId").is(teamId),
                Criteria.where("blockedTeamId").is(teamId)));
        List<Block> docs = mongoTemplate.find(query, Block.class, COLLECTION);

        Set<String> other = new HashSet<>();
        for (Block doc : docs) {
            other.add(doc.getBlockingTeamId().equals(teamId) ? doc.getBlockedTeamId() : doc.getBlockingTeamId());
        }
        return other;
    }

    @Override
    public void block(String blockingTeamId, String blockedTeamId, String reason) {
        Query pair = new Query(Criteria.where("blockingTeamId").is(blockingTeamId).and("blockedTeamId").is(blockedTeamId));
        Update insertOnly = new Update()
                .setOnInsert("_id", UUID.randomUUID().toString())
                .setOnInsert("reason", reason)
                .setOnInsert("createdAt", Instant.now());
        mongoTemplate.upsert(pair, insertOnly, Block.class, COLLECTION);
    }

    @Override
    public List<Block> findByBlockingTeamId(String teamId) {
        Query query = new Query(Criteria.where("blockingTeamId").is(teamId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, Block.class, COLLECTION);
    }

    @Override
    public Optional<Block> findById(String id) {
        Block doc = mongoTemplate.findById(id, Block.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public void deleteById(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), Block.class, COLLECTION);
    }
}
