package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Remembers which resource a client's Idempotency-Key header created, so a
 * retried request returns the original result instead of creating a second
 * one. The key itself is the document id.
 */
@Document("idempotencyKeys")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

    @Id
    private String id;
    private String resourceId;
    private Instant createdAt;
}
