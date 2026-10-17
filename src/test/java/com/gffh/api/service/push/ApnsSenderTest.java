package com.gffh.api.service.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gffh.api.config.PushProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApnsSenderTest {

    private static final String DEVICE = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
    private static final String PATH = "/3/device/" + DEVICE;

    private final ObjectMapper json = new ObjectMapper();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-15T12:00:00Z"));
    private final PushMessage message = new PushMessage("New message", "See you Saturday!",
            Map.of("type", "MESSAGE_RECEIVED", "conversationId", "conv-1", "teamId", "team-1"));
    private KeyPair keys;
    private StubHttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keys = generator.generateKeyPair();
        server = new StubHttpServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void sendsAnAlertWithAnEs256ProviderTokenAndTheAppsTopic() throws Exception {
        server.reply(PATH, 200, "");

        assertEquals(PushSender.Result.SENT, sender().send(DEVICE, message));

        StubHttpServer.Recorded request = server.requestsTo(PATH).get(0);
        assertEquals("POST", request.method());
        assertEquals("com.kickabout.app", request.header("apns-topic"));
        assertEquals("alert", request.header("apns-push-type"));
        assertEquals("10", request.header("apns-priority"));

        String authorization = request.header("authorization");
        assertTrue(authorization.startsWith("bearer "));
        SignedJWT jwt = SignedJWT.parse(authorization.substring("bearer ".length()));
        assertTrue(jwt.verify(new ECDSAVerifier((ECPublicKey) keys.getPublic())));
        assertEquals(JWSAlgorithm.ES256, jwt.getHeader().getAlgorithm());
        assertEquals("KEY1234567", jwt.getHeader().getKeyID());
        assertEquals("TEAM123456", jwt.getJWTClaimsSet().getIssuer());
        assertEquals(clock.instant(), jwt.getJWTClaimsSet().getIssueTime().toInstant());

        JsonNode body = json.readTree(request.body());
        assertEquals("New message", body.path("aps").path("alert").path("title").asText());
        assertEquals("See you Saturday!", body.path("aps").path("alert").path("body").asText());
        assertEquals("default", body.path("aps").path("sound").asText());
        assertEquals("MESSAGE_RECEIVED", body.path("type").asText());
        assertEquals("conv-1", body.path("conversationId").asText());
        assertEquals("team-1", body.path("teamId").asText());
        assertFalse(body.has("requestId"));
    }

    @Test
    void theProviderTokenIsReusedFor50Minutes() {
        server.reply(PATH, 200, "");
        ApnsSender sender = sender();

        sender.send(DEVICE, message);
        clock.advance(Duration.ofMinutes(49));
        sender.send(DEVICE, message);
        clock.advance(Duration.ofMinutes(2));
        sender.send(DEVICE, message);

        var requests = server.requestsTo(PATH);
        assertEquals(requests.get(0).header("authorization"), requests.get(1).header("authorization"));
        assertNotEquals(requests.get(1).header("authorization"), requests.get(2).header("authorization"));
    }

    @Test
    void anExpiredProviderTokenIsReplacedAndTheSendRetriedOnce() {
        server.reply(PATH, 403, "{\"reason\": \"ExpiredProviderToken\"}").reply(PATH, 200, "");

        assertEquals(PushSender.Result.SENT, sender().send(DEVICE, message));

        var requests = server.requestsTo(PATH);
        assertEquals(2, requests.size());
        assertNotEquals(requests.get(0).header("authorization"), requests.get(1).header("authorization"));
    }

    @Test
    void goneAndBadDeviceTokensAreReportedInvalid() {
        server.reply(PATH, 410, "{\"reason\": \"Unregistered\", \"timestamp\": 1760529600000}")
                .reply(PATH, 400, "{\"reason\": \"BadDeviceToken\"}")
                .reply(PATH, 400, "{\"reason\": \"DeviceTokenNotForTopic\"}")
                .reply(PATH, 400, "{\"reason\": \"PayloadTooLarge\"}");
        ApnsSender sender = sender();

        assertEquals(PushSender.Result.INVALID_TOKEN, sender.send(DEVICE, message));
        assertEquals(PushSender.Result.INVALID_TOKEN, sender.send(DEVICE, message));
        assertEquals(PushSender.Result.INVALID_TOKEN, sender.send(DEVICE, message));
        // Our payload's fault, not the device's: keep the token.
        assertEquals(PushSender.Result.FAILED, sender.send(DEVICE, message));
    }

    @Test
    void outagesThrottlingAndBadCredentialsAreFailuresThatKeepTheToken() {
        server.reply(PATH, 503, "{\"reason\": \"ServiceUnavailable\"}")
                .reply(PATH, 429, "{\"reason\": \"TooManyRequests\"}")
                .reply(PATH, 403, "{\"reason\": \"InvalidProviderToken\"}");
        ApnsSender sender = sender();

        assertEquals(PushSender.Result.FAILED, sender.send(DEVICE, message));
        assertEquals(PushSender.Result.FAILED, sender.send(DEVICE, message));
        // Still rejected after one fresh provider token: give up rather than loop.
        assertEquals(PushSender.Result.FAILED, sender.send(DEVICE, message));
        assertEquals(4, server.requestsTo(PATH).size());
    }

    @Test
    void anUnreachableServerIsAFailureNotAnException() {
        ApnsSender sender = sender();
        server.close();

        assertEquals(PushSender.Result.FAILED, sender.send(DEVICE, message));
    }

    @Test
    void apnsProductionPicksTheHost() {
        PushProperties.Apns config = config(pem());
        assertEquals(ApnsSender.SANDBOX_URL, new ApnsSender(config, json, null, clock, null).baseUrl());

        config.setProduction(true);
        assertEquals(ApnsSender.PRODUCTION_URL, new ApnsSender(config, json, null, clock, null).baseUrl());
    }

    @Test
    void offUntilEveryValueIsSetAndWithAnUnreadableKey() {
        PushProperties.Apns noBundle = config(pem());
        noBundle.setBundleId("");
        assertFalse(new ApnsSender(noBundle, json, null, clock, server.url()).isConfigured());

        ApnsSender badKey = new ApnsSender(config("-----BEGIN PRIVATE KEY-----\nnot-a-key\n-----END PRIVATE KEY-----"),
                json, null, clock, server.url());
        assertFalse(badKey.isConfigured());
        assertEquals(PushSender.Result.FAILED, badKey.send(DEVICE, message));
        assertTrue(server.requests().isEmpty());
    }

    @Test
    void aKeyPastedOnOneLineWithEscapedNewlinesIsAccepted() {
        assertTrue(new ApnsSender(config(pem().replace("\n", "\\n")), json, null, clock, server.url())
                .isConfigured());
    }

    private ApnsSender sender() {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        ApnsSender sender = new ApnsSender(config(pem()), json, http, clock, server.url());
        assertTrue(sender.isConfigured());
        return sender;
    }

    private PushProperties.Apns config(String privateKey) {
        PushProperties.Apns config = new PushProperties.Apns();
        config.setKeyId("KEY1234567");
        config.setTeamId("TEAM123456");
        config.setBundleId("com.kickabout.app");
        config.setPrivateKey(privateKey);
        return config;
    }

    private String pem() {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }
}
