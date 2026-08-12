package com.gffh.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gffh.rate-limits")
public class RateLimitProperties {

    private int invitationsPerHour = 20;
    private int authAttemptsPerHour = 10;

    public int getInvitationsPerHour() { return invitationsPerHour; }
    public void setInvitationsPerHour(int v) { this.invitationsPerHour = v; }

    public int getAuthAttemptsPerHour() { return authAttemptsPerHour; }
    public void setAuthAttemptsPerHour(int v) { this.authAttemptsPerHour = v; }
}
