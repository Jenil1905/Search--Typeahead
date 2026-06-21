package com.example.typeahead.controller;

import com.example.typeahead.service.SearchSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * <h1>SearchController</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This controller handles HTTP POST requests when a user submits a completed search query (clicks search or presses Enter).
 * 
 * <h3>What problem it solves:</h3>
 * It receives search events and sends them to the in-memory buffer service instead of writing directly to the database.
 * This guarantees low latency for the user (response returns in ~1-5ms) and protects Postgres from write spikes.
 * 
 * <h3>How it works internally:</h3>
 * <ul>
 *   <li>Maps POST requests to <code>/api/search</code>.</li>
 *   <li>Binds the JSON payload to the {@link SearchRequest} DTO.</li>
 *   <li>Invokes <code>searchSubmissionService.submitSearch()</code>, adding the query to our concurrent memory map.</li>
 *   <li>Returns a fast success payload: <code>{"message": "searched"}</code>.</li>
 * </ul>
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: Why do we use POST instead of GET for search submission?</b><br>
 *       A: GET is designed to be safe and idempotent (making a request doesn't change server state). Submitting a search
 *       *mutates* server state (increments search counts), hence POST is semantic and correct.</li>
 *   <li><b>Q: What is a DTO (Data Transfer Object)?</b><br>
 *       A: A DTO is a simple object used to transfer data between the client and server. It has no business logic
 *       and only contains fields, getters, and setters matching the request JSON structure.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    private final SearchSubmissionService searchSubmissionService;

    public SearchController(SearchSubmissionService searchSubmissionService) {
        this.searchSubmissionService = searchSubmissionService;
    }

    /**
     * Endpoint to submit a completed search.
     * Route: POST /api/search
     * Request body: { "query": "java" }
     * 
     * @param request DTO containing the query text.
     * @return Standard confirmation message.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> submitSearch(@RequestBody SearchRequest request) {
        String query = request.getQuery();
        logger.info("REST API Request: POST /api/search with query=[{}]", query);
        
        searchSubmissionService.submitSearch(query);

        Map<String, String> response = new HashMap<>();
        response.put("message", "searched");
        return ResponseEntity.ok(response);
    }

    /**
     * Data Transfer Object representing the search request payload.
     */
    public static class SearchRequest {
        private String query;

        public SearchRequest() {
        }

        public SearchRequest(String query) {
            this.query = query;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
}
