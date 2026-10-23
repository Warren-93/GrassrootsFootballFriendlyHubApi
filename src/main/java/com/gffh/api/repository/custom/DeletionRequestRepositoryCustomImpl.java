package com.gffh.api.repository.custom;

import com.gffh.api.model.DeletionRequest;
import com.gffh.api.model.enums.DeletionRequestStatus;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Optional;

public class DeletionRequestRepositoryCustomImpl implements DeletionRequestRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public DeletionRequestRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<DeletionRequest> saveIfStatus(DeletionRequest updated, DeletionRequestStatus expected) {
        Query query = new Query(Criteria.where("_id").is(updated.getId()).and("status").is(expected));
        return Optional.ofNullable(mongoTemplate.findAndReplace(query, updated,
                FindAndReplaceOptions.options().returnNew()));
    }
}
