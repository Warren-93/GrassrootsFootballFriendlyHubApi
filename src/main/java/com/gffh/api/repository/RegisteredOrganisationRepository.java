package com.gffh.api.repository;

import com.gffh.api.model.RegisteredOrganisation;

import java.util.List;
import java.util.Optional;

public interface RegisteredOrganisationRepository {

    Optional<RegisteredOrganisation> findById(String id);

    /** Case-insensitive exact match, for spotting duplicates. */
    Optional<RegisteredOrganisation> findByName(String name);

    /** Alphabetical by name. */
    List<RegisteredOrganisation> findAll();

    RegisteredOrganisation save(RegisteredOrganisation organisation);

    void delete(String id);
}
