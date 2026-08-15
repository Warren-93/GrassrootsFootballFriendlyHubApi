package com.gffh.api.web;

import com.gffh.api.service.NotificationPreferenceService;
import com.gffh.api.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Notifications", description = "SCR-HM-02 notification centre and SCR-PR-09 preferences")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    public NotificationController(NotificationService notificationService, NotificationPreferenceService preferenceService) {
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
    }

    @GetMapping("/api/v1/notifications")
    public ResponseEntity<List<NotificationDtos.NotificationView>> list(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok(notificationService.list(principal.getSubject()));
    }

    @GetMapping("/api/v1/notifications/unread-count")
    public ResponseEntity<NotificationDtos.UnreadCountView> unreadCount(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok(new NotificationDtos.UnreadCountView(notificationService.unreadCount(principal.getSubject())));
    }

    @PostMapping("/api/v1/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt principal, @PathVariable String id) {
        notificationService.markRead(principal.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/notifications/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt principal) {
        notificationService.markAllRead(principal.getSubject());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/notifications")
    public ResponseEntity<Void> clearAll(@AuthenticationPrincipal Jwt principal) {
        notificationService.clearAll(principal.getSubject());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/me/notification-preferences")
    public ResponseEntity<NotificationDtos.PreferenceView> getPreferences(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok(preferenceService.get(principal.getSubject()));
    }

    @PatchMapping("/api/v1/me/notification-preferences")
    public ResponseEntity<NotificationDtos.PreferenceView> updatePreferences(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody NotificationDtos.UpdatePreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.update(principal.getSubject(), request));
    }
}
