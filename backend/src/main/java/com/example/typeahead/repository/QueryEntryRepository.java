package com.example.typeahead.repository;

import com.example.typeahead.model.QueryEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * <h1>QueryEntryRepository</h1>
 * 
 * <h3>Why this interface exists:</h3>
 * This is the Data Access Object (DAO) interface. It extends {@link JpaRepository}, allowing us
 * to perform database CRUD operations on the <code>query_entry</code> table.
 * 
 * <h3>What problem it solves:</h3>
 * It removes boilerplate SQL code. Spring Data JPA automatically provides SQL implementations at runtime
 * for standard methods (e.g. <code>save()</code>, <code>findById()</code>) and custom queries written in JPQL.
 * 
 * <h3>How it works internally:</h3>
 * <ul>
 *   <li>Spring Data JPA scans this interface and creates a proxy bean implementation at runtime.</li>
 *   <li>The method <code>findByQueryTextIgnoreCase</code> uses Spring's query creation naming convention,
 *       which translates to SQL: <code>SELECT * FROM query_entry WHERE LOWER(query_text) = LOWER(?)</code>.</li>
 *   <li>The <code>@Query</code> annotation contains a JPQL (Java Persistence Query Language) query. It matches the
 *       prefix using <code>LIKE CONCAT(:prefix, '%')</code> and sorts using our ranking formula:
 *       <code>0.7 * totalCount + 0.3 * recentCount</code>.</li>
 * </ul>
 * 
 * <h3>Common Viva Questions:</h3>
 * <ol>
 *   <li><b>Q: What is JPQL?</b><br>
 *       A: JPQL stands for Java Persistence Query Language. It is a platform-independent query language
 *       used to write database queries in JPA. Instead of targeting table and column names directly, it targets
 *       Java Entity classes (e.g. <code>QueryEntry</code>) and field names (e.g. <code>queryText</code>).</li>
 *   <li><b>Q: What is Pageable and why use it?</b><br>
 *       A: <code>Pageable</code> is a Spring Data parameter used to enforce pagination. It appends
 *       <code>LIMIT ? OFFSET ?</code> to the generated SQL, preventing the database from loading millions of rows
 *       into memory. In our case, we pass a Limit of 10.</li>
 * </ol>
 */
@Repository
public interface QueryEntryRepository extends JpaRepository<QueryEntry, Long> {

    /**
     * Find a query entry by its exact text, case-insensitive.
     * Useful for updating counts during search submission.
     */
    Optional<QueryEntry> findByQueryTextIgnoreCase(String queryText);

    /**
     * Finds top suggestions starting with a specific prefix.
     * Uses our custom score formula: (0.7 * totalCount) + (0.3 * recentCount)
     * limit is controlled using the Pageable parameter.
     */
    @Query("SELECT q FROM QueryEntry q WHERE LOWER(q.queryText) LIKE LOWER(CONCAT(:prefix, '%')) " +
           "ORDER BY (0.7 * q.totalCount + 0.3 * q.recentCount) DESC")
    List<QueryEntry> findSuggestions(@Param("prefix") String prefix, Pageable pageable);

    /**
     * Finds the overall top queries across the whole database.
     * Used for display on the dashboard or trending search panel.
     */
    @Query("SELECT q FROM QueryEntry q ORDER BY (0.7 * q.totalCount + 0.3 * q.recentCount) DESC")
    List<QueryEntry> findTopTrending(Pageable pageable);
}

/*
 ==================================================================================
 VIVA NOTES: PREFIX SEARCH OPTIMIZATION
 ==================================================================================
 1. JPQL: <code>LOWER(q.queryText) LIKE LOWER(CONCAT(:prefix, '%'))</code> compiles to
    an index-friendly query if we index the column correctly.
 2. Sorting in DB vs memory: Sorting in DB is optimal when we only want the top N items, as the DB
    optimizer can use the index and sorting algorithms (like Heap Sort) to return only 10 items
    without loading the whole table.
 ==================================================================================
*/
