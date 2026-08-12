package com.gffh.api.web;

import com.gffh.api.domain.FriendlyRequest;
import com.gffh.api.service.FriendlyRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/friendly-requests")
@Tag(name = "Friendly requests", description = "Negotiating and confirming fixtures between teams")
public class FriendlyRequestController {

    private final FriendlyRequestService friendlyRequestService;

    public FriendlyRequestController(FriendlyRequestService friendlyRequestService) {
        this.friendlyRequestService = friendlyRequestService;
    }

    @PostMapping
    public ResponseEntity<FriendlyRequestDtos.FriendlyRequestView> send(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody FriendlyRequestDtos.SendRequest request) {
        FriendlyRequest fr = friendlyRequestService.send(principal.getSubject(), request);
        return ResponseEntity.ok(view(fr, principal.getSubject()));
    }

    @PostMapping("/{id}/actions/{action}")
    public ResponseEntity<FriendlyRequestDtos.FriendlyRequestView> act(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String id,
            @PathVariable String action,
            @RequestBody(required = false) FriendlyRequestDtos.ActionRequest request) {
        String reason = request != null ? request.reason() : null;
        FriendlyRequest fr = friendlyRequestService.act(principal.getSubject(), id, action, reason);
        return ResponseEntity.ok(view(fr, principal.getSubject()));
    }

    @GetMapping
    public ResponseEntity<List<FriendlyRequestDtos.FriendlyRequestView>> list(
            @AuthenticationPrincipal Jwt principal,
            @RequestParam String teamId) {
        var views = friendlyRequestService.list(principal.getSubject(), teamId).stream()
                .map(fr -> view(fr, principal.getSubject()))
                .toList();
        return ResponseEntity.ok(views);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FriendlyRequestDtos.FriendlyRequestView> get(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String id) {
        FriendlyRequest fr = friendlyRequestService.get(principal.getSubject(), id);
        return ResponseEntity.ok(view(fr, principal.getSubject()));
    }

    private FriendlyRequestDtos.FriendlyRequestView view(FriendlyRequest fr, String userId) {
        boolean isSender = friendlyRequestService.canManage(userId, fr.senderTeamId());

        var senderContact = friendlyRequestService.team(fr.senderTeamId())
                .map(t -> new FriendlyRequestDtos.TeamContact(t.id(), t.managerName(), t.contactPhone(), t.defaultVenueId()))
                .orElse(null);
        var recipientContact = friendlyRequestService.team(fr.recipientTeamId())
                .map(t -> new FriendlyRequestDtos.TeamContact(t.id(), t.managerName(), t.contactPhone(), t.defaultVenueId()))
                .orElse(null);

        return FriendlyRequestDtos.FriendlyRequestView.from(fr, isSender, senderContact, recipientContact);
    }
}
