package com.gffh.api.web;

import com.gffh.api.service.ConversationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Conversations", description = "Team-to-team chat, available as soon as a team has published availability")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/api/v1/conversations")
    public ResponseEntity<ConversationDtos.ConversationView> start(@AuthenticationPrincipal Jwt principal,
                                                                    @Valid @RequestBody ConversationDtos.StartConversationRequest request) {
        return ResponseEntity.ok(conversationService.start(principal.getSubject(), request));
    }

    @GetMapping("/api/v1/teams/{teamId}/conversations")
    public ResponseEntity<List<ConversationDtos.ConversationView>> list(@AuthenticationPrincipal Jwt principal,
                                                                         @PathVariable String teamId) {
        return ResponseEntity.ok(conversationService.list(principal.getSubject(), teamId));
    }

    @GetMapping("/api/v1/conversations/{conversationId}")
    public ResponseEntity<ConversationDtos.ConversationView> get(@AuthenticationPrincipal Jwt principal,
                                                                  @PathVariable String conversationId) {
        return ResponseEntity.ok(conversationService.get(principal.getSubject(), conversationId));
    }

    @GetMapping("/api/v1/conversations/{conversationId}/messages")
    public ResponseEntity<List<ConversationDtos.MessageView>> messages(@AuthenticationPrincipal Jwt principal,
                                                                        @PathVariable String conversationId) {
        return ResponseEntity.ok(conversationService.messages(principal.getSubject(), conversationId));
    }

    @PostMapping("/api/v1/conversations/{conversationId}/messages")
    public ResponseEntity<ConversationDtos.MessageView> send(@AuthenticationPrincipal Jwt principal,
                                                              @PathVariable String conversationId,
                                                              @Valid @RequestBody ConversationDtos.SendMessageRequest request) {
        return ResponseEntity.ok(conversationService.send(principal.getSubject(), conversationId, request));
    }
}
