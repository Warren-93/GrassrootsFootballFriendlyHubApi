package com.gffh.api.repository;

import com.gffh.api.domain.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(String id);

    Optional<User> findByEmail(String email);

    User save(User user);

    /** Case-insensitive email/name search, for ADM-06's user search. */
    List<User> search(String query, int limit);
}
