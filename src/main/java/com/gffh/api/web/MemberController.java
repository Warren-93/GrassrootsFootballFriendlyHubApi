package com.gffh.api.web;

import com.gffh.api.service.MemberService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams/{teamId}/members")
@Tag(name = "Members", description = "SCR-PR-04: who can act on behalf of a team")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ResponseEntity<List<MemberDtos.MemberView>> list(@AuthenticationPrincipal Jwt principal,
                                                             @PathVariable String teamId) {
        return ResponseEntity.ok(memberService.list(principal.getSubject(), teamId));
    }

    @PostMapping
    public ResponseEntity<MemberDtos.MemberView> add(@AuthenticationPrincipal Jwt principal,
                                                      @PathVariable String teamId,
                                                      @Valid @RequestBody MemberDtos.AddMemberRequest request) {
        return ResponseEntity.ok(memberService.add(principal.getSubject(), teamId, request));
    }

    @PatchMapping("/{membershipId}")
    public ResponseEntity<MemberDtos.MemberView> updateRole(@AuthenticationPrincipal Jwt principal,
                                                             @PathVariable String teamId,
                                                             @PathVariable String membershipId,
                                                             @Valid @RequestBody MemberDtos.UpdateRoleRequest request) {
        return ResponseEntity.ok(memberService.updateRole(principal.getSubject(), teamId, membershipId, request));
    }

    @DeleteMapping("/{membershipId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt principal,
                                       @PathVariable String teamId,
                                       @PathVariable String membershipId) {
        memberService.remove(principal.getSubject(), teamId, membershipId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/join-code")
    public ResponseEntity<MemberDtos.JoinCodeView> joinCode(@AuthenticationPrincipal Jwt principal,
                                                            @PathVariable String teamId) {
        return ResponseEntity.ok(new MemberDtos.JoinCodeView(
                memberService.getOrCreateJoinCode(principal.getSubject(), teamId)));
    }

    @PostMapping("/join-code/regenerate")
    public ResponseEntity<MemberDtos.JoinCodeView> regenerateJoinCode(@AuthenticationPrincipal Jwt principal,
                                                                       @PathVariable String teamId) {
        return ResponseEntity.ok(new MemberDtos.JoinCodeView(
                memberService.regenerateJoinCode(principal.getSubject(), teamId)));
    }
}
