package com.example.typeahead.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <h1>CacheStatsService</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This service tracks cache statistics (hits, misses, and hit rate) for key lookup operations.
 * 
 * <h3>What problem it solves:</h3>
 * Tracking cache performance is critical for production capacity planning. If the cache hit rate is low
 * (e.g. below 50%), it indicates the cache is sized incorrectly, has poor eviction settings, or the application
 * logic has a bug. This service tracks these metrics dynamically in-memory without adding SQL overhead.
 * 
 * <h3>How it works internally:</h3>
 * <ul>
 *   <li>It uses {@link AtomicLong} for thread-safe increments. Standard Java primitive <code>long</code>
 *       is not thread-safe and can cause race conditions (lost updates) when updated by concurrent request threads.</li>
 *   <li>The <code>getStats()</code> method retrieves the current hits and misses, calculates the total requests,
 *       and formats the hit rate as a percentage with 2 decimal places.</li>
 * </ul>
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: What is a Cache Hit Rate, and what is a good target?</b><br>
 *       A: The cache hit rate is <code>(Hits / (Hits + Misses)) * 100%</code>. A good target for read-heavy systems
 *       is 80% to 95%. Higher hit rates mean fewer database queries and lower system latencies.</li>
 *   <li><b>Q: Why use AtomicLong instead of volatile long or plain long?</b><br>
 *       A: Plain long is vulnerable to write collision. <code>volatile long</code> ensures visibility across threads
 *       but does not guarantee atomicity for compound operations (read-modify-write like <code>++</code>).
 *       <code>AtomicLong</code> uses low-level CPU Compare-And-Swap (CAS) instructions to perform atomic increments
 *       without expensive lock overhead.</li>
 * </ol>
 */
@Service
public class CacheStatsService {

    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    /**
     * Records a cache hit.
     */
    public void recordHit() {
        hits.incrementAndGet();
    }

    /**
     * Records a cache miss.
     */
    public void recordMiss() {
        misses.incrementAndGet();
    }

    /**
     * Resets the cache statistics to zero.
     */
    public void reset() {
        hits.set(0);
        misses.set(0);
    }

    /**
     * Retrieves the stats.
     */
    public Map<String, Object> getStats() {
        long currentHits = hits.get();
        long currentMisses = misses.get();
        long total = currentHits + currentMisses;
        
        double hitRate = 0.0;
        if (total > 0) {
            hitRate = ((double) currentHits / total) * 100.0;
            // Round to 2 decimal places
            hitRate = Math.round(hitRate * 100.0) / 100.0;
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("hits", currentHits);
        stats.put("misses", currentMisses);
        stats.put("totalRequests", total);
        stats.put("hitRate", hitRate);
        
        return stats;
    }
}

/*
 ==================================================================================
 VIVA NOTES: CACHE TELEMETRY
 ==================================================================================
 1. Caching without metrics is flying blind. In enterprise environments, these metrics are pushed
    to systems like Prometheus and visualised using Grafana.
 2. Cache Hit Rate Formula: (Hits / Total Requests) * 100.
 3. Memory overhead: AtomicLong is lightweight and consumes only 8 bytes of heap memory.
 ==================================================================================
*/
