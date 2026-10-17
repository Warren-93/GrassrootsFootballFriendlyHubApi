package com.gffh.api.model;

import java.time.Instant;

/** An admin-only note on a {@link Report} - never visible to the reported team. */
public record InternalNote(String adminId, String note, Instant createdAt) {
}
