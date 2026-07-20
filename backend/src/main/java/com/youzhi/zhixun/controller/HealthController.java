package com.youzhi.zhixun.controller;

import com.youzhi.zhixun.retrieval.RagQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final RagQueryService ragQueryService;

    public HealthController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        String mode = ragQueryService.isReady() ? "REAL_EMBEDDING_RETRIEVAL" : "DEMO_FIXTURE";
        return Map.of("status", "UP", "mode", mode);
    }
}
