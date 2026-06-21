package com.example.typeahead.service;

import org.springframework.stereotype.Service;

/**
 * <h1>TrendingSearchService</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This service calculates the popularity score for a search term using a weighted hybrid formula that
 * merges historical lifetime volume with recent velocity.
 * 
 * <h3>What problem it solves:</h3>
 * <ul>
 *   <li><b>If we rank by Total Count only:</b> The list becomes static. Old, massive queries (e.g. "facebook")
 *       will permanently dominate, making it impossible for new, fast-rising topics (e.g. "world cup 2026", "chatgpt")
 *       to break into the trending list.</li>
 *   <li><b>If we rank by Recent Count only:</b> The suggestions become highly volatile. A single spam bot searching
 *       for an obscure term 50 times in a minute would force it to rank #1.</li>
 *   <li><b>Hybrid Ranking:</b> Solves both by combining them: <code>Score = 0.7 * totalCount + 0.3 * recentCount</code>.</li>
 * </ul>
 * 
 * <h3>How it works internally:</h3>
 * The <code>calculateScore</code> method receives the raw counts from the DB or memory and returns a computed double score.
 * This decoupled service makes it easy to fine-tune weights or swap in more complex decay algorithms (e.g., exponential time-decay)
 * without touching database queries.
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: Why do recent searches matter in a Typeahead system?</b><br>
 *       A: Search behavior is highly correlated with time and current events. If a natural disaster, sports event, or
 *       viral topic occurs, users will search for it immediately. Typeahead must adapt quickly to match user intent.</li>
 *   <li><b>Q: What are alternative algorithms for trending searches?</b><br>
 *       A: 
 *       <ul>
 *         <li><b>Hacker News Formula</b>: <code>Score = (Votes - 1) / (Age_in_Hours + 2)^1.8</code>.</li>
 *         <li><b>Reddit Hot Ranking</b>: Logarithmic scale for score + linear time-decay factor.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@Service
public class TrendingSearchService {

    /**
     * Calculates the popularity score using the hybrid formula:
     * Score = (0.7 * totalCount) + (0.3 * recentCount)
     * 
     * @param totalCount  Historical lifetime search count
     * @param recentCount Recent search count within the current window/buffer
     * @return Double representing the popularity weight of the query
     */
    public double calculateScore(long totalCount, long recentCount) {
        return (0.7 * totalCount) + (0.3 * recentCount);
    }
}

/*
 ==================================================================================
 VIVA NOTES: TRENDING ALGORITHMS
 ==================================================================================
 1. Lifetime vs velocity: Lifetime volume represents trust and baseline popularity. Velocity (recent searches)
    represents freshness and viral potential.
 2. Scoring weights: The weights (0.7 and 0.3) are configurable. A news website would favor velocity (e.g., 0.8 * recent + 0.2 * total),
    while an e-commerce catalog might favor historical sales volume (e.g., 0.9 * total + 0.1 * recent).
 ==================================================================================
*/
