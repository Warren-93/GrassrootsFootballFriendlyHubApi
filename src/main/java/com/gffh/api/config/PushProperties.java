package com.gffh.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Push notification credentials. Every value is optional: each sender is
 * simply off until its own settings are present (see FcmSender, ApnsSender).
 */
@Component
@ConfigurationProperties(prefix = "gffh.push")
public class PushProperties {

    private final Fcm fcm = new Fcm();
    private final Apns apns = new Apns();

    public Fcm getFcm() { return fcm; }

    public Apns getApns() { return apns; }

    public static class Fcm {

        /** The Firebase service-account JSON, raw or base64. The project ID is read from it. */
        private String serviceAccountJson = "";

        public String getServiceAccountJson() { return serviceAccountJson; }
        public void setServiceAccountJson(String v) { this.serviceAccountJson = v == null ? "" : v.trim(); }
    }

    public static class Apns {

        /** The .p8 key's ID, from the Apple Developer account. */
        private String keyId = "";
        private String teamId = "";
        /** The .p8 file's contents (PEM). */
        private String privateKey = "";
        /** The iOS app's bundle ID, sent as the apns-topic. */
        private String bundleId = "";
        /** api.push.apple.com when true, the sandbox (development builds) otherwise. */
        private boolean production = false;

        public String getKeyId() { return keyId; }
        public void setKeyId(String v) { this.keyId = v == null ? "" : v.trim(); }

        public String getTeamId() { return teamId; }
        public void setTeamId(String v) { this.teamId = v == null ? "" : v.trim(); }

        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String v) { this.privateKey = v == null ? "" : v.trim(); }

        public String getBundleId() { return bundleId; }
        public void setBundleId(String v) { this.bundleId = v == null ? "" : v.trim(); }

        public boolean isProduction() { return production; }
        public void setProduction(boolean v) { this.production = v; }

        public boolean isComplete() {
            return !keyId.isBlank() && !teamId.isBlank() && !privateKey.isBlank() && !bundleId.isBlank();
        }
    }
}
