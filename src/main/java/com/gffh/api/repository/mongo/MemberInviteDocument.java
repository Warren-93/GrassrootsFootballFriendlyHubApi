package com.gffh.api.repository.mongo;

import com.gffh.api.model.MemberInvite;
import com.gffh.api.model.enums.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("memberInvites")
public class MemberInviteDocument {

    @Id
    public String id;
    public String email;
    public String teamId;
    public Role role;
    public String invitedByUserId;
    public Instant createdAt;
    public Instant expiresAt;

    public static MemberInviteDocument from(MemberInvite i) {
        MemberInviteDocument d = new MemberInviteDocument();
        d.id = i.getId();
        d.email = i.getEmail();
        d.teamId = i.getTeamId();
        d.role = i.getRole();
        d.invitedByUserId = i.getInvitedByUserId();
        d.createdAt = i.getCreatedAt();
        d.expiresAt = i.getExpiresAt();
        return d;
    }

    public MemberInvite toDomain() {
        return new MemberInvite(id, email, teamId, role, invitedByUserId, createdAt, expiresAt);
    }
}
