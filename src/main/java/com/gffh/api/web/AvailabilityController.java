package com.gffh.api.web;

import com.gffh.api.service.AvailabilityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/teams/{teamId}/availability")
@Tag(name = "Availability", description = "Publishing and withdrawing a team's playable windows")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PostMapping
    public ResponseEntity<AvailabilityDtos.SlotView> create(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String teamId,
            @Valid @RequestBody AvailabilityDtos.CreateSlotRequest request) {
        var slot = availabilityService.create(principal.getSubject(), teamId, request);
        return ResponseEntity.ok(AvailabilityDtos.SlotView.from(slot));
    }

    @GetMapping
    public ResponseEntity<List<AvailabilityDtos.SlotView>> list(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String teamId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var slots = availabilityService.list(principal.getSubject(), teamId, from, to).stream()
                .map(AvailabilityDtos.SlotView::from)
                .toList();
        return ResponseEntity.ok(slots);
    }

    @GetMapping("/{slotId}")
    public ResponseEntity<AvailabilityDtos.SlotView> get(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String teamId,
            @PathVariable String slotId) {
        var slot = availabilityService.get(principal.getSubject(), teamId, slotId);
        return ResponseEntity.ok(AvailabilityDtos.SlotView.from(slot));
    }

    @PutMapping("/{slotId}")
    public ResponseEntity<AvailabilityDtos.SlotView> update(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String teamId,
            @PathVariable String slotId,
            @Valid @RequestBody AvailabilityDtos.CreateSlotRequest request) {
        var slot = availabilityService.update(principal.getSubject(), teamId, slotId, request);
        return ResponseEntity.ok(AvailabilityDtos.SlotView.from(slot));
    }

    @DeleteMapping("/{slotId}")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String teamId,
            @PathVariable String slotId) {
        availabilityService.withdraw(principal.getSubject(), teamId, slotId);
        return ResponseEntity.noContent().build();
    }
}
