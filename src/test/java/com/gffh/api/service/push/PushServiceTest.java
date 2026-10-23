package com.gffh.api.service.push;

import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.PushToken;
import com.gffh.api.model.enums.PushPlatform;
import com.gffh.api.repository.PushTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PushServiceTest {

    private static final String APNS = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";

    private final PushTokenRepository tokens = mock(PushTokenRepository.class);
    private final FakeSender android = new FakeSender(PushPlatform.ANDROID, true);
    private final FakeSender ios = new FakeSender(PushPlatform.IOS, true);
    private final PushMessage message = new PushMessage("Title", "Body", Map.of("type", "REQUEST_RECEIVED"));

    @Test
    void sendsToEveryDeviceAndForgetsTokensTheProviderRejects() {
        when(tokens.findByUserId("u1")).thenReturn(List.of(
                token("fcm-ok", PushPlatform.ANDROID), token("fcm-gone", PushPlatform.ANDROID),
                token(APNS, PushPlatform.IOS)));
        android.invalid.add("fcm-gone");

        new PushService(tokens, List.of(android, ios)).sendToUser("u1", message);

        assertEquals(List.of("fcm-ok", "fcm-gone"), android.sentTo);
        assertEquals(List.of(APNS), ios.sentTo);
        verify(tokens).deleteById("fcm-gone");
        verify(tokens, never()).deleteById("fcm-ok");
    }

    @Test
    void anUnconfiguredPlatformIsSkippedAndAFailureKeepsTheToken() {
        FakeSender iosOff = new FakeSender(PushPlatform.IOS, false);
        android.failing = true;
        when(tokens.findByUserId("u1")).thenReturn(List.of(
                token("fcm-1", PushPlatform.ANDROID), token(APNS, PushPlatform.IOS)));
        PushService service = new PushService(tokens, List.of(android, iosOff));

        assertDoesNotThrow(() -> service.sendToUser("u1", message));
        assertTrue(iosOff.sentTo.isEmpty());
        verify(tokens, never()).delete(any());
        assertTrue(service.isConfigured());
        assertFalse(new PushService(tokens, List.of(iosOff)).isConfigured());
    }

    @Test
    void iosTokensAreNormalisedToLowerCaseHexAndAndroidTokensKeptAsGiven() {
        PushService service = new PushService(tokens, List.of(android, ios));

        service.register("u1", PushPlatform.IOS, "<" + APNS.toUpperCase().substring(0, 8) + " " + APNS.substring(8) + ">");
        service.register("u1", PushPlatform.ANDROID, "  dXyZ:APA91b-Token_1  ");

        ArgumentCaptor<PushToken> saved = ArgumentCaptor.forClass(PushToken.class);
        verify(tokens, times(2)).save(saved.capture());
        assertEquals(APNS, saved.getAllValues().get(0).getToken());
        assertEquals("dXyZ:APA91b-Token_1", saved.getAllValues().get(1).getToken());
        assertEquals("u1", saved.getAllValues().get(1).getUserId());
    }

    @Test
    void aNonHexIosTokenIsRejected() {
        PushService service = new PushService(tokens, List.of(android, ios));

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> service.register("u1", PushPlatform.IOS, "dXyZ:APA91b-an-fcm-token"));
        assertEquals("VALIDATION_FAILED", e.code());
        verify(tokens, never()).save(any());
    }

    private static PushToken token(String value, PushPlatform platform) {
        return new PushToken(value, "u1", platform, Instant.now());
    }

    private static final class FakeSender implements PushSender {
        private final PushPlatform platform;
        private final boolean configured;
        final List<String> sentTo = new ArrayList<>();
        final List<String> invalid = new ArrayList<>();
        boolean failing;

        FakeSender(PushPlatform platform, boolean configured) {
            this.platform = platform;
            this.configured = configured;
        }

        @Override
        public PushPlatform platform() {
            return platform;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public Result send(String deviceToken, PushMessage message) {
            sentTo.add(deviceToken);
            if (failing) return Result.FAILED;
            return invalid.contains(deviceToken) ? Result.INVALID_TOKEN : Result.SENT;
        }
    }
}
