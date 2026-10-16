package com.gffh.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Google and Apple sign-in configuration. Each provider is simply off until
 * its IDs are set, and {@code GET /api/v1/config} tells the clients which
 * buttons to show.
 */
@Component
@ConfigurationProperties(prefix = "gffh.oauth")
public class OAuthProperties {

    private final Google google = new Google();
    private final Apple apple = new Apple();

    public Google getGoogle() { return google; }

    public Apple getApple() { return apple; }

    public static class Google {

        /** The OAuth Web client ID: the web button, Android's serverClientId, and an accepted audience. */
        private String clientId = "";
        /** More accepted audiences, e.g. an iOS client ID. */
        private List<String> additionalClientIds = new ArrayList<>();

        public String getClientId() { return clientId; }
        public void setClientId(String v) { this.clientId = v == null ? "" : v.trim(); }

        public List<String> getAdditionalClientIds() { return additionalClientIds; }
        public void setAdditionalClientIds(List<String> v) { this.additionalClientIds = v == null ? new ArrayList<>() : v; }

        public boolean isEnabled() { return !clientId.isBlank(); }

        /** Every audience a Google ID token may be issued for. */
        public List<String> audiences() {
            return nonBlank(Stream.concat(Stream.of(clientId), additionalClientIds.stream()));
        }
    }

    public static class Apple {

        /** The Services ID for web sign-in (an accepted audience). */
        private String servicesId = "";
        /** The iOS app's bundle ID (the audience of native iOS tokens). */
        private String bundleId = "";

        public String getServicesId() { return servicesId; }
        public void setServicesId(String v) { this.servicesId = v == null ? "" : v.trim(); }

        public String getBundleId() { return bundleId; }
        public void setBundleId(String v) { this.bundleId = v == null ? "" : v.trim(); }

        public boolean isEnabled() { return !servicesId.isBlank() || !bundleId.isBlank(); }

        /** Every audience an Apple identity token may be issued for. */
        public List<String> audiences() {
            return nonBlank(Stream.of(servicesId, bundleId));
        }
    }

    private static List<String> nonBlank(Stream<String> values) {
        return values.filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
    }
}
