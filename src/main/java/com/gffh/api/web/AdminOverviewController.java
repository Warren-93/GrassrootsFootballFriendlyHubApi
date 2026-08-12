package com.gffh.api.web;

import com.gffh.api.service.AdminOverviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/overview")
@Tag(name = "Admin overview", description = "ADM-02: verification backlog, report queue depth, weekly activity, error rate")
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    public AdminOverviewController(AdminOverviewService adminOverviewService) {
        this.adminOverviewService = adminOverviewService;
    }

    @GetMapping
    public ResponseEntity<AdminOverviewDtos.OverviewResponse> get() {
        return ResponseEntity.ok(adminOverviewService.get());
    }
}
