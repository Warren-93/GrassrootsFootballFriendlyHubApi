package com.gffh.api.web;

import com.gffh.api.domain.Team;
import com.gffh.api.service.MemberService;
import com.gffh.api.service.TeamReadinessService;
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
    private final MemberService memberService;
    private final TeamReadinessService readiness;

    public TeamController(TeamService teamService, MemberService memberService, TeamReadinessService readiness) {
        this.teamService = teamService;
        this.memberService = memberService;
        this.readiness = readiness;
    }

    private TeamDtos.TeamView view(Team team, boolean includeContact) {
        return TeamDtos.TeamView.from(team, includeContact, readiness.hasVenue(team));
    }

    @PostMapping
    public ResponseEntity<TeamDtos.TeamView> create(@AuthenticationPrincipal Jwt principal,
                                                     @Valid @RequestBody TeamDtos.CreateTeamRequest request) {
        Team team = teamService.create(principal.getSubject(), request);
        return ResponseEntity.ok(view(team, true));
    }

    @GetMapping
    public ResponseEntity<List<TeamDtos.TeamView>> listByClub(@AuthenticationPrincipal Jwt principal,
                                                               @RequestParam String clubId) {
        var views = teamService.listByClub(principal.getSubject(), clubId).stream()
                .map(t -> view(t, true)).toList();
        return ResponseEntity.ok(views);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<TeamDtos.TeamView>> listMine(@AuthenticationPrincipal Jwt principal) {
        String userId = principal.getSubject();
        var views = teamService.listMine(userId).stream()
                .map(t -> TeamDtos.TeamView.mine(t, teamService.canManage(userId, t.id()), readiness.hasVenue(t))).toList();
        return ResponseEntity.ok(views);
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamDtos.TeamView> get(@AuthenticationPrincipal Jwt principal,
                                                 @PathVariable String teamId) {
        String userId = principal.getSubject();
        Team team = teamService.get(userId, teamId);
        boolean canManage = teamService.canManage(userId, teamId);
        return ResponseEntity.ok(view(team, canManage));
    }

    @PatchMapping("/{teamId}")
    public ResponseEntity<TeamDtos.TeamView> update(@AuthenticationPrincipal Jwt principal,
                                                     @PathVariable String teamId,
                                                     @Valid @RequestBody TeamDtos.UpdateTeamRequest request) {
        Team team = teamService.update(principal.getSubject(), teamId, request);
        return ResponseEntity.ok(view(team, true));
    }

    @PostMapping("/{teamId}/archive")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt principal, @PathVariable String teamId) {
        teamService.archive(principal.getSubject(), teamId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{teamId}/privacy")
    public ResponseEntity<TeamDtos.PrivacyPreferences> getPrivacy(@AuthenticationPrincipal Jwt principal,
                                                                    @PathVariable String teamId) {
        return ResponseEntity.ok(teamService.getPrivacyPreferences(principal.getSubject(), teamId));
    }

    @PatchMapping("/{teamId}/privacy")
    public ResponseEntity<TeamDtos.PrivacyPreferences> updatePrivacy(
            @AuthenticationPrincipal Jwt principal, @PathVariable String teamId,
            @RequestBody TeamDtos.PrivacyPreferences request) {
        return ResponseEntity.ok(teamService.updatePrivacyPreferences(principal.getSubject(), teamId, request));
    }

    @PostMapping("/join")
    public ResponseEntity<MemberDtos.JoinResultView> join(@AuthenticationPrincipal Jwt principal,
                                                           @Valid @RequestBody MemberDtos.RedeemJoinCodeRequest request) {
        return ResponseEntity.ok(memberService.redeem(principal.getSubject(), request.code()));
    }
}
