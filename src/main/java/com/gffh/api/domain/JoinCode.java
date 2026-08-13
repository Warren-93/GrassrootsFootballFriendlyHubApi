package com.gffh.api.domain;

import java.time.Instant;

/**
 * SCR-PR-04's self-service alternative to being added by email: a manager
 * shares this code and anyone holding it can join the team directly, without
 * an existing manager needing to look up their account. One active code per
 * team - regenerating replaces it, immediately invalidating the old one.
 */
public record JoinCode(String id, String teamId, String code, String createdByUserId, Instant createdAt) {}
