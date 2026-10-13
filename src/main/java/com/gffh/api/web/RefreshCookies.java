package com.gffh.api.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The browser login flow: the refresh token lives in an httpOnly cookie that
 * page scripts can't read, instead of being handed to the web app to store.
 * The web app keeps only the short-lived access token, in memory.
 *
 * <p>A request opts in with the {@value #MODE_HEADER} header. That header is
 * not CORS-safelisted, so a browser must pass a preflight first, and the cookie
 * is only read or written when the request's Origin is one of the exact
 * configured origins - never the *.vercel.app preview pattern CORS also
 * accepts, since anyone can host a page there. Everything else (the mobile
 * apps, Vercel previews) keeps receiving the refresh token in the body.
 */
@Component
public class RefreshCookies {

    public static final String MODE_HEADER = "X-Auth-Mode";
    public static final String COOKIE_MODE = "cookie";
    static final String COOKIE_NAME = "gffh_refresh";
    static final String COOKIE_PATH = "/api/v1/auth";
    private static final Duration MAX_AGE = Duration.ofDays(30);

    private final List<String> trustedOrigins;
    private final boolean secure;
    private final String sameSite;

    public RefreshCookies(
            @Value("${gffh.cors.allowed-origins:}") List<String> trustedOrigins,
            @Value("${gffh.auth.cookie.secure:true}") boolean secure,
            @Value("${gffh.auth.cookie.same-site:None}") String sameSite) {
        this.trustedOrigins = trustedOrigins;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    /** True when this request asked for the cookie flow from a trusted origin. */
    public boolean applies(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        return COOKIE_MODE.equalsIgnoreCase(request.getHeader(MODE_HEADER))
                && origin != null && trustedOrigins.contains(origin);
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> !v.isBlank())
                .findFirst();
    }

    public void write(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, header(refreshToken, MAX_AGE.toSeconds()));
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, header("", 0));
    }

    // Built by hand rather than with ResponseCookie: the Spring version in use
    // can't emit Partitioned, which lets Chrome and Firefox keep a cross-site
    // (SameSite=None) cookie when third-party cookies are otherwise blocked.
    private String header(String value, long maxAgeSeconds) {
        StringBuilder sb = new StringBuilder()
                .append(COOKIE_NAME).append('=').append(value)
                .append("; Path=").append(COOKIE_PATH)
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly");
        if (secure) sb.append("; Secure");
        sb.append("; SameSite=").append(sameSite);
        if (secure && "None".equalsIgnoreCase(sameSite)) sb.append("; Partitioned");
        return sb.toString();
    }
}
