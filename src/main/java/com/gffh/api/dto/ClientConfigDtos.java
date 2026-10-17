package com.gffh.api.dto;



/** Wire types for {@code GET /api/v1/config}: what the clients need to know before anyone signs in. */
public final class ClientConfigDtos {

    private ClientConfigDtos() {}

    /** {@code supportEmail} is null (absent) when no address is configured. */
    public record ClientConfigView(String supportEmail, GoogleConfig google, AppleConfig apple) {}

    /** {@code clientId} is null when Google sign-in is off. */
    public record GoogleConfig(boolean enabled, String clientId) {}

    /**
     * {@code servicesId} is null when web sign-in with Apple isn't configured (iOS may still be).
     * {@code iosEnabled} says whether the iOS app's own tokens are accepted ({@code APPLE_BUNDLE_ID} set),
     * so the app doesn't offer a button that can only fail.
     */
    public record AppleConfig(boolean enabled, String servicesId, boolean iosEnabled) {}
}
