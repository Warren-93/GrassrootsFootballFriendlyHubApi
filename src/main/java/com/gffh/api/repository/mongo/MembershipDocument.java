package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Membership;
import com.gffh.api.domain.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("memberships")
public class MembershipDocument {

    @Id
    public String id;
    public String userId;
    public String teamId;
    public String clubId;
    public String role;
    public Instant createdAt;

    public static MembershipDocument from(Membership m) {
        MembershipDocument d = new MembershipDocument();
        d.id = m.id();
        d.userId = m.userId();
        d.teamId = m.teamId();
        d.clubId = m.clubId();
        d.role = m.role().name();
        d.createdAt = m.createdAt();
        return d;
    }

    public Membership toDomain() {
        return new Membership(id, userId, teamId, clubId, Role.valueOf(role), createdAt);
    }
}
