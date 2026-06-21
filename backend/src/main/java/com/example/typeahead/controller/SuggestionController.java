package com.example.typeahead.controller;

import com.example.typeahead.model.QueryEntry;
import com.example.typeahead.service.SuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <h1>SuggestionController</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This is the REST controller that exposes the HTTP GET endpoint for search autocomplete suggestions.
 * 
 * <h3>What problem it solves:</h3>
 * It acts as the gateway between the React frontend client and our backend services. It listens
 * for HTTP requests at <code>/api/suggestions</code>, validates inputs, and responds with JSON suggestions.
 * 
 * <h3>How it works internally:</h3>
 * <ul>
 *   <li>The <code>@RestController</code> annotation tells Spring Boot that this class handles REST requests
 *       and automatically serializes the returned object (a List of Strings) into JSON.</li>
 *   <li>The <code>@GetMapping</code> maps HTTP GET requests to the <code>getSuggestions</code> method.</li>
 *   <li>It delegates business logic to {@link SuggestionService}.</li>
 * </ul>
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: What is the difference between @Controller and @RestController?</b><br>
 *       A: <code>@Controller</code> is used for traditional MVC web applications that return views (HTML/JSP).
 *       <code>@RestController</code> is a specialized version that combines <code>@Controller</code> and
 *       <code>@ResponseBody</code>, meaning it returns data (JSON/XML) directly in the HTTP response body.</li>
 *   <li><b>Q: What is @RequestParam(name = "q", defaultValue = "")?</b><br>
 *       A: It binds an HTTP query parameter (e.g. <code>?q=java</code>) to a method parameter. The
 *       <code>defaultValue</code> prevents errors if the user calls the endpoint without typing anything.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private static final Logger logger = LoggerFactory.getLogger(SuggestionController.class);
    
    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /**
     * Endpoint to fetch search suggestions while the user types.
     * Route: GET /api/suggestions?q=<prefix>
     * 
     * @param query Search prefix typed by the user.
     * @return List of matching suggestions (maximum 10, sorted by popularity).
     */
    @GetMapping
    public ResponseEntity<List<String>> getSuggestions(@RequestParam(name = "q", defaultValue = "") String query) {
        logger.info("REST API Request: GET /api/suggestions?q=[{}]", query);
        List<String> suggestions = suggestionService.getSuggestions(query);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Endpoint to fetch the top 10 trending searches.
     * Route: GET /api/suggestions/trending
     * 
     * @return List of top QueryEntry objects containing counts and scores.
     */
    @GetMapping("/trending")
    public ResponseEntity<List<QueryEntry>> getTrending() {
        logger.info("REST API Request: GET /api/suggestions/trending");
        List<QueryEntry> trending = suggestionService.getTrending();
        return ResponseEntity.ok(trending);
    }
}
