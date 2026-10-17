package com.gffh.api.controller;

import com.gffh.api.dto.FixtureDtos;
import com.gffh.api.model.Fixture;
import com.gffh.api.service.FixtureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fixtures")
@Tag(name = "Fixtures", description = "Confirmed friendlies, with contact and venue details")
public class FixtureController {

    private final FixtureService fixtureService;

    public FixtureController(FixtureService fixtureService) {
        this.fixtureService = fixtureService;
    }

    @GetMapping
    public ResponseEntity<List<FixtureDtos.FixtureView>> list(
            @AuthenticationPrincipal Jwt principal,
            @RequestParam String teamId) {
        String userId = principal.getSubject();
        var views = fixtureService.list(userId, teamId).stream()
                .map(f -> toView(userId, f))
                .toList();
        return ResponseEntity.ok(views);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FixtureDtos.FixtureView> get(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String id) {
        String userId = principal.getSubject();
        return ResponseEntity.ok(toView(userId, fixtureService.get(userId, id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<FixtureDtos.FixtureView> cancel(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String id,
            @RequestBody(required = false) FixtureDtos.CancelFixtureRequest request) {
        String reason = request != null ? request.reason() : null;
        String userId = principal.getSubject();
        return ResponseEntity.ok(toView(userId, fixtureService.cancel(userId, id, reason)));
    }

    /** Members who don't manage either team see the fixture without the managers' contact details. */
    private FixtureDtos.FixtureView toView(String userId, Fixture fixture) {
        return FixtureDtos.FixtureView.from(fixture,
                fixtureService.team(fixture.homeTeamId()),
                fixtureService.team(fixture.awayTeamId()),
                fixtureService.venueFor(fixture),
                fixtureService.managesEitherTeam(userId, fixture));
    }
}
