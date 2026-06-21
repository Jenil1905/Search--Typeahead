-- Seed dataset for Search Typeahead System
-- ON CONFLICT ensures that restart of container doesn't cause primary key issues

INSERT INTO query_entry (query_text, total_count, recent_count, last_searched_at) VALUES
('java', 150, 30, CURRENT_TIMESTAMP),
('javascript', 120, 40, CURRENT_TIMESTAMP),
('spring boot', 180, 20, CURRENT_TIMESTAMP),
('react', 200, 50, CURRENT_TIMESTAMP),
('docker', 90, 10, CURRENT_TIMESTAMP),
('redis', 80, 15, CURRENT_TIMESTAMP),
('postgresql', 70, 5, CURRENT_TIMESTAMP),
('python', 110, 25, CURRENT_TIMESTAMP),
('nodejs', 100, 35, CURRENT_TIMESTAMP),
('consistent hashing', 95, 45, CURRENT_TIMESTAMP),
('typeahead system', 85, 50, CURRENT_TIMESTAMP),
('java tutorial', 140, 22, CURRENT_TIMESTAMP),
('java 17 features', 130, 25, CURRENT_TIMESTAMP),
('java compiler online', 115, 18, CURRENT_TIMESTAMP),
('java interview questions', 105, 30, CURRENT_TIMESTAMP),
('java virtual machine', 98, 14, CURRENT_TIMESTAMP),
('java stream api', 92, 19, CURRENT_TIMESTAMP),
('java spring boot', 88, 28, CURRENT_TIMESTAMP),
('java arraylist', 82, 12, CURRENT_TIMESTAMP),
('java design patterns', 78, 15, CURRENT_TIMESTAMP),
('java concurrency', 75, 10, CURRENT_TIMESTAMP)
ON CONFLICT (query_text) DO NOTHING;
