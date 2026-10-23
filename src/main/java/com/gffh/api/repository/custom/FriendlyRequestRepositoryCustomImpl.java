package com.gffh.api.repository.custom;

import com.gffh.api.model.FriendlyRequest;
import com.gffh.api.model.enums.RequestStatus;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Optional;

public class FriendlyRequestRepositoryCustomImpl implements FriendlyRequestRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public FriendlyRequestRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<FriendlyRequest> saveIfStatus(FriendlyRequest request, RequestStatus expectedStatus) {
        Query query = new Query(Criteria.where("_id").is(request.getId()).and("status").is(expectedStatus.name()));
        FriendlyRequest saved = mongoTemplate.findAndReplace(query, request, FindAndReplaceOptions.options().returnNew());
        return Optional.ofNullable(saved);
    }
}
