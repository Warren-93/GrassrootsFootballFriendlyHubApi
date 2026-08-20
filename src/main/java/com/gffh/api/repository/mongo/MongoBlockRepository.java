package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Block;
import com.gffh.api.repository.BlockRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
        List<BlockDocument> docs = mongoTemplate.find(query, BlockDocument.class, COLLECTION);

        Set<String> other = new HashSet<>();
        for (BlockDocument doc : docs) {
            other.add(doc.blockingTeamId.equals(teamId) ? doc.blockedTeamId : doc.blockingTeamId);
        }
        return other;
    }

    @Override
    public void block(String blockingTeamId, String blockedTeamId, String reason) {
        BlockDocument doc = new BlockDocument();
        doc.id = UUID.randomUUID().toString();
        doc.blockingTeamId = blockingTeamId;
        doc.blockedTeamId = blockedTeamId;
        doc.reason = reason;
        doc.createdAt = Instant.now();
        mongoTemplate.save(doc, COLLECTION);
    }

    @Override
    public List<Block> findByBlockingTeamId(String teamId) {
        Query query = new Query(Criteria.where("blockingTeamId").is(teamId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, BlockDocument.class, COLLECTION).stream()
                .map(BlockDocument::toDomain).toList();
    }

    @Override
    public Optional<Block> findById(String id) {
        BlockDocument doc = mongoTemplate.findById(id, BlockDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(BlockDocument::toDomain);
    }

    @Override
    public void deleteById(String id) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(id)), BlockDocument.class, COLLECTION);
    }
}
