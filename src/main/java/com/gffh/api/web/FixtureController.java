package com.gffh.api.web;

import com.gffh.api.domain.Fixture;
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
        var views = fixtureService.list(principal.getSubject(), teamId).stream()
                .map(this::toView)
                .toList();
        return ResponseEntity.ok(views);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FixtureDtos.FixtureView> get(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String id) {
        return ResponseEntity.ok(toView(fixtureService.get(principal.getSubject(), id)));
    }

    private FixtureDtos.FixtureView toView(Fixture fixture) {
        return FixtureDtos.FixtureView.from(fixture,
                fixtureService.team(fixture.homeTeamId()),
                fixtureService.team(fixture.awayTeamId()));
    }
}
