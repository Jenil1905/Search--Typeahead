package com.example.typeahead.service;

import com.example.typeahead.model.QueryEntry;
import com.example.typeahead.repository.QueryEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * <h1>BatchWriteScheduler</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This component runs background scheduled tasks. Its primary job is to periodically flush aggregated
 * search counts from the memory buffer in {@link SearchSubmissionService} into the PostgreSQL database.
 * It also handles periodic decay of recent popularity scores and invalidation of the Redis cache.
 * 
 * <h3>What problem it solves:</h3>
 * <ul>
 *   <li><b>PostgreSQL Load Reduction</b>: Instead of spamming the database with update operations on every click,
 *       this scheduler flushes all changes once every 30 seconds inside a single transaction. This reduces connection holding times
 *       and index re-balancing operations.</li>
 *   <li><b>Cache Invalidation</b>: In the Cache-Aside pattern, the cache becomes stale when database values change.
 *       After updating the DB, this scheduler invalidates (deletes) keys in Redis to ensure subsequent queries see
 *       the updated suggestion rankings.</li>
 *   <li><b>Recent Count Decay</b>: A "recent" count must decay over time, otherwise it behaves identically to
 *       the historical total count. The scheduler halving recent counts periodically ensures trending queries naturally cool down.</li>
 * </ul>
 * 
 * <h3>How it works internally:</h3>
 * <ol>
 *   <li>The <code>flushBufferToDatabase()</code> method runs every 30 seconds (configured via fixedRate).</li>
 *   <li>It captures a snapshot of the buffered search queries, clears them from the queue to prevent loss,
 *       and fetches corresponding database records in batch.</li>
 *   <li>It performs an "upsert" (update existing or insert new row) for each term, saves them all at once using
 *       <code>repository.saveAll()</code>, and clears the Redis cache.</li>
 * </ol>
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: What happens to the cache consistency when the database is updated?</b><br>
 *       A: We perform active <b>Cache Invalidation</b>. By deleting the cached suggestion lists in Redis,
 *       the next request for suggestions will trigger a cache-miss, fetch the newly sorted records from the database,
 *       and populate Redis with fresh data. This guarantees eventual consistency.</li>
 *   <li><b>Q: How does spring schedule tasks?</b><br>
 *       A: Spring uses a task scheduler thread pool. By default, it runs tasks on a background thread.
 *       We configured it in {@link com.example.typeahead.config.SchedulerConfig} to use 2 threads to prevent execution delays.</li>
 * </ol>
 */
@Service
public class BatchWriteScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BatchWriteScheduler.class);

    private final SearchSubmissionService searchSubmissionService;
    private final QueryEntryRepository queryEntryRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public BatchWriteScheduler(SearchSubmissionService searchSubmissionService,
                               QueryEntryRepository queryEntryRepository,
                               RedisTemplate<String, String> redisTemplate) {
        this.searchSubmissionService = searchSubmissionService;
        this.queryEntryRepository = queryEntryRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Flushes buffered counts to PostgreSQL database every 30 seconds.
     * Uses `@Transactional` to ensure the entire batch succeeds or rolls back as a unit.
     */
    @Scheduled(fixedRateString = "${app.scheduler.batch-write-rate-ms:30000}")
    @Transactional
    public void flushBufferToDatabase() {
        Map<String, Long> batch = searchSubmissionService.getBufferSnapshot();
        if (batch.isEmpty()) {
            logger.info("Batch Write Scheduler: In-memory buffer is empty. Skipping database flush.");
            return;
        }

        logger.info("Batch Write Scheduler: Flushing {} aggregated search terms to PostgreSQL...", batch.size());

        List<QueryEntry> entriesToSave = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : batch.entrySet()) {
            String queryText = entry.getKey();
            Long countIncrement = entry.getValue();

            // Safely subtract from the memory buffer so we only clear what we are currently saving
            searchSubmissionService.removeFromBuffer(queryText, countIncrement);

            // Fetch from DB if exists, otherwise create new
            Optional<QueryEntry> optionalEntry = queryEntryRepository.findByQueryTextIgnoreCase(queryText);
            QueryEntry queryEntry;
            
            if (optionalEntry.isPresent()) {
                queryEntry = optionalEntry.get();
                queryEntry.setTotalCount(queryEntry.getTotalCount() + countIncrement);
                queryEntry.setRecentCount(queryEntry.getRecentCount() + countIncrement);
                queryEntry.setLastSearchedAt(LocalDateTime.now());
            } else {
                queryEntry = new QueryEntry(
                        queryText,
                        countIncrement, // totalCount
                        countIncrement, // recentCount
                        LocalDateTime.now()
                );
            }
            entriesToSave.add(queryEntry);
        }

        // Batch save to PostgreSQL
        queryEntryRepository.saveAll(entriesToSave);
        logger.info("Batch Write Scheduler: Successfully saved {} records to database.", entriesToSave.size());

        // Invalidate Redis cache
        invalidateCache();
    }

    /**
     * Decay Task: Runs every 10 minutes to cool down trending queries by 20%.
     * Keeps trending searches fresh by reducing historical recent velocity.
     */
    @Scheduled(fixedRate = 600000) // 10 minutes (600,000 ms)
    @Transactional
    public void decayRecentSearchCounts() {
        logger.info("Decay Scheduler: Commencing decay of recent search popularity statistics...");
        List<QueryEntry> allEntries = queryEntryRepository.findAll();
        boolean changed = false;
        
        for (QueryEntry entry : allEntries) {
            if (entry.getRecentCount() > 0) {
                // Exponential decay: reduce recentCount by 20%
                long decayedCount = Math.round(entry.getRecentCount() * 0.8);
                entry.setRecentCount(decayedCount);
                changed = true;
            }
        }
        
        if (changed) {
            queryEntryRepository.saveAll(allEntries);
            logger.info("Decay Scheduler: Successfully decayed recent search metrics.");
            invalidateCache();
        }
    }

    /**
     * Helper to clear Redis Cache.
     * Deletes all cached query suggestion keys.
     */
    private void invalidateCache() {
        try {
            // Find all cached suggestion keys in Redis
            Set<String> keys = redisTemplate.keys("simulated_node:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("Cache Invalidation: Deleted {} suggestion keys from Redis.", keys.size());
            }
        } catch (Exception e) {
            logger.error("Cache Invalidation Error: Failed to flush Redis keys", e);
        }
    }
}

/*
 ==================================================================================
 VIVA NOTES: BATCH TRANSACTIONS AND FAILURES
 ==================================================================================
 1. Why @Transactional?
    If the database server shuts down midway through saving 100 queries, we want to roll back the whole batch.
    This prevents partial updates and inconsistent state.
 
 2. Failure Scenarios:
    If the database is down, the scheduler will throw an exception. The in-memory buffer subtraction
    is done safely. However, to prevent losing data in real systems, we would catch database write errors, 
    restore the counts back to the memory buffer, and retry on the next interval.
 ==================================================================================
*/
