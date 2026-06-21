package com.example.typeahead.controller;

import com.example.typeahead.service.ConsistentHashingService;
import com.example.typeahead.service.SearchSubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h1>ConsistentHashingController</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This controller exposes endpoints to inspect the state of the consistent hashing ring, route specific keys,
 * dynamically add/remove cache nodes, and view the current in-memory search buffer.
 * 
 * <h3>What problem it solves:</h3>
 * It makes the abstract consistent hashing ring visible and interactive. Instead of just reading console logs,
 * the React frontend can query these endpoints to render the ring, trace query routing clockwise,
 * and dynamically simulate scale-out/scale-in events (adding/removing nodes).
 */
@RestController
@RequestMapping("/api/hashing")
public class ConsistentHashingController {

    private final ConsistentHashingService consistentHashingService;
    private final SearchSubmissionService searchSubmissionService;

    public ConsistentHashingController(ConsistentHashingService consistentHashingService,
                                         SearchSubmissionService searchSubmissionService) {
        this.consistentHashingService = consistentHashingService;
        this.searchSubmissionService = searchSubmissionService;
    }

    /**
     * Gets the full layout of virtual nodes on the ring.
     * Route: GET /api/hashing/ring
     */
    @GetMapping("/ring")
    public ResponseEntity<List<ConsistentHashingService.VirtualNodeInfo>> getRingState() {
        return ResponseEntity.ok(consistentHashingService.getRingState());
    }

    /**
     * Gets list of active physical nodes.
     * Route: GET /api/hashing/nodes
     */
    @GetMapping("/nodes")
    public ResponseEntity<List<String>> getActiveNodes() {
        return ResponseEntity.ok(consistentHashingService.getActiveNodes());
    }

    /**
     * Dynamically registers a new physical Redis cache node.
     * Route: POST /api/hashing/nodes?name=RedisNode4
     */
    @PostMapping("/nodes")
    public ResponseEntity<List<String>> addNode(@RequestParam(name = "name") String nodeName) {
        consistentHashingService.addNode(nodeName);
        return ResponseEntity.ok(consistentHashingService.getActiveNodes());
    }

    /**
     * Dynamically de-registers a physical Redis cache node.
     * Route: DELETE /api/hashing/nodes?name=RedisNode3
     */
    @DeleteMapping("/nodes")
    public ResponseEntity<List<String>> removeNode(@RequestParam(name = "name") String nodeName) {
        consistentHashingService.removeNode(nodeName);
        return ResponseEntity.ok(consistentHashingService.getActiveNodes());
    }

    /**
     * Traces the hash value of a key and returns the physical node it maps to.
     * Route: GET /api/hashing/route?q=java
     */
    @GetMapping("/route")
    public ResponseEntity<Map<String, Object>> routeKey(@RequestParam(name = "q") String key) {
        String node = consistentHashingService.routeKey(key);
        
        Map<String, Object> result = new HashMap<>();
        result.put("query", key);
        result.put("mappedNode", node);
        return ResponseEntity.ok(result);
    }

    /**
     * Exposes the current aggregated in-memory search buffer before it is flushed.
     * Route: GET /api/hashing/buffer
     */
    @GetMapping("/buffer")
    public ResponseEntity<Map<String, Long>> getBufferState() {
        return ResponseEntity.ok(searchSubmissionService.getBufferSnapshot());
    }
}
