package com.gffh.api.web;

import com.gffh.api.service.TestAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/test-accounts")
@Tag(name = "Admin test accounts", description = "Generate and remove test accounts, tracked in their own collection")
public class AdminTestAccountController {

    private final TestAccountService testAccountService;

    public AdminTestAccountController(TestAccountService testAccountService) {
        this.testAccountService = testAccountService;
    }

    @GetMapping
    public ResponseEntity<List<AdminTestAccountDtos.TestAccountView>> list() {
        return ResponseEntity.ok(testAccountService.list());
    }

    @PostMapping
    public ResponseEntity<List<AdminTestAccountDtos.TestAccountView>> create(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody AdminTestAccountDtos.CreateRequest request) {
        return ResponseEntity.ok(testAccountService.create(principal.getSubject(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt principal, @PathVariable String id) {
        testAccountService.delete(principal.getSubject(), id);
        return ResponseEntity.noContent().build();
    }
}
