package com.gffh.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gffh.api.domain.VerificationTokenPurpose;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrevoMailerTest {

    private final ObjectMapper json = new ObjectMapper();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private int status = 201;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(exchange.getRequestHeaders().getFirst("api-key"));
            byte[] out = "{\"messageId\":\"x\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsTheVerifyLinkOverHttpsInsteadOfSmtp() throws Exception {
        JavaMailSender smtp = mock(JavaMailSender.class);
        EmailService email = emailService(smtp, brevo("key-123"));

        assertTrue(email.isConfigured());
        email.sendToken("manager@example.com", VerificationTokenPurpose.EMAIL_VERIFY, "tok");

        assertEquals(List.of("key-123"), keys);
        JsonNode sent = json.readTree(bodies.get(0));
        assertEquals("Kickabout", sent.at("/sender/name").asText());
        assertEquals("noreply@example.com", sent.at("/sender/email").asText());
        assertEquals("manager@example.com", sent.at("/to/0/email").asText());
        assertEquals("Verify your email for Kickabout", sent.at("/subject").asText());
        assertTrue(sent.at("/textContent").asText().contains("https://app.example.com/verify-email?token=tok"));
        verifyNoInteractions(smtp);
    }

    @Test
    void aRefusalIsLoggedNotThrown() {
        status = 401;
        EmailService email = emailService(null, brevo("bad-key"));

        assertDoesNotThrow(() -> email.send("manager@example.com", "Hello", "Body"));
        assertEquals(1, bodies.size());
    }

    @Test
    void withoutAKeyBrevoStaysOff() {
        assertFalse(brevo("").isConfigured());
        assertFalse(emailService(null, brevo("")).isConfigured());
    }

    @Test
    void readsANamedOrBareSender() {
        assertEquals(Map.of("name", "Kickabout", "email", "a@b.com"), BrevoMailer.sender("Kickabout <a@b.com>"));
        assertEquals(Map.of("email", "a@b.com"), BrevoMailer.sender("a@b.com"));
        assertEquals(Map.of("email", "a@b.com"), BrevoMailer.sender("<a@b.com>"));
    }

    private BrevoMailer brevo(String key) {
        return new BrevoMailer(key, "http://127.0.0.1:" + server.getAddress().getPort(), json);
    }

    @SuppressWarnings("unchecked")
    private static EmailService emailService(JavaMailSender smtp, BrevoMailer brevo) {
        ObjectProvider<JavaMailSender> smtpProvider = mock(ObjectProvider.class);
        when(smtpProvider.getIfAvailable()).thenReturn(smtp);
        ObjectProvider<BrevoMailer> brevoProvider = mock(ObjectProvider.class);
        when(brevoProvider.getIfAvailable()).thenReturn(brevo);
        return new EmailService(smtpProvider, brevoProvider, "Kickabout <noreply@example.com>", "https://app.example.com");
    }
}
