package com.gffh.api.repository;

import com.gffh.api.model.Block;
import com.gffh.api.repository.custom.BlockRepositoryCustom;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BlockRepository extends MongoRepository<Block, String>, BlockRepositoryCustom {

    /** The blocks *this* team placed, newest first - one-directional, unlike the matching-time set. */
    List<Block> findByBlockingTeamIdOrderByCreatedAtDesc(String teamId);
}
