package com.gffh.api.repository;

import com.gffh.api.domain.PlatformAdmin;

import java.util.Optional;

public interface PlatformAdminRepository {

    Optional<PlatformAdmin> findById(String id);

    Optional<PlatformAdmin> findByEmail(String email);

    PlatformAdmin save(PlatformAdmin admin);

    long count();
}
