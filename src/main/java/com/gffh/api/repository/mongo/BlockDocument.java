package com.gffh.api.repository.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("blocks")
public class BlockDocument {

    @Id
    public String id;
    public String blockingTeamId;
    public String blockedTeamId;
    public String reason;
    public Instant createdAt;
}
