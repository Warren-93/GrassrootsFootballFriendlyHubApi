package com.gffh.api.web;

import com.gffh.api.service.MemberInviteService;
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
    private final MemberInviteService inviteService;

    public MemberController(MemberService memberService, MemberInviteService inviteService) {
        this.memberService = memberService;
        this.inviteService = inviteService;
    }

    @GetMapping
    public ResponseEntity<List<MemberDtos.MemberView>> list(@AuthenticationPrincipal Jwt principal,
                                                             @PathVariable String teamId) {
        return ResponseEntity.ok(memberService.list(principal.getSubject(), teamId));
    }

    /** 200 with the new member for an existing account; 202 with the invite for an address with no account yet. */
    @PostMapping
    public ResponseEntity<?> add(@AuthenticationPrincipal Jwt principal,
                                 @PathVariable String teamId,
                                 @Valid @RequestBody MemberDtos.AddMemberRequest request) {
        MemberService.AddResult result = memberService.add(principal.getSubject(), teamId, request);
        if (result.invited()) {
            return ResponseEntity.accepted().body(MemberDtos.InvitedView.of(result.invite()));
        }
        return ResponseEntity.ok(result.member());
    }

    @GetMapping("/invites")
    public ResponseEntity<List<MemberDtos.InviteView>> listInvites(@AuthenticationPrincipal Jwt principal,
                                                                   @PathVariable String teamId) {
        return ResponseEntity.ok(inviteService.list(principal.getSubject(), teamId));
    }

    @DeleteMapping("/invites/{inviteId}")
    public ResponseEntity<Void> cancelInvite(@AuthenticationPrincipal Jwt principal,
                                             @PathVariable String teamId,
                                             @PathVariable String inviteId) {
        inviteService.cancel(principal.getSubject(), teamId, inviteId);
        return ResponseEntity.noContent().build();
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
