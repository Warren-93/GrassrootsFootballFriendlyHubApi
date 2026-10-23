package com.gffh.api.repository;

import com.gffh.api.model.RegisteredOrganisation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public interface RegisteredOrganisationRepository extends MongoRepository<RegisteredOrganisation, String> {

    Optional<RegisteredOrganisation> findFirstByNameRegex(Pattern name);

    /** Case-insensitive exact match, for spotting duplicates. */
    default Optional<RegisteredOrganisation> findByName(String name) {
        return findFirstByNameRegex(Pattern.compile("^" + Pattern.quote(name.trim()) + "$", Pattern.CASE_INSENSITIVE));
    }

    /** Alphabetical by name. */
    List<RegisteredOrganisation> findAllByOrderByNameAsc();
}
