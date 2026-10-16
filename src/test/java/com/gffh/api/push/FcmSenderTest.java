package com.gffh.api.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FcmSenderTest {

    private static final String SEND_PATH = "/v1/projects/kickabout-test/messages:send";
    private static final String TOKEN_PATH = "/token";
    private static final String OK_TOKEN = """
            {"access_token": "at-1", "expires_in": 3600, "token_type": "Bearer"}""";

    private final ObjectMapper json = new ObjectMapper();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-15T12:00:00Z"));
    private final PushMessage message = new PushMessage("New friendly request", "Rovers sent you a friendly request.",
            Map.of("type", "REQUEST_RECEIVED", "requestId", "fr-1", "teamId", "team-1"));
    private KeyPair keys;
    private StubHttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        server = new StubHttpServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void sendsTheMessageWithAnAccessTokenFromASignedAssertion() throws Exception {
        server.reply(TOKEN_PATH, 200, OK_TOKEN).reply(SEND_PATH, 200, "{\"name\": \"projects/kickabout-test/messages/1\"}");

        assertEquals(PushSender.Result.SENT, sender().send("device-token-1", message));

        StubHttpServer.Recorded tokenRequest = server.requestsTo(TOKEN_PATH).get(0);
        assertEquals("application/x-www-form-urlencoded", tokenRequest.header("Content-Type"));
        Map<String, String> form = form(tokenRequest.body());
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", form.get("grant_type"));

        SignedJWT assertion = SignedJWT.parse(form.get("assertion"));
        assertTrue(assertion.verify(new RSASSAVerifier((RSAPublicKey) keys.getPublic())));
        assertEquals(JWSAlgorithm.RS256, assertion.getHeader().getAlgorithm());
        assertEquals("key-123", assertion.getHeader().getKeyID());
        JWTClaimsSet claims = assertion.getJWTClaimsSet();
        assertEquals("push@kickabout-test.iam.gserviceaccount.com", claims.getIssuer());
        assertEquals(List.of(server.url() + TOKEN_PATH), claims.getAudience());
        assertEquals(FcmSender.SCOPE, claims.getStringClaim("scope"));
        assertEquals(clock.instant(), claims.getIssueTime().toInstant());
        assertEquals(clock.instant().plus(Duration.ofHours(1)), claims.getExpirationTime().toInstant());

        StubHttpServer.Recorded send = server.requestsTo(SEND_PATH).get(0);
        assertEquals("POST", send.method());
        assertEquals("Bearer at-1", send.header("Authorization"));
        JsonNode body = json.readTree(send.body()).path("message");
        assertEquals("device-token-1", body.path("token").asText());
        assertEquals("New friendly request", body.path("notification").path("title").asText());
        assertEquals("Rovers sent you a friendly request.", body.path("notification").path("body").asText());
        assertEquals("REQUEST_RECEIVED", body.path("data").path("type").asText());
        assertEquals("fr-1", body.path("data").path("requestId").asText());
        assertEquals("team-1", body.path("data").path("teamId").asText());
        assertFalse(body.path("data").has("fixtureId"));
        assertEquals("HIGH", body.path("android").path("priority").asText());
    }

    @Test
    void theAccessTokenIsCachedUntilShortlyBeforeItExpires() {
        server.reply(TOKEN_PATH, 200, OK_TOKEN).reply(SEND_PATH, 200, "{}");
        FcmSender sender = sender();

        sender.send("device-1", message);
        clock.advance(Duration.ofMinutes(50));
        sender.send("device-2", message);
        assertEquals(1, server.requestsTo(TOKEN_PATH).size());

        clock.advance(Duration.ofMinutes(6));
        sender.send("device-3", message);
        assertEquals(2, server.requestsTo(TOKEN_PATH).size());
        assertEquals(3, server.requestsTo(SEND_PATH).size());
    }

    @Test
    void aRejectedAccessTokenIsRefreshedAndTheSendRetriedOnce() {
        server.reply(TOKEN_PATH, 200, OK_TOKEN)
                .reply(SEND_PATH, 401, "{\"error\": {\"code\": 401, \"status\": \"UNAUTHENTICATED\"}}")
                .reply(SEND_PATH, 200, "{}");

        assertEquals(PushSender.Result.SENT, sender().send("device-1", message));
        assertEquals(2, server.requestsTo(TOKEN_PATH).size());
        assertEquals(2, server.requestsTo(SEND_PATH).size());
    }

    @Test
    void unregisteredAndMalformedTokensAreReportedInvalid() {
        server.reply(TOKEN_PATH, 200, OK_TOKEN)
                .reply(SEND_PATH, 404, fcmError(404, "NOT_FOUND", "Requested entity was not found.", "UNREGISTERED"))
                .reply(SEND_PATH, 400, fcmError(400, "INVALID_ARGUMENT",
                        "The registration token is not a valid FCM registration token", "INVALID_ARGUMENT"))
                .reply(SEND_PATH, 403, fcmError(403, "PERMISSION_DENIED", "SenderId mismatch", "SENDER_ID_MISMATCH"))
                .reply(SEND_PATH, 400, fcmError(400, "INVALID_ARGUMENT", "Invalid value at 'message.data'",
                        "INVALID_ARGUMENT"));
        FcmSender sender = sender();

        assertEquals(PushSender.Result.INVALID_TOKEN, sender.send("gone", message));
        assertEquals(PushSender.Result.INVALID_TOKEN, sender.send("garbled", message));
        assertEquals(PushSender.Result.INVALID_TOKEN, sender.send("other-project", message));
        // A bad payload is our fault, not the device's: keep the token.
        assertEquals(PushSender.Result.FAILED, sender.send("fine", message));
    }

    @Test
    void outagesAndQuotaAreFailuresThatKeepTheToken() {
        server.reply(TOKEN_PATH, 200, OK_TOKEN)
                .reply(SEND_PATH, 503, fcmError(503, "UNAVAILABLE", "The service is unavailable.", "UNAVAILABLE"))
                .reply(SEND_PATH, 429, fcmError(429, "RESOURCE_EXHAUSTED", "Quota exceeded.", "QUOTA_EXCEEDED"))
                .reply(SEND_PATH, 500, "not json");
        FcmSender sender = sender();

        assertEquals(PushSender.Result.FAILED, sender.send("device-1", message));
        assertEquals(PushSender.Result.FAILED, sender.send("device-1", message));
        assertEquals(PushSender.Result.FAILED, sender.send("device-1", message));
    }

    @Test
    void aTokenEndpointFailureIsAFailureAndNothingIsSent() {
        server.reply(TOKEN_PATH, 400, "{\"error\": \"invalid_grant\"}");

        assertEquals(PushSender.Result.FAILED, sender().send("device-1", message));
        assertTrue(server.requestsTo(SEND_PATH).isEmpty());
    }

    @Test
    void anUnreachableServerIsAFailureNotAnException() {
        FcmSender sender = sender();
        server.close();

        assertEquals(PushSender.Result.FAILED, sender.send("device-1", message));
    }

    @Test
    void theServiceAccountJsonMayBeRawOrBase64() throws Exception {
        String raw = serviceAccountJson("https://oauth2.googleapis.com/token");
        String base64 = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertEquals("kickabout-test", FcmSender.parse(raw, json).projectId());
        FcmSender.ServiceAccount decoded = FcmSender.parse(base64, json);
        assertEquals("kickabout-test", decoded.projectId());
        assertEquals("push@kickabout-test.iam.gserviceaccount.com", decoded.clientEmail());
    }

    @Test
    void theTokenUriDefaultsToGooglesWhenTheJsonOmitsIt() throws Exception {
        ObjectNode node = (ObjectNode) json.readTree(serviceAccountJson("x"));
        node.remove("token_uri");

        assertEquals("https://oauth2.googleapis.com/token", FcmSender.parse(node.toString(), json).tokenUri());
    }

    @Test
    void missingOrUnreadableConfigLeavesTheSenderOff() throws Exception {
        assertNull(FcmSender.parse("", json));
        assertNull(FcmSender.parse("{not json", json));
        assertNull(FcmSender.parse("{\"project_id\": \"p\"}", json));
        assertNull(FcmSender.parse(serviceAccountJson("x").replace("BEGIN PRIVATE KEY-----\\n",
                "BEGIN PRIVATE KEY-----\\n!!!"), json));

        FcmSender off = new FcmSender(null, json, null, clock, server.url());
        assertFalse(off.isConfigured());
        assertEquals(PushSender.Result.FAILED, off.send("device-1", message));
        assertTrue(server.requests().isEmpty());
    }

    private FcmSender sender() {
        try {
            FcmSender.ServiceAccount account = FcmSender.parse(serviceAccountJson(server.url() + TOKEN_PATH), json);
            assertNotNull(account);
            HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            FcmSender sender = new FcmSender(account, json, http, clock, server.url());
            assertTrue(sender.isConfigured());
            return sender;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String serviceAccountJson(String tokenUri) throws Exception {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return json.writeValueAsString(Map.of(
                "type", "service_account",
                "project_id", "kickabout-test",
                "private_key_id", "key-123",
                "private_key", pem,
                "client_email", "push@kickabout-test.iam.gserviceaccount.com",
                "token_uri", tokenUri));
    }

    private static String fcmError(int code, String status, String message, String errorCode) {
        return """
                {"error": {"code": %d, "message": "%s", "status": "%s", "details": [
                  {"@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", "errorCode": "%s"}]}}
                """.formatted(code, message, status, errorCode);
    }

    private static Map<String, String> form(String body) {
        return Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }
}
