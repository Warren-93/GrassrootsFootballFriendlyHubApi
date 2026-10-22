package com.gffh.api.controller.admin;

import com.gffh.api.dto.admin.AdminChatFilterDtos;
import com.gffh.api.service.moderation.ChatFilterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/chat-filter")
@Tag(name = "Admin chat filter", description = "The words the chat language filter masks with asterisks")
public class AdminChatFilterController {

    private final ChatFilterService chatFilterService;

    public AdminChatFilterController(ChatFilterService chatFilterService) {
        this.chatFilterService = chatFilterService;
    }

    @GetMapping
    public ResponseEntity<AdminChatFilterDtos.ChatFilterView> current() {
        return ResponseEntity.ok(AdminChatFilterDtos.ChatFilterView.from(chatFilterService.current()));
    }

    @PutMapping
    public ResponseEntity<AdminChatFilterDtos.ChatFilterView> update(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody AdminChatFilterDtos.UpdateChatFilterRequest request) {
        return ResponseEntity.ok(AdminChatFilterDtos.ChatFilterView.from(
                chatFilterService.update(principal.getSubject(), request.words())));
    }

    @PostMapping("/reset")
    public ResponseEntity<AdminChatFilterDtos.ChatFilterView> reset(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok(AdminChatFilterDtos.ChatFilterView.from(
                chatFilterService.resetToDefaults(principal.getSubject())));
    }

    @PostMapping("/preview")
    public ResponseEntity<AdminChatFilterDtos.PreviewView> preview(
            @Valid @RequestBody AdminChatFilterDtos.PreviewRequest request) {
        return ResponseEntity.ok(AdminChatFilterDtos.PreviewView.from(chatFilterService.preview(request.text())));
    }
}
