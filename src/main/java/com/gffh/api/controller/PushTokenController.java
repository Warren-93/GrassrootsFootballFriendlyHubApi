package com.gffh.api.controller;

import com.gffh.api.dto.PushTokenDtos;
import com.gffh.api.service.push.PushService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/push-tokens")
@Tag(name = "Push tokens", description = "Devices that receive push notifications for the signed-in user")
public class PushTokenController {

    private final PushService pushService;

    public PushTokenController(PushService pushService) {
        this.pushService = pushService;
    }

    /** Idempotent; call on every app start once the OS hands over a token. */
    @PostMapping
    public ResponseEntity<Void> register(@AuthenticationPrincipal Jwt principal,
                                         @Valid @RequestBody PushTokenDtos.RegisterPushTokenRequest request) {
        pushService.register(principal.getSubject(), request.platform(), request.token());
        return ResponseEntity.noContent().build();
    }

    /** Call on sign-out, before dropping the access token, so the device stops getting this account's pushes. */
    @DeleteMapping
    public ResponseEntity<Void> unregister(@AuthenticationPrincipal Jwt principal,
                                           @Valid @RequestBody PushTokenDtos.DeletePushTokenRequest request) {
        pushService.unregister(principal.getSubject(), request.token());
        return ResponseEntity.noContent().build();
    }
}
