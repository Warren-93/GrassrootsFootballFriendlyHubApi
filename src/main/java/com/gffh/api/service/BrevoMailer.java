package com.gffh.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends email through Brevo's HTTPS API instead of SMTP, for hosts that block
 * outbound SMTP ports (Render's free plan has since September 2025). On when
 * {@code BREVO_API_KEY} is set; {@code MAIL_FROM} must be a sender Brevo has
 * verified (a single address is enough, no domain needed).
 */
@Component
public class BrevoMailer {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** "Kickabout <noreply@example.com>" or a bare address. */
    private static final Pattern NAMED_ADDRESS = Pattern.compile("^\\s*(.*?)\\s*<([^>]+)>\\s*$");

    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper json;
    private final HttpClient http;

    public BrevoMailer(@Value("${gffh.mail.brevo.api-key:}") String apiKey,
                       @Value("${gffh.mail.brevo.base-url:https://api.brevo.com}") String baseUrl,
                       ObjectMapper json) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /** Throws {@link IOException} when Brevo can't be reached or refuses the email. */
    public void send(String from, String to, String subject, String text) throws IOException {
        String body = json.writeValueAsString(Map.of(
                "sender", sender(from),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", text));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v3/smtp/email"))
                .timeout(TIMEOUT)
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        if (response.statusCode() / 100 != 2) {
            String detail = response.body() == null ? "" : response.body();
            throw new IOException("Brevo answered HTTP " + response.statusCode() + " "
                    + detail.substring(0, Math.min(200, detail.length())));
        }
    }

    static Map<String, String> sender(String from) {
        Matcher named = NAMED_ADDRESS.matcher(from);
        if (named.matches() && !named.group(1).isBlank()) {
            return Map.of("name", named.group(1).replaceAll("^\"|\"$", ""), "email", named.group(2).trim());
        }
        return Map.of("email", named.matches() ? named.group(2).trim() : from.trim());
    }
}
