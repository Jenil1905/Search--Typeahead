package com.example.typeahead.service;

import com.example.typeahead.model.QueryEntry;
import com.example.typeahead.repository.QueryEntryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <h1>SuggestionService</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This service coordinates typeahead autocomplete queries. It checks the cache first (routed via Consistent Hashing)
 * and falls back to PostgreSQL on a cache miss.
 * 
 * <h3>What problem it solves:</h3>
 * <ul>
 *   <li><b>Low Latency Autocomplete</b>: Suggestions must load within 10-50ms of a keystroke.
 *       Querying the DB on every single keystroke is slow and resource-heavy. We solve this using the
 *       <b>Cache-Aside Pattern</b> (also known as Lazy Loading).</li>
 *   <li><b>Consistent Cache Routing</b>: By routing keys through the Consistent Hashing ring, we ensure
 *       a specific search prefix is always cached on a designated cache node (simulated here by prefix).</li>
 * </ul>
 * 
 * <h3>How it works internally (Cache-Aside Flow):</h3>
 * <ol>
 *   <li>The client requests suggestions for a prefix (e.g., "ja").</li>
 *   <li>We map "ja" to a Redis node (e.g., <code>RedisNode2</code>) using the <code>ConsistentHashingService</code>.</li>
 *   <li>We construct a Redis key: <code>simulated_node:RedisNode2:suggestions:ja</code>.</li>
 *   <li>We attempt to retrieve the value from Redis. If found (<b>Cache Hit</b>), we deserialize the JSON list and return.</li>
 *   <li>If not found (<b>Cache Miss</b>), we query the database using the case-insensitive prefix match repository method.</li>
 *   <li>We save the database results as a JSON string in Redis with a 5-minute TTL, and return them.</li>
 * </ol>
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: Describe Cache-Aside (Lazy Loading) advantages and disadvantages.</b><br>
 *       A: 
 *       <b>Advantages:</b> Only data actually requested is cached. Cache misses are handled gracefully. Node failures aren't fatal.
 *       <b>Disadvantages:</b> Cache miss penalty (the first query is slower). Stale data if the database is modified and cache is not invalidated.</li>
 *   <li><b>Q: Why do we serialize the list to JSON instead of using Java Serialization?</b><br>
 *       A: Java serialization is language-dependent, slower, and easily broken by class changes. JSON is standard,
 *       language-agnostic, and readable in the Redis CLI.</li>
 * </ol>
 */
@Service
public class SuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(SuggestionService.class);

    private final QueryEntryRepository queryEntryRepository;
    private final ConsistentHashingService consistentHashingService;
    private final CacheStatsService cacheStatsService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper; // For JSON serialization/deserialization

    public SuggestionService(QueryEntryRepository queryEntryRepository,
                             ConsistentHashingService consistentHashingService,
                             CacheStatsService cacheStatsService,
                             RedisTemplate<String, String> redisTemplate,
                             ObjectMapper objectMapper) {
        this.queryEntryRepository = queryEntryRepository;
        this.consistentHashingService = consistentHashingService;
        this.cacheStatsService = cacheStatsService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch suggestion strings matching a prefix, sorting by popularity.
     * 
     * @param prefix String typed by the user.
     * @return List of up to 10 matching query suggestions.
     */
    public List<String> getSuggestions(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String cleanPrefix = prefix.trim().toLowerCase();

        // 1. Consistent Hashing: Locate responsible cache node
        String targetNode = consistentHashingService.routeKey(cleanPrefix);
        String cacheKey = String.format("simulated_node:%s:suggestions:%s", targetNode, cleanPrefix);

        // 2. Cache-Aside Pattern: Try to read from cache (wrapped in try-catch for fault tolerance)
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                // CACHE HIT
                cacheStatsService.recordHit();
                logger.info("Cache HIT: Suggestions for [{}] retrieved from simulated [{}] in Redis", cleanPrefix, targetNode);
                
                // Deserialize JSON list to Java List
                return objectMapper.readValue(cachedJson, new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            // Fault Tolerance: If Redis fails, log it and proceed to Database fallback (Graceful Degradation)
            logger.error("Redis connection failed! Falling back directly to PostgreSQL. Error: {}", e.getMessage());
        }

        // CACHE MISS
        cacheStatsService.recordMiss();
        logger.info("Cache MISS: Suggestions for [{}] not in cache. Querying PostgreSQL database...", cleanPrefix);

        // 3. Fallback to Database: Fetch top 10 ranked matching queries
        List<QueryEntry> dbResults = queryEntryRepository.findSuggestions(cleanPrefix, PageRequest.of(0, 10));
        
        List<String> suggestions = dbResults.stream()
                .map(QueryEntry::getQueryText)
                .collect(Collectors.toList());

        // 4. Cache Populate: Save results to Redis for future cache hits
        try {
            String jsonToCache = objectMapper.writeValueAsString(suggestions);
            // Cache suggestion list with 5 minutes Time-to-Live (TTL)
            redisTemplate.opsForValue().set(cacheKey, jsonToCache, 5, TimeUnit.MINUTES);
            logger.info("Cache Populate: Stored suggestions for [{}] on simulated [{}] in Redis (TTL: 5m)", cleanPrefix, targetNode);
        } catch (Exception e) {
            logger.error("Failed to populate cache in Redis. Error: {}", e.getMessage());
        }

        return suggestions;
    }

    /**
     * Fetch overall top trending queries in the system.
     * Maps to QueryEntryRepository.findTopTrending.
     */
    public List<QueryEntry> getTrending() {
        return queryEntryRepository.findTopTrending(PageRequest.of(0, 10));
    }
}

/*
 ==================================================================================
 VIVA NOTES: CACHE-ASIDE (LAZY LOADING) vs WRITE-THROUGH
 ==================================================================================
 1. Cache-Aside: App queries cache. On miss, app queries DB, then writes to cache.
    - If cache node crashes, traffic falls back to DB, and cache is rebuilt lazily.
 2. Write-Through: App writes to cache. Cache server writes to DB.
    - Guarantees cache consistency, but writes are slow since they wait for database confirmation.
 3. Fault Tolerance: Caches should always be optional. If Redis is down, the system remains
    functional, albeit slower, because the DB handles the reads.
 ==================================================================================
*/
