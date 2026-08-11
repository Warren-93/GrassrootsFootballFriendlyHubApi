package com.gffh.api.web;

import com.gffh.api.domain.Venue;
import com.gffh.api.service.VenueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/venues")
@Tag(name = "Venues", description = "Club-scoped pitches available for home fixtures")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    public ResponseEntity<VenueDtos.VenueView> create(@AuthenticationPrincipal Jwt principal,
                                                       @Valid @RequestBody VenueDtos.CreateVenueRequest request) {
        return ResponseEntity.ok(VenueDtos.VenueView.from(venueService.create(principal.getSubject(), request)));
    }

    @GetMapping
    public ResponseEntity<List<VenueDtos.VenueView>> list(@AuthenticationPrincipal Jwt principal,
                                                           @RequestParam String clubId) {
        var views = venueService.list(principal.getSubject(), clubId).stream()
                .map(VenueDtos.VenueView::from).toList();
        return ResponseEntity.ok(views);
    }

    @PatchMapping("/{venueId}")
    public ResponseEntity<VenueDtos.VenueView> update(@AuthenticationPrincipal Jwt principal,
                                                       @PathVariable String venueId,
                                                       @RequestBody VenueDtos.UpdateVenueRequest request) {
        return ResponseEntity.ok(VenueDtos.VenueView.from(venueService.update(principal.getSubject(), venueId, request)));
    }

    @PostMapping("/{venueId}/default")
    public ResponseEntity<VenueDtos.VenueView> setDefault(@AuthenticationPrincipal Jwt principal,
                                                           @PathVariable String venueId) {
        return ResponseEntity.ok(VenueDtos.VenueView.from(venueService.setDefault(principal.getSubject(), venueId)));
    }

    @DeleteMapping("/{venueId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt principal, @PathVariable String venueId) {
        venueService.delete(principal.getSubject(), venueId);
        return ResponseEntity.noContent().build();
    }
}
