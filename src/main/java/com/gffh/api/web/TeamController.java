package com.gffh.api.web;

import com.gffh.api.domain.Team;
import com.gffh.api.service.TeamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Team profile creation and management")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<TeamDtos.TeamView> create(@AuthenticationPrincipal Jwt principal,
                                                     @Valid @RequestBody TeamDtos.CreateTeamRequest request) {
        Team team = teamService.create(principal.getSubject(), request);
        return ResponseEntity.ok(TeamDtos.TeamView.from(team));
    }

    @GetMapping
    public ResponseEntity<List<TeamDtos.TeamView>> listByClub(@AuthenticationPrincipal Jwt principal,
                                                               @RequestParam String clubId) {
        var views = teamService.listByClub(principal.getSubject(), clubId).stream()
                .map(t -> TeamDtos.TeamView.from(t, true)).toList();
        return ResponseEntity.ok(views);
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamDtos.TeamView> get(@AuthenticationPrincipal Jwt principal,
                                                 @PathVariable String teamId) {
        String userId = principal.getSubject();
        Team team = teamService.get(userId, teamId);
        boolean canManage = teamService.canManage(userId, teamId);
        return ResponseEntity.ok(TeamDtos.TeamView.from(team, canManage));
    }

    @PatchMapping("/{teamId}")
    public ResponseEntity<TeamDtos.TeamView> update(@AuthenticationPrincipal Jwt principal,
                                                     @PathVariable String teamId,
                                                     @RequestBody TeamDtos.UpdateTeamRequest request) {
        Team team = teamService.update(principal.getSubject(), teamId, request);
        return ResponseEntity.ok(TeamDtos.TeamView.from(team));
    }
}
