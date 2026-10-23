package com.gffh.api.repository;

import com.gffh.api.model.TestAccount;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TestAccountRepository extends MongoRepository<TestAccount, String> {

    List<TestAccount> findAllByOrderByCreatedAtDesc();
}
