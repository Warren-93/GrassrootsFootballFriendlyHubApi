package com.gffh.api.web;

import com.gffh.api.domain.Club;
import com.gffh.api.service.ClubService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clubs")
@Tag(name = "Clubs", description = "Club creation and management")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @PostMapping
    public ResponseEntity<ClubDtos.ClubView> create(@AuthenticationPrincipal Jwt principal,
                                                     @Valid @RequestBody ClubDtos.CreateClubRequest request) {
        Club club = clubService.create(principal.getSubject(), request);
        return ResponseEntity.ok(ClubDtos.ClubView.from(club));
    }

    @GetMapping("/{clubId}")
    public ResponseEntity<ClubDtos.ClubView> get(@AuthenticationPrincipal Jwt principal,
                                                 @PathVariable String clubId) {
        return ResponseEntity.ok(ClubDtos.ClubView.from(clubService.get(principal.getSubject(), clubId)));
    }

    @PatchMapping("/{clubId}")
    public ResponseEntity<ClubDtos.ClubView> update(@AuthenticationPrincipal Jwt principal,
                                                     @PathVariable String clubId,
                                                     @RequestBody ClubDtos.UpdateClubRequest request) {
        Club club = clubService.update(principal.getSubject(), clubId, request);
        return ResponseEntity.ok(ClubDtos.ClubView.from(club));
    }
}
