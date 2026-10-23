package com.gffh.api.repository.mongo;

import com.gffh.api.model.Membership;
import com.gffh.api.model.enums.Role;
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
        d.id = m.getId();
        d.userId = m.getUserId();
        d.teamId = m.getTeamId();
        d.clubId = m.getClubId();
        d.role = m.getRole().name();
        d.createdAt = m.getCreatedAt();
        return d;
    }

    public Membership toDomain() {
        return new Membership(id, userId, teamId, clubId, Role.valueOf(role), createdAt);
    }
}
