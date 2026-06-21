import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { 
  Search, Database, Cpu, Zap, RefreshCw, 
  Plus, Trash2, Loader2, TrendingUp, HardDrive, 
  AlertCircle, ShieldCheck, ArrowRight, HelpCircle
} from 'lucide-react';

/**
 * <h1>Search Typeahead Dashboard</h1>
 * 
 * <h3>Why this file exists:</h3>
 * This is the main React file representing our entire user interface. It binds together the search input,
 * suggestion dropdown, cache telemetry, and consistent hashing visualization.
 * 
 * <h3>Key Design Patterns Implemented:</h3>
 * <ul>
 *   <li><b>Debouncing (300ms)</b>: Restricts the execution of autocomplete API requests while the user is typing.
 *       Instead of firing requests on every keystroke, a timeout waits 300ms. If the user presses another key,
 *       the timer resets. This protects the backend from keystroke storms.</li>
 *   <li><b>Keyboard Accessibility</b>: Users can navigate suggestions using ArrowUp/ArrowDown keys and press Enter to select,
 *       creating a seamless desktop search feel.</li>
 *   <li><b>Circular SVG Hashing Ring</b>: Uses basic trigonometry (sine, cosine) to map 32-bit hashes onto a
 *       2D circle, visually mapping queries to virtual nodes on a clockwise routing line.</li>
 * </ul>
 */
export default function App() {
  // Search states
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [searchHistory, setSearchHistory] = useState([]);
  const [activeSuggestionIndex, setActiveSuggestionIndex] = useState(-1);
  const [showDropdown, setShowDropdown] = useState(false);

  // System states
  const [trending, setTrending] = useState([]);
  const [cacheStats, setCacheStats] = useState({ hits: 0, misses: 0, hitRate: 0.0 });
  const [buffer, setBuffer] = useState({});
  const [ringState, setRingState] = useState([]);
  const [activeNodes, setActiveNodes] = useState([]);
  
  // Custom action states
  const [newNodeName, setNewNodeName] = useState('');
  const [routedQueryInfo, setRoutedQueryInfo] = useState(null);
  
  // Debounce Timer Ref
  const debounceTimeoutRef = useRef(null);
  const dropdownRef = useRef(null);

  // ----------------------------------------------------
  // API INVOCATIONS
  // ----------------------------------------------------

  // Fetch suggestions with Prefix match
  const fetchSuggestions = async (prefix) => {
    if (!prefix.trim()) {
      setSuggestions([]);
      return;
    }
    setLoading(true);
    try {
      const res = await axios.get(`/api/suggestions?q=${encodeURIComponent(prefix)}`);
      setSuggestions(res.data);
      setError(null);
      
      // Also trace where this prefix hashes on the ring
      const routeRes = await axios.get(`/api/hashing/route?q=${encodeURIComponent(prefix)}`);
      setRoutedQueryInfo(routeRes.data);
    } catch (err) {
      console.error("Error fetching suggestions:", err);
      setError("Failed to connect to backend server.");
    } finally {
      setLoading(false);
    }
  };

  // Submit Search (POST to save searches in memory buffer)
  const handleSearchSubmit = async (searchWord) => {
    if (!searchWord.trim()) return;
    try {
      await axios.post('/api/search', { query: searchWord });
      
      // Add to local UI history logs
      setSearchHistory(prev => [
        { query: searchWord, timestamp: new Date().toLocaleTimeString() },
        ...prev.slice(0, 4)
      ]);
      
      // Refresh system states immediately
      fetchSystemState();
      
      // Clear search box states
      setQuery('');
      setSuggestions([]);
      setShowDropdown(false);
      setActiveSuggestionIndex(-1);
    } catch (err) {
      console.error("Error submitting search:", err);
      setError("Failed to submit search.");
    }
  };

  // Fetch telemetry and cluster configuration
  const fetchSystemState = async () => {
    try {
      const statsRes = await axios.get('/api/cache/stats');
      setCacheStats(statsRes.data);

      const trendingRes = await axios.get('/api/suggestions/trending');
      setTrending(trendingRes.data);

      const bufferRes = await axios.get('/api/hashing/buffer');
      setBuffer(bufferRes.data);

      const ringRes = await axios.get('/api/hashing/ring');
      setRingState(ringRes.data);

      const nodesRes = await axios.get('/api/hashing/nodes');
      setActiveNodes(nodesRes.data);
    } catch (err) {
      console.error("Error loading system metrics:", err);
    }
  };

  // Add a new Redis Node
  const handleAddNode = async (e) => {
    e.preventDefault();
    if (!newNodeName.trim()) return;
    try {
      await axios.post(`/api/hashing/nodes?name=${encodeURIComponent(newNodeName)}`);
      setNewNodeName('');
      fetchSystemState();
    } catch (err) {
      console.error("Error adding node:", err);
    }
  };

  // Remove a Redis Node
  const handleRemoveNode = async (nodeName) => {
    try {
      await axios.delete(`/api/hashing/nodes?name=${encodeURIComponent(nodeName)}`);
      fetchSystemState();
      if (routedQueryInfo && routedQueryInfo.mappedNode === nodeName) {
        setRoutedQueryInfo(null);
      }
    } catch (err) {
      console.error("Error removing node:", err);
    }
  };

  // Reset cache metrics
  const handleResetCacheStats = async () => {
    try {
      await axios.post('/api/cache/reset');
      fetchSystemState();
    } catch (err) {
      console.error("Error resetting stats:", err);
    }
  };

  // ----------------------------------------------------
  // DEBOUNCING AND KEYBOARD LOGIC
  // ----------------------------------------------------

  // Handle typing inside search input
  const handleInputChange = (e) => {
    const value = e.target.value;
    setQuery(value);
    setShowDropdown(true);
    setActiveSuggestionIndex(-1);

    // Clear previous timeout (Debouncing Core)
    if (debounceTimeoutRef.current) {
      clearTimeout(debounceTimeoutRef.current);
    }

    // Set new timeout (will fire after 300ms of inactivity)
    debounceTimeoutRef.current = setTimeout(() => {
      fetchSuggestions(value);
    }, 300);
  };

  // Keyboard navigation inside dropdown
  const handleKeyDown = (e) => {
    if (!showDropdown || suggestions.length === 0) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveSuggestionIndex(prev => (prev + 1) % suggestions.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveSuggestionIndex(prev => (prev - 1 + suggestions.length) % suggestions.length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (activeSuggestionIndex >= 0 && activeSuggestionIndex < suggestions.length) {
        const selected = suggestions[activeSuggestionIndex];
        handleSearchSubmit(selected);
      } else {
        handleSearchSubmit(query);
      }
    } else if (e.key === 'Escape') {
      setShowDropdown(false);
    }
  };

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleOutsideClick = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, []);

  // Poll system state every 2.5 seconds
  useEffect(() => {
    fetchSystemState();
    const interval = setInterval(fetchSystemState, 2500);
    return () => clearInterval(interval);
  }, []);

  // Clean up debounce timer on unmount
  useEffect(() => {
    return () => {
      if (debounceTimeoutRef.current) {
        clearTimeout(debounceTimeoutRef.current);
      }
    };
  }, []);

  // ----------------------------------------------------
  // COORDINATE MATHEMATICS FOR RING
  // ----------------------------------------------------
  const getCoordinates = (hashVal) => {
    // Map full 32-bit integer range to [0, 1] percentage
    const minVal = -2147483648;
    const maxVal = 2147483647;
    const percentage = (hashVal - minVal) / (maxVal - minVal);
    
    // Convert to angle (subtracting PI/2 places 0/Min at 12 o'clock top)
    const angle = (percentage * 2 * Math.PI) - (Math.PI / 2);
    
    // Circle radius is 40% of parent SVG box
    const radius = 40;
    const cx = 50 + radius * Math.cos(angle);
    const cy = 50 + radius * Math.sin(angle);
    return { cx, cy };
  };

  // Assign distinct colors to simulated nodes
  const getNodeColor = (nodeName) => {
    const colors = {
      'RedisNode1': '#3b82f6', // blue
      'RedisNode2': '#10b981', // emerald
      'RedisNode3': '#a855f7', // purple
      'RedisNode4': '#f59e0b', // amber
      'RedisNode5': '#ec4899'  // pink
    };
    return colors[nodeName] || '#6b7280'; // gray fallback
  };

  // Calculate hash of routed query (simple visualization)
  const getRoutedQueryHash = (q) => {
    // We compute a basic mock MD5 index layout or fetch hash from service
    // Let's just generate a simple hash code on the client side for positioning on the ring if not fetched
    let hash = 0;
    for (let i = 0; i < q.length; i++) {
      const char = q.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32bit integer
    }
    return hash;
  };

  return (
    <div className="app-container">
      {/* HEADER BAR */}
      <header className="app-header">
        <div className="header-brand">
          <div className="icon-badge">
            <Zap size={20} className="icon-pulse" />
          </div>
          <div>
            <h1>Search Typeahead Engine</h1>
            <p>Consistent Hashing &amp; Cache-Aside Buffer System</p>
          </div>
        </div>
        <div className="header-status">
          <span className="status-indicator online"></span>
          <span>System Healthy (Cluster Active)</span>
        </div>
      </header>

      {/* ERROR BANNER */}
      {error && (
        <div className="error-banner">
          <AlertCircle size={18} />
          <span>{error}</span>
          <button className="close-btn" onClick={() => setError(null)}>&times;</button>
        </div>
      )}

      {/* DASHBOARD GRID */}
      <main className="dashboard-grid">
        
        {/* LEFT COLUMN: SEARCH INPUT & TRENDING */}
        <section className="dashboard-column flex-col">
          
          {/* SEARCH & AUTOCOMPLETE */}
          <div className="dashboard-card main-search-card" ref={dropdownRef}>
            <div className="card-header">
              <Search size={18} className="text-secondary" />
              <h2>Search Bar</h2>
            </div>
            
            <div className="search-box-wrapper">
              <input
                type="text"
                placeholder="Type here to search (e.g., java, react, docker)..."
                value={query}
                onChange={handleInputChange}
                onKeyDown={handleKeyDown}
                className="search-input"
                autoComplete="off"
              />
              <div className="search-buttons">
                {loading && <Loader2 size={18} className="icon-spin text-muted" />}
                <button 
                  onClick={() => handleSearchSubmit(query)} 
                  className="search-btn"
                  disabled={!query.trim()}
                >
                  Search
                </button>
              </div>

              {/* SUGGESTION DROPDOWN */}
              {showDropdown && query.trim() && (
                <div className="dropdown-panel">
                  {suggestions.length > 0 ? (
                    <ul className="suggestions-list">
                      {suggestions.map((suggestion, index) => (
                        <li
                          key={index}
                          onClick={() => handleSearchSubmit(suggestion)}
                          className={`suggestion-item ${index === activeSuggestionIndex ? 'active' : ''}`}
                        >
                          <Search size={14} className="text-muted" />
                          <span>{suggestion}</span>
                          <ArrowRight size={12} className="arrow-hover" />
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <div className="dropdown-empty">
                      {loading ? "Fetching recommendations..." : "No suggestions found (DB fallback will occur on submission)"}
                    </div>
                  )}
                  {routedQueryInfo && (
                    <div className="routing-preview">
                      <Database size={12} style={{ color: getNodeColor(routedQueryInfo.mappedNode) }} />
                      <span>Consistent Hashing matches prefix <strong>"{query.trim().toLowerCase()}"</strong> to node: 
                        <strong style={{ color: getNodeColor(routedQueryInfo.mappedNode) }}> {routedQueryInfo.mappedNode}</strong>
                      </span>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* DEBOUNCE EDUCATIONAL CAPTION */}
            <div className="card-footer educational">
              <HelpCircle size={14} />
              <p>
                <strong>Debounce Active (300ms)</strong>: Autocomplete queries are delayed. Stops API spam on every keystroke, reducing server traffic by up to 80%!
              </p>
            </div>
          </div>

          {/* TRENDING TOPICS */}
          <div className="dashboard-card">
            <div className="card-header">
              <TrendingUp size={18} className="text-trending" />
              <h2>Trending Searches</h2>
            </div>
            <div className="card-content">
              {trending.length > 0 ? (
                <div className="trending-table-wrapper">
                  <table className="trending-table">
                    <thead>
                      <tr>
                        <th>Query Text</th>
                        <th className="align-right">Lifetime Searches</th>
                        <th className="align-right">Recent (30s)</th>
                        <th className="align-right">Rank Score</th>
                      </tr>
                    </thead>
                    <tbody>
                      {trending.map((item, idx) => {
                        // Weighted score: 0.7 * total + 0.3 * recent
                        const computedScore = Math.round((0.7 * item.totalCount + 0.3 * item.recentCount) * 100) / 100;
                        return (
                          <tr key={item.id} className="trending-row">
                            <td className="query-text">
                              <span className="trending-index">#{idx + 1}</span>
                              {item.queryText}
                            </td>
                            <td className="align-right">{item.totalCount}</td>
                            <td className="align-right text-accent">{item.recentCount}</td>
                            <td className="align-right font-bold text-success">{computedScore}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="empty-message">No queries stored in system database yet.</p>
              )}
            </div>
            <div className="card-footer educational">
              <Cpu size={14} />
              <p>
                <strong>Ranking Formula</strong>: <code>Score = 0.7 * lifetime + 0.3 * recent</code>. Balances historical popularity with recent surges to highlight trending terms.
              </p>
            </div>
          </div>

          {/* HISTORICAL LOGS FOR DEMO */}
          <div className="dashboard-card">
            <div className="card-header">
              <ShieldCheck size={18} className="text-secondary" />
              <h2>Recent Search Submissions</h2>
            </div>
            <div className="card-content">
              {searchHistory.length > 0 ? (
                <ul className="history-list">
                  {searchHistory.map((h, i) => (
                    <li key={i} className="history-item">
                      <Zap size={14} className="text-accent" />
                      <span>Searched: <strong>"{h.query}"</strong></span>
                      <span className="history-time">{h.timestamp}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="empty-message">Submit searches above to watch write-buffering live.</p>
              )}
            </div>
          </div>
        </section>

        {/* RIGHT COLUMN: RING VISUALIZER & SYSTEM METRICS */}
        <section className="dashboard-column flex-col">
          
          {/* CONSISTENT HASHING RING */}
          <div className="dashboard-card hashing-ring-card">
            <div className="card-header">
              <Cpu size={18} className="text-accent" />
              <h2>Consistent Hashing Ring</h2>
            </div>
            
            <div className="ring-visualization-container">
              <div className="svg-wrapper">
                <svg viewBox="0 0 100 100" className="hashing-svg">
                  {/* Outer circle line */}
                  <circle cx="50" cy="50" r="40" className="main-ring-path" />
                  
                  {/* Virtual Nodes dots on the ring */}
                  {ringState.map((vn, idx) => {
                    const { cx, cy } = getCoordinates(vn.hash);
                    return (
                      <circle
                        key={idx}
                        cx={cx}
                        cy={cy}
                        r="1.8"
                        fill={getNodeColor(vn.physicalNode)}
                        className="vn-dot"
                      >
                        <title>{`${vn.virtualNodeName}\nHash: ${vn.hash}`}</title>
                      </circle>
                    );
                  })}

                  {/* Pulsing Dot of Routed Key */}
                  {routedQueryInfo && routedQueryInfo.query && (() => {
                    const queryHash = getRoutedQueryHash(routedQueryInfo.query.toLowerCase().trim());
                    const { cx, cy } = getCoordinates(queryHash);
                    const targetNode = routedQueryInfo.mappedNode;
                    return (
                      <>
                        <line 
                          x1="50" 
                          y1="50" 
                          x2={cx} 
                          y2={cy} 
                          className="routing-ray" 
                        />
                        <circle
                          cx={cx}
                          cy={cy}
                          r="3"
                          fill="#facc15"
                          className="routed-query-dot"
                        >
                          <title>{`Query: "${routedQueryInfo.query}"\nHash: ${queryHash}\nMapped: ${targetNode}`}</title>
                        </circle>
                      </>
                    );
                  })()}
                </svg>

                {/* SVG Center Info */}
                <div className="ring-center-label">
                  <span className="center-title">Nodes</span>
                  <span className="center-value">{activeNodes.length}</span>
                </div>
              </div>

              {/* ROUTE INFO PANEL */}
              {routedQueryInfo && (
                <div className="routed-box">
                  <div className="routed-badge">
                    <span className="pulse-yellow"></span>
                    <span>Last Routed Prefix: <strong>"{routedQueryInfo.query}"</strong></span>
                  </div>
                  <div className="routed-details">
                    <span>Key Hash: <code>{getRoutedQueryHash(routedQueryInfo.query.toLowerCase().trim())}</code></span>
                    <span>→ Clockwise Node: <strong style={{ color: getNodeColor(routedQueryInfo.mappedNode) }}>{routedQueryInfo.mappedNode}</strong></span>
                  </div>
                </div>
              )}
            </div>

            {/* NODE REGISTRATION AND CONTROLS */}
            <div className="node-manager-panel">
              <h3>Configure Cluster Nodes</h3>
              <form onSubmit={handleAddNode} className="add-node-form">
                <input
                  type="text"
                  placeholder="e.g. RedisNode4"
                  value={newNodeName}
                  onChange={(e) => setNewNodeName(e.target.value)}
                  className="node-input"
                />
                <button type="submit" className="add-node-btn">
                  <Plus size={16} /> Add Node
                </button>
              </form>

              <div className="active-nodes-list">
                {activeNodes.map((node) => (
                  <div key={node} className="node-chip" style={{ borderLeftColor: getNodeColor(node) }}>
                    <span className="node-chip-name">{node}</span>
                    {activeNodes.length > 1 && (
                      <button 
                        onClick={() => handleRemoveNode(node)} 
                        className="delete-node-btn"
                        title="Remove cache node"
                      >
                        <Trash2 size={12} />
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* IN-MEMORY BUFFER (BATCH WRITING SYSTEM) */}
          <div className="dashboard-card">
            <div className="card-header">
              <HardDrive size={18} className="text-secondary" />
              <h2>Write Buffer Monitor (Memory)</h2>
            </div>
            <div className="card-content">
              {Object.keys(buffer).length > 0 ? (
                <div className="buffer-visual-box">
                  <div className="buffer-badge">
                    <span className="pulse-green"></span>
                    <span>AGGREGATED IN RAM (Flushes every 30s)</span>
                  </div>
                  <div className="buffer-chips">
                    {Object.entries(buffer).map(([q, count]) => (
                      <div key={q} className="buffer-chip">
                        <span className="buffer-text">"{q}"</span>
                        <span className="buffer-count">+{count}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="buffer-empty">
                  <Loader2 size={16} className="icon-spin text-muted" />
                  <span>Write buffer empty. Search queries will aggregate here first.</span>
                </div>
              )}
            </div>
            <div className="card-footer educational">
              <HelpCircle size={14} />
              <p>
                <strong>Batch Persist</strong>: Incoming searches are held in RAM. Every 30 seconds, a background transaction flushes this queue into Postgres, shielding the database disk from heavy writes.
              </p>
            </div>
          </div>

          {/* CACHE TELEMETRY / STATS */}
          <div className="dashboard-card">
            <div className="card-header">
              <Database size={18} className="text-success" />
              <h2>Cache Telemetry</h2>
              <button 
                onClick={handleResetCacheStats} 
                className="reset-stats-btn" 
                title="Reset cache statistics"
              >
                <RefreshCw size={14} />
              </button>
            </div>
            <div className="card-content">
              <div className="stats-row">
                <div className="stat-card">
                  <span className="stat-label">Cache Hits</span>
                  <span className="stat-value text-success">{cacheStats.hits}</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label">Cache Misses</span>
                  <span className="stat-value text-error">{cacheStats.misses}</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label font-bold">Hit Rate</span>
                  <span className={`stat-value font-extrabold ${cacheStats.hitRate >= 75 ? 'text-success' : 'text-trending'}`}>
                    {cacheStats.hitRate}%
                  </span>
                </div>
              </div>
            </div>
            <div className="card-footer educational">
              <HelpCircle size={14} />
              <p>
                <strong>Cache-Aside</strong>: Autocomplete matches are read from Redis. Misses query Postgres and populate the cache. Flush events invalidate cache keys to avoid stale statistics.
              </p>
            </div>
          </div>

        </section>

      </main>
    </div>
  );
}
