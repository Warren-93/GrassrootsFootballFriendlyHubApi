package com.gffh.api.web;

import com.gffh.api.service.MessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fixtures/{fixtureId}/messages")
@Tag(name = "Messages", description = "SCR-FX-05: coordinate logistics on a confirmed fixture")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ResponseEntity<List<MessageDtos.MessageView>> list(@AuthenticationPrincipal Jwt principal,
                                                                @PathVariable String fixtureId) {
        return ResponseEntity.ok(messageService.list(principal.getSubject(), fixtureId));
    }

    @PostMapping
    public ResponseEntity<MessageDtos.MessageView> send(@AuthenticationPrincipal Jwt principal,
                                                          @PathVariable String fixtureId,
                                                          @Valid @RequestBody MessageDtos.SendMessageRequest request) {
        return ResponseEntity.ok(messageService.send(principal.getSubject(), fixtureId, request));
    }
}
