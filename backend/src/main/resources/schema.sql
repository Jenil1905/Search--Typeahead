-- SQL Schema for Search Typeahead System

CREATE TABLE IF NOT EXISTS query_entry (
    id BIGSERIAL PRIMARY KEY,
    query_text VARCHAR(255) NOT NULL UNIQUE,
    total_count BIGINT NOT NULL DEFAULT 0,
    recent_count BIGINT NOT NULL DEFAULT 0,
    last_searched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index on query_text column for case-insensitive/prefix search efficiency
CREATE INDEX IF NOT EXISTS idx_query_text ON query_entry (query_text);
