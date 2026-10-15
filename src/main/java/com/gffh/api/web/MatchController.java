package com.gffh.api.web;

import com.gffh.api.service.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/matches")
@Tag(name = "Matching", description = "Opponent discovery and compatibility scoring")
public class MatchController {

    private final MatchingService matchingService;

    public MatchController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping("/search")
    @Operation(summary = "Find compatible opponents",
            description = "Returns every opponent with the same age group and gender, ranked "
                    + "by a compatibility score with server-generated reasons. Ability, format, "
                    + "distance and shared availability affect the ranking, not who is shown. "
                    + "The optional filters are kept for older clients.")
    public ResponseEntity<MatchDtos.SearchResponse> search(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody MatchDtos.SearchRequest request) {

        // Authorisation is enforced in the service against team membership, not
        // here: section 7 of the Technical Specification requires server-side
        // checks on every protected resource, and a controller annotation
        // cannot express "is a manager of this particular team".
        return ResponseEntity.ok(
                matchingService.search(principal.getSubject(), request));
    }
}
