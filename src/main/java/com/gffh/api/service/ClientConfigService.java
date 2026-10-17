package com.gffh.api.service;

import com.gffh.api.config.OAuthProperties;
import com.gffh.api.dto.ClientConfigDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The public client configuration: the support address to show, and which
 * sign-in providers are switched on. Nothing here is secret - it is all
 * visible in the clients anyway.
 */
@Service
public class ClientConfigService {

    private final OAuthProperties oauth;
    private final String supportEmail;

    public ClientConfigService(OAuthProperties oauth,
                               @Value("${gffh.mail.support-email:}") String supportEmail,
                               @Value("${gffh.mail.from:}") String mailFrom) {
        this.oauth = oauth;
        this.supportEmail = supportEmail(supportEmail, mailFrom);
    }

    public ClientConfigDtos.ClientConfigView current() {
        OAuthProperties.Google google = oauth.getGoogle();
        OAuthProperties.Apple apple = oauth.getApple();
        return new ClientConfigDtos.ClientConfigView(supportEmail,
                new ClientConfigDtos.GoogleConfig(google.isEnabled(), blankToNull(google.getClientId())),
                new ClientConfigDtos.AppleConfig(apple.isEnabled(), blankToNull(apple.getServicesId()),
                        !apple.getBundleId().isBlank()));
    }

    /**
     * {@code SUPPORT_EMAIL} when set, else the bare address inside
     * {@code MAIL_FROM} ({@code Kickabout <noreply@x.com>} gives
     * {@code noreply@x.com}), else null.
     */
    static String supportEmail(String configured, String mailFrom) {
        if (configured != null && !configured.isBlank()) return configured.trim();
        if (mailFrom == null || mailFrom.isBlank()) return null;
        String from = mailFrom.trim();
        int open = from.lastIndexOf('<');
        int close = from.lastIndexOf('>');
        if (open >= 0 && close > open) from = from.substring(open + 1, close).trim();
        return from.contains("@") && !from.contains(" ") ? from : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
