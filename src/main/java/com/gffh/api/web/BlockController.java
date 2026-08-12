package com.gffh.api.web;

import com.gffh.api.service.BlockService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Blocks", description = "SCR-PR-11: a team blocking another - symmetric, invisible in search either way")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping("/api/v1/teams/{teamId}/blocks")
    public ResponseEntity<Void> block(@AuthenticationPrincipal Jwt principal, @PathVariable String teamId,
                                      @Valid @RequestBody BlockDtos.BlockRequest request) {
        blockService.block(principal.getSubject(), teamId, request);
        return ResponseEntity.ok().build();
    }
}
