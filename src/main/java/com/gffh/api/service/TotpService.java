package com.gffh.api.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * RFC 6238 TOTP (SHA-1, 6 digits, 30-second step) - platform admin sign-in's
 * mandatory second factor (ADM-01). Implemented directly rather than pulling
 * in a dependency: the algorithm is small, stable and doesn't change.
 */
@Service
public class TotpService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final String HMAC_ALGO = "HmacSHA1";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[20]; // 160-bit, per RFC 4226's recommendation
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String provisioningUri(String secret, String accountEmail, String issuer) {
        return "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d".formatted(
                urlEncode(issuer), urlEncode(accountEmail), secret, urlEncode(issuer),
                CODE_DIGITS, TIME_STEP_SECONDS);
    }

    /** Allows the previous and next time step too, to tolerate clock drift. */
    public boolean verifyCode(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (generateCode(secret, step).equals(code)) return true;
        }
        return false;
    }

    private String generateCode(String base32Secret, long timeStep) {
        byte[] key = base32Decode(base32Secret);
        byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int bits = 0, value = 0;
        for (byte b : data) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32_ALPHABET.charAt((value >>> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32_ALPHABET.charAt((value << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    private byte[] base32Decode(String encoded) {
        String clean = encoded.trim().toUpperCase().replace("=", "");
        int bits = 0, value = 0, index = 0;
        byte[] output = new byte[clean.length() * 5 / 8];
        for (char c : clean.toCharArray()) {
            value = (value << 5) | BASE32_ALPHABET.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                output[index++] = (byte) ((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return output;
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
