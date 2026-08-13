package com.gffh.api.repository.mongo;

import com.gffh.api.domain.JoinCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("joinCodes")
public class JoinCodeDocument {

    @Id
    public String id;
    public String teamId;
    public String code;
    public String createdByUserId;
    public Instant createdAt;

    public static JoinCodeDocument from(JoinCode j) {
        JoinCodeDocument d = new JoinCodeDocument();
        d.id = j.id();
        d.teamId = j.teamId();
        d.code = j.code();
        d.createdByUserId = j.createdByUserId();
        d.createdAt = j.createdAt();
        return d;
    }

    public JoinCode toDomain() {
        return new JoinCode(id, teamId, code, createdByUserId, createdAt);
    }
}
