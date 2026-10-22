package com.gffh.api.controller;

import com.gffh.api.dto.GifDtos;
import com.gffh.api.service.gif.GifService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gifs")
@Tag(name = "GIFs", description = "GIF search for the chat's GIF picker, proxied to GIPHY so the API key stays on the server")
public class GifController {

    private final GifService gifService;

    public GifController(GifService gifService) {
        this.gifService = gifService;
    }

    @GetMapping("/trending")
    public ResponseEntity<GifDtos.GifResults> trending(@RequestParam(defaultValue = "24") int limit) {
        return ResponseEntity.ok(GifDtos.GifResults.of(gifService.trending(limit)));
    }

    @GetMapping("/search")
    public ResponseEntity<GifDtos.GifResults> search(@RequestParam(defaultValue = "") String q,
                                                     @RequestParam(defaultValue = "24") int limit) {
        return ResponseEntity.ok(GifDtos.GifResults.of(gifService.search(q, limit)));
    }
}
