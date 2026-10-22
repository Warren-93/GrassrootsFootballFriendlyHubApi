package com.gffh.api.controller.admin;

import com.gffh.api.dto.admin.AdminAssociationDtos;
import com.gffh.api.service.AdminAssociationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/associations")
@Tag(name = "Admin associations", description = "Registered (verified) associations with their leagues and divisions")
public class AdminAssociationController {

    private final AdminAssociationService associations;

    public AdminAssociationController(AdminAssociationService associations) {
        this.associations = associations;
    }

    @GetMapping
    public ResponseEntity<List<AdminAssociationDtos.OrganisationView>> list() {
        return ResponseEntity.ok(associations.list());
    }

    @PostMapping
    public ResponseEntity<AdminAssociationDtos.OrganisationView> create(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody AdminAssociationDtos.OrganisationInput body) {
        return ResponseEntity.ok(associations.create(principal.getSubject(), body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminAssociationDtos.OrganisationView> update(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id,
            @Valid @RequestBody AdminAssociationDtos.OrganisationInput body) {
        return ResponseEntity.ok(associations.update(principal.getSubject(), id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt principal, @PathVariable String id) {
        associations.delete(principal.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leagues")
    public ResponseEntity<AdminAssociationDtos.OrganisationView> addLeague(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id,
            @Valid @RequestBody AdminAssociationDtos.LeagueInput body) {
        return ResponseEntity.ok(associations.addLeague(principal.getSubject(), id, body));
    }

    @PutMapping("/{id}/leagues/{leagueId}")
    public ResponseEntity<AdminAssociationDtos.OrganisationView> updateLeague(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id, @PathVariable String leagueId,
            @Valid @RequestBody AdminAssociationDtos.LeagueInput body) {
        return ResponseEntity.ok(associations.updateLeague(principal.getSubject(), id, leagueId, body));
    }

    @DeleteMapping("/{id}/leagues/{leagueId}")
    public ResponseEntity<AdminAssociationDtos.OrganisationView> deleteLeague(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id, @PathVariable String leagueId) {
        return ResponseEntity.ok(associations.deleteLeague(principal.getSubject(), id, leagueId));
    }
}
