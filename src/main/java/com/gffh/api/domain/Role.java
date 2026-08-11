package com.gffh.api.domain;

public enum Role {
    USER, TEAM_MANAGER, CLUB_ADMIN, PLATFORM_ADMIN;

    public boolean canManageTeam() { return this == TEAM_MANAGER || this == CLUB_ADMIN; }

    public boolean canManageClub() { return this == CLUB_ADMIN; }
}
