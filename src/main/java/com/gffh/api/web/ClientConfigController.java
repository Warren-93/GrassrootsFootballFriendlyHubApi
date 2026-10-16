package com.gffh.api.web;

import com.gffh.api.service.ClientConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Public (no sign-in needed) and cacheable for five minutes - see {@link ClientConfigService}. */
@RestController
@Tag(name = "Config", description = "Public client configuration: support email and sign-in providers")
public class ClientConfigController {

    private final ClientConfigService clientConfigService;

    public ClientConfigController(ClientConfigService clientConfigService) {
        this.clientConfigService = clientConfigService;
    }

    @GetMapping("/api/v1/config")
    public ResponseEntity<ClientConfigDtos.ClientConfigView> config() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(clientConfigService.current());
    }
}
