package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SCR-PR-04's self-service alternative to being added by email: a manager
 * shares this code and anyone holding it can join the team directly, without
 * an existing manager needing to look up their account. One active code per
 * team - regenerating replaces it, immediately invalidating the old one.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinCode {

    private String id;
    private String teamId;
    private String code;
    private String createdByUserId;
    private Instant createdAt;
}
