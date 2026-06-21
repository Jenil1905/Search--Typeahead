package com.example.typeahead.controller;

import com.example.typeahead.service.CacheStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * <h1>CacheStatsController</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This controller exposes endpoints to monitor cache performance (hit rate, hits, misses).
 * 
 * <h3>What problem it solves:</h3>
 * Provides real-time metrics to administrative consoles or frontends so developers can monitor
 * whether caching is working effectively.
 * 
 * <h3>How it works internally:</h3>
 * Maps requests to <code>/api/cache/stats</code> and calls {@link CacheStatsService}.
 */
@RestController
@RequestMapping("/api/cache")
public class CacheStatsController {

    private final CacheStatsService cacheStatsService;

    public CacheStatsController(CacheStatsService cacheStatsService) {
        this.cacheStatsService = cacheStatsService;
    }

    /**
     * Retrieves cache statistics.
     * Route: GET /api/cache/stats
     * 
     * @return Map containing hits, misses, total requests, and hitRate.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(cacheStatsService.getStats());
    }

    /**
     * Resets cache metrics back to zero.
     * Route: POST /api/cache/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetStats() {
        cacheStatsService.reset();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Cache statistics reset successfully");
        return ResponseEntity.ok(response);
    }
}
