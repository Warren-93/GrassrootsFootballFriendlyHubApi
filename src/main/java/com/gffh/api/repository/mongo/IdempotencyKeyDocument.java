package com.gffh.api.repository.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("idempotencyKeys")
public class IdempotencyKeyDocument {

    @Id
    public String id;
    public String resourceId;
    public Instant createdAt;
}
