package com.gffh.api.web;

import com.gffh.api.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookie mode for the web client. A request carrying {@code X-Auth-Mode: cookie}
 * gets its refresh token as an httpOnly cookie instead of in the JSON body, so
 * no script on the page can read it, and {@code /auth/refresh} and
 * {@code /auth/logout} read it back from that cookie.
 *
 * <p>Those two endpoints are the only ones that act on a cookie, and they are
 * guarded against cross-site requests twice over: the cookie is
 * {@code SameSite=Strict} (the web app reaches the API through its own origin
 * via a Vercel rewrite, so it's first-party), and the cookie is only honoured
 * alongside the custom mode header, which a cross-origin page can't send
 * without passing the CORS allow-list. The mobile clients never send the
 * header and keep the plain body-token flow.
 */
@Component
public class RefreshTokenCookies {

    public static final String MODE_HEADER = "X-Auth-Mode";
    public static final String COOKIE_NAME = "gffh_refresh";
    private static final String PATH = "/api/v1/auth";

    private final boolean secure;

    public RefreshTokenCookies(@Value("${gffh.auth.refresh-cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public static boolean isCookieMode(String modeHeader) {
        return "cookie".equalsIgnoreCase(modeHeader);
    }

    /** Sends the tokens the way the caller asked for them. */
    public ResponseEntity<AuthDtos.TokenResponse> respond(String modeHeader, AuthDtos.TokenResponse tokens) {
        if (!isCookieMode(modeHeader)) return ResponseEntity.ok(tokens);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), Duration.ofDays(RefreshTokenService.TTL_DAYS)).toString())
                .body(tokens.withoutRefreshToken());
    }

    public String clearingCookie() {
        return cookie("", Duration.ZERO).toString();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
