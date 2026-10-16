package com.gffh.api.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gffh.api.config.PushProperties;
import com.gffh.api.domain.PushPlatform;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
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
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Android pushes through Firebase Cloud Messaging's HTTP v1 API.
 *
 * <p>Configured by {@code FCM_SERVICE_ACCOUNT_JSON}: the service-account key
 * Firebase hands out, pasted raw or base64-encoded. The sender signs a short
 * JWT with that key, trades it at Google's token endpoint for an hour-long
 * OAuth access token (cached until five minutes before it expires), and
 * posts each message to {@code /v1/projects/<project_id>/messages:send}.
 * With no JSON, or JSON it can't read, the sender is off.
 */
@Component
public class FcmSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmSender.class);
    static final String FCM_BASE_URL = "https://fcm.googleapis.com";
    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration ASSERTION_LIFETIME = Duration.ofHours(1);
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    /** The parts of the service-account JSON this sender uses. */
    record ServiceAccount(String projectId, String clientEmail, String privateKeyId, PrivateKey privateKey,
                          String tokenUri) {}

    private record AccessToken(String value, Instant refreshAt) {}

    private final ServiceAccount account;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Clock clock;
    private final String fcmBaseUrl;
    private final ReentrantLock tokenLock = new ReentrantLock();
    private AccessToken accessToken;

    @Autowired
    public FcmSender(PushProperties properties, ObjectMapper json) {
        this(parse(properties.getFcm().getServiceAccountJson(), json), json, null, Clock.systemUTC(), FCM_BASE_URL);
    }

    FcmSender(ServiceAccount account, ObjectMapper json, HttpClient http, Clock clock, String fcmBaseUrl) {
        this.account = account;
        this.json = json;
        this.http = http != null || account == null ? http
                : HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.clock = clock;
        this.fcmBaseUrl = fcmBaseUrl;
        if (account != null) log.info("FCM push configured for project {}", account.projectId());
    }

    @Override
    public PushPlatform platform() {
        return PushPlatform.ANDROID;
    }

    @Override
    public boolean isConfigured() {
        return account != null;
    }

    @Override
    public Result send(String deviceToken, PushMessage message) {
        if (account == null) return Result.FAILED;
        try {
            HttpResponse<String> response = post(deviceToken, message, accessToken(false));
            if (response.statusCode() == 401) {
                // The cached access token stopped working early (revoked, or the
                // key was rotated): fetch a fresh one and try once more.
                response = post(deviceToken, message, accessToken(true));
            }
            return interpret(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (Exception e) {
            log.warn("FCM push failed: {}", e.toString());
            return Result.FAILED;
        }
    }

    private HttpResponse<String> post(String deviceToken, PushMessage message, String bearer)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(fcmBaseUrl + "/v1/projects/" + account.projectId() + "/messages:send"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body(deviceToken, message)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** {"message": {"token", "notification": {title, body}, "data": {...}, "android": {"priority": "HIGH"}}}. */
    String body(String deviceToken, PushMessage message) throws IOException {
        ObjectNode root = json.createObjectNode();
        ObjectNode msg = root.putObject("message");
        msg.put("token", deviceToken);
        ObjectNode notification = msg.putObject("notification");
        notification.put("title", message.title());
        notification.put("body", message.body());
        if (!message.data().isEmpty()) {
            ObjectNode data = msg.putObject("data");
            message.data().forEach(data::put);
        }
        msg.putObject("android").put("priority", "HIGH");
        return json.writeValueAsString(root);
    }

    /**
     * UNREGISTERED (404) means the app was uninstalled or the token rotated;
     * SENDER_ID_MISMATCH means the token belongs to another Firebase project;
     * INVALID_ARGUMENT about the registration token means it was never valid.
     * All three are permanent, so the token should be forgotten. Anything else
     * (quota, outage, our own credentials) is not the token's fault.
     */
    Result interpret(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return Result.SENT;

        JsonNode error = readError(response.body());
        String errorStatus = error.path("status").asText("");
        String errorMessage = error.path("message").asText("");
        String fcmCode = "";
        for (JsonNode detail : error.path("details")) {
            if (detail.hasNonNull("errorCode")) fcmCode = detail.get("errorCode").asText();
        }

        boolean invalidToken = status == 404
                || "UNREGISTERED".equals(fcmCode)
                || "SENDER_ID_MISMATCH".equals(fcmCode)
                || (status == 400 && ("INVALID_ARGUMENT".equals(fcmCode) || "INVALID_ARGUMENT".equals(errorStatus))
                    && errorMessage.toLowerCase(Locale.ROOT).contains("registration token"));
        if (invalidToken) return Result.INVALID_TOKEN;

        log.warn("FCM push rejected: HTTP {} {} {}", status, errorStatus, fcmCode);
        return Result.FAILED;
    }

    private JsonNode readError(String body) {
        try {
            return json.readTree(body == null || body.isBlank() ? "{}" : body).path("error");
        } catch (IOException e) {
            return json.missingNode();
        }
    }

    /** The cached OAuth access token, fetching a new one when it is close to expiry or {@code forceRefresh}. */
    String accessToken(boolean forceRefresh) throws IOException, InterruptedException, JOSEException {
        tokenLock.lock();
        try {
            Instant now = clock.instant();
            if (!forceRefresh && accessToken != null && now.isBefore(accessToken.refreshAt())) {
                return accessToken.value();
            }
            String form = "grant_type=" + URLEncoder.encode(JWT_BEARER, StandardCharsets.UTF_8)
                    + "&assertion=" + URLEncoder.encode(assertion(now), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(account.tokenUri()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                accessToken = null;
                throw new IOException("Google token endpoint answered HTTP " + response.statusCode());
            }
            JsonNode body = json.readTree(response.body());
            String value = body.path("access_token").asText("");
            if (value.isBlank()) throw new IOException("Google token endpoint returned no access_token");
            long expiresIn = body.path("expires_in").asLong(3600);
            accessToken = new AccessToken(value, now.plusSeconds(expiresIn).minus(REFRESH_MARGIN));
            return value;
        } finally {
            tokenLock.unlock();
        }
    }

    /** The self-signed JWT Google's token endpoint exchanges for an access token (RFC 7523). */
    String assertion(Instant now) throws JOSEException {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(account.privateKeyId())
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(account.clientEmail())
                .audience(account.tokenUri())
                .claim("scope", SCOPE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ASSERTION_LIFETIME)))
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(account.privateKey()));
        return jwt.serialize();
    }

    /** Null (sender off) when unset or unreadable; a bad value is logged, never fatal to startup. */
    static ServiceAccount parse(String raw, ObjectMapper json) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String text = raw.trim();
            if (!text.startsWith("{")) {
                text = new String(Base64.getMimeDecoder().decode(text), StandardCharsets.UTF_8).trim();
            }
            JsonNode node = json.readTree(text);
            String projectId = node.path("project_id").asText("");
            String clientEmail = node.path("client_email").asText("");
            String privateKey = node.path("private_key").asText("");
            if (projectId.isBlank() || clientEmail.isBlank() || privateKey.isBlank()) {
                log.warn("FCM_SERVICE_ACCOUNT_JSON is missing project_id, client_email or private_key; "
                        + "Android push is off");
                return null;
            }
            String tokenUri = node.path("token_uri").asText("");
            return new ServiceAccount(projectId, clientEmail, node.path("private_key_id").asText(null),
                    PemKeys.pkcs8(privateKey, "RSA"), tokenUri.isBlank() ? DEFAULT_TOKEN_URI : tokenUri);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            log.warn("FCM_SERVICE_ACCOUNT_JSON could not be read ({}); Android push is off", e.getClass().getSimpleName());
            return null;
        }
    }
}
