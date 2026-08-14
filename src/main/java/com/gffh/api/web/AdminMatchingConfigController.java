package com.gffh.api.web;

import com.gffh.api.service.MatchingConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/matching-config")
@Tag(name = "Admin matching config", description = "ADM-08: factor weight sliders, preview, publish with version note")
public class AdminMatchingConfigController {

    private final MatchingConfigService matchingConfigService;

    public AdminMatchingConfigController(MatchingConfigService matchingConfigService) {
        this.matchingConfigService = matchingConfigService;
    }

    @GetMapping("/current")
    public ResponseEntity<MatchingConfigDtos.CurrentWeightsView> current() {
        return ResponseEntity.ok(MatchingConfigDtos.CurrentWeightsView.from(matchingConfigService.current()));
    }

    @GetMapping("/versions")
    public ResponseEntity<List<MatchingConfigDtos.VersionView>> history() {
        return ResponseEntity.ok(matchingConfigService.history());
    }

    @PostMapping("/preview")
    public ResponseEntity<MatchDtos.SearchResponse> preview(@Valid @RequestBody MatchingConfigDtos.PreviewRequest request) {
        return ResponseEntity.ok(matchingConfigService.preview(request));
    }

    @PostMapping("/publish")
    public ResponseEntity<MatchingConfigDtos.VersionView> publish(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody MatchingConfigDtos.PublishRequest request) {
        return ResponseEntity.ok(matchingConfigService.publish(principal.getSubject(), request));
    }
}
