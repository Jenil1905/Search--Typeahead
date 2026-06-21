package com.example.typeahead.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * <h1>QueryEntry Entity</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This is the JPA (Java Persistence API) entity that maps to the database table <code>query_entry</code>.
 * It models the state of a search query in our system, including historical popularity, recent popularity,
 * and metadata.
 * 
 * <h3>What problem it solves:</h3>
 * It abstracts database interactions. Instead of writing raw SQL to map result sets to Java objects,
 * Hibernate JPA reads this class structure and maps it automatically to table columns.
 * 
 * <h3>Field Explanations:</h3>
 * <ul>
 *   <li><b>id</b>: Unique primary key. Required for relational database integrity. Automatically incremented.</li>
 *   <li><b>queryText</b>: The actual string the user searched (e.g. "java"). It must be unique, case-insensitive,
 *       and indexed. Indexing is crucial for fast prefix matches (e.g., finding queries starting with "ja").</li>
 *   <li><b>totalCount</b>: Total number of times this query was submitted since system creation. Represents
 *       long-term historical popularity.</li>
 *   <li><b>recentCount</b>: Number of times this query was searched in the recent tracking window (e.g., current sliding day/hour).
 *       Used to identify what is trending *right now*.</li>
 *   <li><b>lastSearchedAt</b>: Timestamp of the last search event. Helpful for cache eviction policies and analytical reports.</li>
 * </ul>
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: Why do we add an index on queryText?</b><br>
 *       A: Because prefix matching (e.g., <code>LIKE 'abc%'</code>) will perform an expensive table scan if not indexed.
 *       An index on <code>queryText</code> allows the database to do a fast range scan (B-Tree index traversal), reducing latency.</li>
 *   <li><b>Q: What is @Column(nullable = false, unique = true)?</b><br>
 *       A: It sets schema-level constraints. <code>nullable = false</code> means the field cannot be null (NOT NULL).
 *       <code>unique = true</code> ensures no duplicate search terms can be inserted, which prevents duplicate entries for the same query.</li>
 * </ol>
 */
@Entity
@Table(
    name = "query_entry",
    indexes = {
        @Index(name = "idx_query_text", columnList = "queryText")
    }
)
public class QueryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String queryText;

    @Column(nullable = false)
    private Long totalCount;

    @Column(nullable = false)
    private Long recentCount;

    @Column(nullable = false)
    private LocalDateTime lastSearchedAt;

    // --- Constructors ---
    
    // Default constructor required by JPA
    public QueryEntry() {
    }

    public QueryEntry(String queryText, Long totalCount, Long recentCount, LocalDateTime lastSearchedAt) {
        this.queryText = queryText;
        this.totalCount = totalCount;
        this.recentCount = recentCount;
        this.lastSearchedAt = lastSearchedAt;
    }

    // --- Getters & Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Long getRecentCount() {
        return recentCount;
    }

    public void setRecentCount(Long recentCount) {
        this.recentCount = recentCount;
    }

    public LocalDateTime getLastSearchedAt() {
        return lastSearchedAt;
    }

    public void setLastSearchedAt(LocalDateTime lastSearchedAt) {
        this.lastSearchedAt = lastSearchedAt;
    }

    @Override
    public String toString() {
        return "QueryEntry{" +
                "id=" + id +
                ", queryText='" + queryText + '\'' +
                ", totalCount=" + totalCount +
                ", recentCount=" + recentCount +
                ", lastSearchedAt=" + lastSearchedAt +
                '}';
    }
}

/*
 ==================================================================================
 VIVA NOTES: JPA ENTITY AND INDEXING
 ==================================================================================
 1. Indexing: We index <code>queryText</code> because the primary operation of a typeahead service is prefix query search.
 2. Primary Key Generation: GenerationType.IDENTITY delegates ID generation to the database auto-increment column (SERIAL in PostgreSQL).
 ==================================================================================
*/
