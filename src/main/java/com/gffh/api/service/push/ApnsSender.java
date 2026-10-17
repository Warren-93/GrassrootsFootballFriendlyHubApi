package com.gffh.api.service.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gffh.api.config.PushProperties;
import com.gffh.api.model.enums.PushPlatform;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * iOS pushes through Apple Push Notification service, with token-based
 * authentication over HTTP/2.
 *
 * <p>Configured by {@code APNS_KEY_ID}, {@code APNS_TEAM_ID},
 * {@code APNS_PRIVATE_KEY} (the .p8 file's PEM contents) and
 * {@code APNS_BUNDLE_ID}; off until all four are set. {@code APNS_PRODUCTION}
 * picks api.push.apple.com over the sandbox, which only development builds
 * (run from Xcode) can receive from.
 *
 * <p>Every request carries an ES256 provider token signed with the .p8 key.
 * Apple rejects tokens older than an hour and throttles ones refreshed more
 * than every 20 minutes, so one is cached for 50 minutes.
 */
@Component
public class ApnsSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ApnsSender.class);
    static final String PRODUCTION_URL = "https://api.push.apple.com";
    static final String SANDBOX_URL = "https://api.sandbox.push.apple.com";
    static final Duration PROVIDER_TOKEN_LIFETIME = Duration.ofMinutes(50);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** Reasons Apple gives for a device token that will never work for this app again. */
    private static final Set<String> INVALID_TOKEN_REASONS = Set.of("BadDeviceToken", "Unregistered",
            "DeviceTokenNotForTopic");
    private static final Set<String> STALE_PROVIDER_TOKEN_REASONS = Set.of("ExpiredProviderToken",
            "InvalidProviderToken");

    private record ProviderToken(String value, Instant refreshAt) {}

    private final String keyId;
    private final String teamId;
    private final String bundleId;
    private final ECPrivateKey key;
    private final String baseUrl;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Clock clock;
    private final ReentrantLock tokenLock = new ReentrantLock();
    private ProviderToken providerToken;

    @Autowired
    public ApnsSender(PushProperties properties, ObjectMapper json) {
        this(properties.getApns(), json, null, Clock.systemUTC(), null);
    }

    /** {@code baseUrl} overrides the Apple host (tests); null picks production or sandbox from config. */
    ApnsSender(PushProperties.Apns config, ObjectMapper json, HttpClient http, Clock clock, String baseUrl) {
        this.keyId = config.getKeyId();
        this.teamId = config.getTeamId();
        this.bundleId = config.getBundleId();
        this.key = config.isComplete() ? parseKey(config.getPrivateKey()) : null;
        this.baseUrl = baseUrl != null ? baseUrl : config.isProduction() ? PRODUCTION_URL : SANDBOX_URL;
        this.json = json;
        this.http = http != null || key == null ? http
                : HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).connectTimeout(TIMEOUT).build();
        this.clock = clock;
        if (key != null) log.info("APNs push configured for {} ({})", bundleId, this.baseUrl);
    }

    @Override
    public PushPlatform platform() {
        return PushPlatform.IOS;
    }

    @Override
    public boolean isConfigured() {
        return key != null;
    }

    /** Which Apple host pushes go to. */
    String baseUrl() {
        return baseUrl;
    }

    @Override
    public Result send(String deviceToken, PushMessage message) {
        if (key == null) return Result.FAILED;
        try {
            String body = body(message);
            HttpResponse<String> response = post(deviceToken, body, providerToken(false));
            if (response.statusCode() == 403 && STALE_PROVIDER_TOKEN_REASONS.contains(reason(response))) {
                response = post(deviceToken, body, providerToken(true));
            }
            return interpret(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (Exception e) {
            log.warn("APNs push failed: {}", e.toString());
            return Result.FAILED;
        }
    }

    private HttpResponse<String> post(String deviceToken, String body, String bearer)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        baseUrl + "/3/device/" + URLEncoder.encode(deviceToken, StandardCharsets.UTF_8)))
                .timeout(TIMEOUT)
                .header("authorization", "bearer " + bearer)
                .header("apns-topic", bundleId)
                .header("apns-push-type", "alert")
                .header("apns-priority", "10")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** {"aps": {"alert": {title, body}, "sound": "default"}, ...data keys at the top level}. */
    String body(PushMessage message) throws IOException {
        ObjectNode root = json.createObjectNode();
        ObjectNode aps = root.putObject("aps");
        ObjectNode alert = aps.putObject("alert");
        alert.put("title", message.title());
        alert.put("body", message.body());
        aps.put("sound", "default");
        message.data().forEach((k, v) -> {
            if (!"aps".equals(k)) root.put(k, v);
        });
        return json.writeValueAsString(root);
    }

    Result interpret(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 200) return Result.SENT;
        String reason = reason(response);
        if (status == 410 || INVALID_TOKEN_REASONS.contains(reason)) return Result.INVALID_TOKEN;
        log.warn("APNs push rejected: HTTP {} {}", status, reason);
        return Result.FAILED;
    }

    private String reason(HttpResponse<String> response) {
        String body = response.body();
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode node = json.readTree(body);
            return node.path("reason").asText("");
        } catch (IOException e) {
            return "";
        }
    }

    /** The cached provider token, signing a new one after 50 minutes or when {@code forceRefresh}. */
    String providerToken(boolean forceRefresh) throws JOSEException {
        tokenLock.lock();
        try {
            Instant now = clock.instant();
            if (!forceRefresh && providerToken != null && now.isBefore(providerToken.refreshAt())) {
                return providerToken.value();
            }
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(teamId)
                    .issueTime(Date.from(now))
                    .build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new ECDSASigner(key));
            providerToken = new ProviderToken(jwt.serialize(), now.plus(PROVIDER_TOKEN_LIFETIME));
            return providerToken.value();
        } finally {
            tokenLock.unlock();
        }
    }

    /** Null (sender off) when the key can't be read; logged, never fatal to startup. */
    private static ECPrivateKey parseKey(String pem) {
        try {
            return (ECPrivateKey) PemKeys.pkcs8(pem, "EC");
        } catch (GeneralSecurityException | ClassCastException e) {
            log.warn("APNS_PRIVATE_KEY is not a .p8 (PKCS#8 PEM EC) key ({}); iOS push is off",
                    e.getClass().getSimpleName());
            return null;
        }
    }
}
