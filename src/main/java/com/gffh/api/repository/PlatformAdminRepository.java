package com.gffh.api.repository;

import com.gffh.api.model.PlatformAdmin;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface PlatformAdminRepository extends MongoRepository<PlatformAdmin, String> {

    Optional<PlatformAdmin> findOneByEmail(String lowerCaseEmail);

    /** Admin emails are stored lower-cased, so the lookup is case-insensitive. */
    default Optional<PlatformAdmin> findByEmail(String email) {
        return findOneByEmail(email.toLowerCase(Locale.ROOT));
    }

    /** Oldest first - the oldest admin is the super admin. */
    List<PlatformAdmin> findAllByOrderByCreatedAtAsc();
}
