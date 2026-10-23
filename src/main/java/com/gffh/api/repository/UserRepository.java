package com.gffh.api.repository;

import com.gffh.api.model.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findOneByEmail(String lowerCaseEmail);

    @Query("{ $or: [ { 'email': { $regex: ?0, $options: 'i' } }, { 'displayName': { $regex: ?0, $options: 'i' } } ] }")
    List<User> findByEmailOrDisplayNameMatching(String regex, Limit limit);

    /** User emails are stored lower-cased, so the lookup is case-insensitive. */
    default Optional<User> findByEmail(String email) {
        return findOneByEmail(email.toLowerCase(Locale.ROOT));
    }

    /** Case-insensitive email/name search, for ADM-06's user search. */
    default List<User> search(String query, int limit) {
        return findByEmailOrDisplayNameMatching(Pattern.quote(query), Limit.of(limit));
    }
}
