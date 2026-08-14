package com.gffh.api.web;

import com.gffh.api.service.ConversationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Admin conversations", description = "Read-only message transcript between two teams, for investigating a report")
public class AdminConversationController {

    private final ConversationService conversationService;

    public AdminConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/api/v1/admin/conversations/between")
    public ResponseEntity<List<ConversationDtos.MessageView>> between(@AuthenticationPrincipal Jwt principal,
                                                                        @RequestParam String teamAId,
                                                                        @RequestParam String teamBId) {
        return ResponseEntity.ok(conversationService.transcriptForAdmin(principal.getSubject(), teamAId, teamBId));
    }
}
