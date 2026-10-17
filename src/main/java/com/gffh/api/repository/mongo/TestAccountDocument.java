package com.gffh.api.repository.mongo;

import com.gffh.api.domain.TestAccount;
import com.gffh.api.domain.TestAccountKind;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("test_accounts")
public class TestAccountDocument {

    @Id
    public String id;
    public String userId;
    public String email;
    public String displayName;
    public String kind;
    public String clubId;
    public String teamId;
    public String createdByAdminId;
    public Instant createdAt;

    public static TestAccountDocument from(TestAccount a) {
        TestAccountDocument d = new TestAccountDocument();
        d.id = a.id();
        d.userId = a.userId();
        d.email = a.email();
        d.displayName = a.displayName();
        d.kind = a.kind().name();
        d.clubId = a.clubId();
        d.teamId = a.teamId();
        d.createdByAdminId = a.createdByAdminId();
        d.createdAt = a.createdAt();
        return d;
    }

    public TestAccount toDomain() {
        return new TestAccount(id, userId, email, displayName, TestAccountKind.valueOf(kind),
                clubId, teamId, createdByAdminId, createdAt);
    }
}
