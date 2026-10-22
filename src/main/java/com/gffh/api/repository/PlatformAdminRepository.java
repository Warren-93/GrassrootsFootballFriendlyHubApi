package com.gffh.api.repository;

import com.gffh.api.model.PlatformAdmin;

import java.util.List;
import java.util.Optional;

public interface PlatformAdminRepository {

    Optional<PlatformAdmin> findById(String id);

    Optional<PlatformAdmin> findByEmail(String email);

    PlatformAdmin save(PlatformAdmin admin);

    /** Oldest first. */
    List<PlatformAdmin> findAll();

    void delete(String id);

    long count();
}
