package com.gffh.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gffh.rate-limits")
public class RateLimitProperties {

    private int invitationsPerHour = 20;
    private int authAttemptsPerHour = 10;
    private int passwordResetsPerHour = 5;

    public int getInvitationsPerHour() { return invitationsPerHour; }
    public void setInvitationsPerHour(int v) { this.invitationsPerHour = v; }

    public int getAuthAttemptsPerHour() { return authAttemptsPerHour; }
    public void setAuthAttemptsPerHour(int v) { this.authAttemptsPerHour = v; }

    public int getPasswordResetsPerHour() { return passwordResetsPerHour; }
    public void setPasswordResetsPerHour(int v) { this.passwordResetsPerHour = v; }
}
