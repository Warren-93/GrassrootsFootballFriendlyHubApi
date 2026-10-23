package com.gffh.api.repository.mongo;

import com.gffh.api.model.TestAccount;
import com.gffh.api.model.enums.TestAccountKind;
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
        d.id = a.getId();
        d.userId = a.getUserId();
        d.email = a.getEmail();
        d.displayName = a.getDisplayName();
        d.kind = a.getKind().name();
        d.clubId = a.getClubId();
        d.teamId = a.getTeamId();
        d.createdByAdminId = a.getCreatedByAdminId();
        d.createdAt = a.getCreatedAt();
        return d;
    }

    public TestAccount toDomain() {
        return new TestAccount(id, userId, email, displayName, TestAccountKind.valueOf(kind),
                clubId, teamId, createdByAdminId, createdAt);
    }
}
