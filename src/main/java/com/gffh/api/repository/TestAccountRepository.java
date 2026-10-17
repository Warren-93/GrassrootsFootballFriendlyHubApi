package com.gffh.api.repository;

import com.gffh.api.model.TestAccount;

import java.util.List;
import java.util.Optional;

public interface TestAccountRepository {

    Optional<TestAccount> findById(String id);

    /** Newest first. */
    List<TestAccount> findAll();

    TestAccount save(TestAccount account);

    void delete(String id);
}
