package com.gffh.api.service.push;

import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.PushToken;
import com.gffh.api.model.enums.PushPlatform;
import com.gffh.api.repository.PushTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The devices each user has registered for push, and delivery to them
 * through whichever {@link PushSender}s are configured (FCM for Android,
 * APNs for iOS). A platform whose sender isn't configured is skipped
 * silently, so the clients can register tokens before the server has
 * credentials and start receiving pushes as soon as it does.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final Pattern APNS_TOKEN = Pattern.compile("[0-9a-f]{32,200}");

    private final PushTokenRepository tokens;
    private final Map<PushPlatform, PushSender> senders = new EnumMap<>(PushPlatform.class);

    public PushService(PushTokenRepository tokens, List<PushSender> senders) {
        this.tokens = tokens;
        senders.forEach(s -> this.senders.put(s.platform(), s));
    }

    /** Idempotent. A token already registered to someone else moves to {@code userId}. */
    public void register(String userId, PushPlatform platform, String rawToken) {
        String token = normalised(platform, rawToken);
        tokens.save(new PushToken(token, userId, platform, Instant.now()));
    }

    /** Forgets the device for this user (on sign-out). A token that isn't theirs is left alone. */
    public void unregister(String userId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        tokens.deleteForUser(userId, rawToken.trim());
        // An APNs token may have been registered in its normalised form.
        String apns = apnsForm(rawToken);
        if (!apns.equals(rawToken.trim())) tokens.deleteForUser(userId, apns);
    }

    public void deleteAllFor(String userId) {
        tokens.deleteByUserId(userId);
    }

    /** True when at least one platform can actually send. */
    public boolean isConfigured() {
        return senders.values().stream().anyMatch(PushSender::isConfigured);
    }

    /**
     * Sends {@code message} to every device {@code userId} has registered,
     * synchronously - callers run this off the request thread. Tokens the
     * provider reports as invalid are deleted. Never throws.
     */
    public void sendToUser(String userId, PushMessage message) {
        List<PushToken> devices;
        try {
            devices = tokens.findByUserId(userId);
        } catch (Exception e) {
            log.warn("Could not load push tokens for user {}", userId, e);
            return;
        }
        for (PushToken device : devices) {
            PushSender sender = senders.get(device.getPlatform());
            if (sender == null || !sender.isConfigured()) continue;
            try {
                if (sender.send(device.getToken(), message) == PushSender.Result.INVALID_TOKEN) {
                    tokens.delete(device.getToken());
                    log.info("Forgot a push token ({}) the provider reported as invalid", device.getPlatform());
                }
            } catch (Exception e) {
                log.warn("Could not push to a {} device for user {}", device.getPlatform(), userId, e);
            }
        }
    }

    /**
     * FCM tokens are kept exactly as given. APNs tokens are hex; spaces and
     * angle brackets (the old NSData description format) are stripped and the
     * result lower-cased, so one device can't be registered twice.
     */
    static String normalised(PushPlatform platform, String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (platform == PushPlatform.IOS) {
            token = apnsForm(token);
            if (!APNS_TOKEN.matcher(token).matches()) {
                throw new BusinessRuleException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST,
                        "token: must be an APNs device token in hex.");
            }
        }
        if (token.isEmpty()) {
            throw new BusinessRuleException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "token: must not be blank.");
        }
        return token;
    }

    private static String apnsForm(String token) {
        return token.replaceAll("[\\s<>]", "").toLowerCase(Locale.ROOT);
    }
}
