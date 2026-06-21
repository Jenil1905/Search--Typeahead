package com.example.typeahead.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>SearchSubmissionService</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This service buffers incoming user searches in-memory, aggregating duplicate terms
 * before they are persisted to the database.
 * 
 * <h3>What problem it solves:</h3>
 * <ul>
 *   <li><b>Database Bottlenecks</b>: In a high-traffic system (e.g. Google or Amazon), thousands of users
 *       search per second. If we execute a SQL <code>UPDATE</code> statement for every single search submission,
 *       the database will run out of connection pool threads, hit high disk I/O, lock rows, and fail under load.</li>
 *   <li><b>Write Amplification</b>: Writing small updates repeatedly forces database engines (PostgreSQL)
 *       to rewrite database pages and WAL logs constantly.</li>
 *   <li><b>Solution</b>: In-memory aggregation buffers these updates. If "java" is searched 100 times in 30 seconds,
 *       we store the number 100 in memory and perform exactly <b>one</b> SQL update instead of 100.</li>
 * </ul>
 * 
 * <h3>How it works internally:</h3>
 * It uses a {@link ConcurrentHashMap} where the key is the lowercase, trimmed query text and the value is the aggregated search count.
 * The <code>ConcurrentHashMap.merge()</code> method is used to atomically add counts, ensuring thread safety
 * when multiple user requests submit searches at the same time.
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: Why are direct database writes bad for a search submission endpoint?</b><br>
 *       A: Because databases are optimized for durability and transactions, which require disk operations. Disk I/O
 *       is thousands of times slower than memory. Buffering in memory shifts the write burden from disk to RAM.</li>
 *   <li><b>Q: What is the trade-off of this in-memory buffering?</b><br>
 *       A: <b>Data Loss Risk</b>: If the application server crashes or loses power before the 30-second flush,
 *       the searches buffered in memory are lost forever. For search popularity, this is an acceptable trade-off
 *       (loss of 30 seconds of search history is not critical, unlike financial transactions).</li>
 * </ol>
 */
@Service
public class SearchSubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(SearchSubmissionService.class);

    // In-memory buffer to aggregate search terms and counts
    private final ConcurrentHashMap<String, Long> searchBuffer = new ConcurrentHashMap<>();

    /**
     * Submits a search query.
     * Cleans the input and increments its count in the thread-safe buffer.
     * 
     * @param query Raw search query submitted by the user.
     */
    public void submitSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        String cleanQuery = query.trim().toLowerCase();
        
        // Atomically increment the count in the buffer
        searchBuffer.merge(cleanQuery, 1L, Long::sum);
        
        logger.info("Buffered search submission: [{}] (Current buffer count: {})", 
                cleanQuery, searchBuffer.get(cleanQuery));
    }

    /**
     * Returns a snapshot of the current buffered searches.
     * Used by the scheduler to flush data, and the controller for visualization.
     */
    public Map<String, Long> getBufferSnapshot() {
        return new HashMap<>(searchBuffer);
    }

    /**
     * Cleans the buffer. Called after successfully persisting updates to PostgreSQL.
     */
    public void clearBuffer() {
        searchBuffer.clear();
        logger.debug("In-memory search buffer cleared");
    }

    /**
     * Overloaded clear method to selectively remove keys that were successfully flushed.
     * This avoids race conditions if a user searches for a term while the flush is happening.
     */
    public void removeFromBuffer(String query, long countSubtracted) {
        searchBuffer.computeIfPresent(query, (k, currentVal) -> {
            long remaining = currentVal - countSubtracted;
            return remaining <= 0 ? null : remaining;
        });
    }
}

/*
 ==================================================================================
 VIVA NOTES: WRITE BUFFERING AND AGGREGATION
 ==================================================================================
 1. Memory vs Disk: RAM access latency is ~100ns; NVMe SSD write latency is ~10-100µs.
    In-memory buffering saves precious millisecond disk seek times.
 2. Thread Safety: We use ConcurrentHashMap instead of HashMap. HashMap is not thread-safe:
    simultaneous writes can cause infinite loops or data corruption (e.g., node circular references).
 ==================================================================================
*/
