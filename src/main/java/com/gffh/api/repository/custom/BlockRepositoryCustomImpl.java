package com.gffh.api.repository.custom;

import com.gffh.api.model.Block;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BlockRepositoryCustomImpl implements BlockRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public BlockRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Set<String> blockedTeamIdsEitherDirection(String teamId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("blockingTeamId").is(teamId),
                Criteria.where("blockedTeamId").is(teamId)));

        Set<String> other = new HashSet<>();
        for (Block block : mongoTemplate.find(query, Block.class)) {
            other.add(block.getBlockingTeamId().equals(teamId) ? block.getBlockedTeamId() : block.getBlockingTeamId());
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
        mongoTemplate.upsert(pair, insertOnly, Block.class);
    }
}
