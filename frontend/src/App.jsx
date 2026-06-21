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
 * Key Design Patterns:
 * - Debouncing (300ms): prevents keystroke storms on the backend.
 * - Keyboard Accessibility: ArrowUp/Down + Enter to navigate suggestions.
 * - Consistent Hashing Ring: SVG circle using trigonometry to map hashes.
 */
export default function App() {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [searchHistory, setSearchHistory] = useState([]);
  const [activeSuggestionIndex, setActiveSuggestionIndex] = useState(-1);
  const [showDropdown, setShowDropdown] = useState(false);

  const [trending, setTrending] = useState([]);
  const [cacheStats, setCacheStats] = useState({ hits: 0, misses: 0, hitRate: 0.0 });
  const [buffer, setBuffer] = useState({});
  const [ringState, setRingState] = useState([]);
  const [activeNodes, setActiveNodes] = useState([]);
  const [newNodeName, setNewNodeName] = useState('');
  const [routedQueryInfo, setRoutedQueryInfo] = useState(null);

  const debounceTimeoutRef = useRef(null);
  const dropdownRef = useRef(null);

  // ── API ──────────────────────────────────────────────────────────────
  const fetchSuggestions = async (prefix) => {
    if (!prefix.trim()) { setSuggestions([]); return; }
    setLoading(true);
    try {
      const res = await axios.get(`/api/suggestions?q=${encodeURIComponent(prefix)}`);
      setSuggestions(res.data);
      setError(null);
      const routeRes = await axios.get(`/api/hashing/route?q=${encodeURIComponent(prefix)}`);
      setRoutedQueryInfo(routeRes.data);
    } catch {
      setError('Failed to connect to backend server.');
    } finally {
      setLoading(false);
    }
  };

  const handleSearchSubmit = async (searchWord) => {
    if (!searchWord.trim()) return;
    try {
      await axios.post('/api/search', { query: searchWord });
      setSearchHistory(prev => [
        { query: searchWord, timestamp: new Date().toLocaleTimeString() },
        ...prev.slice(0, 4)
      ]);
      fetchSystemState();
      setQuery(''); setSuggestions([]); setShowDropdown(false); setActiveSuggestionIndex(-1);
    } catch {
      setError('Failed to submit search.');
    }
  };

  const fetchSystemState = async () => {
    try {
      const [statsRes, trendingRes, bufferRes, ringRes, nodesRes] = await Promise.all([
        axios.get('/api/cache/stats'),
        axios.get('/api/suggestions/trending'),
        axios.get('/api/hashing/buffer'),
        axios.get('/api/hashing/ring'),
        axios.get('/api/hashing/nodes'),
      ]);
      setCacheStats(statsRes.data);
      setTrending(trendingRes.data);
      setBuffer(bufferRes.data);
      setRingState(ringRes.data);
      setActiveNodes(nodesRes.data);
    } catch { /* silent */ }
  };

  const handleAddNode = async (e) => {
    e.preventDefault();
    if (!newNodeName.trim()) return;
    try { await axios.post(`/api/hashing/nodes?name=${encodeURIComponent(newNodeName)}`); setNewNodeName(''); fetchSystemState(); } catch { /* */ }
  };

  const handleRemoveNode = async (nodeName) => {
    try {
      await axios.delete(`/api/hashing/nodes?name=${encodeURIComponent(nodeName)}`);
      fetchSystemState();
      if (routedQueryInfo?.mappedNode === nodeName) setRoutedQueryInfo(null);
    } catch { /* */ }
  };

  const handleResetCacheStats = async () => {
    try { await axios.post('/api/cache/reset'); fetchSystemState(); } catch { /* */ }
  };

  // ── INPUT HANDLING ────────────────────────────────────────────────────
  const handleInputChange = (e) => {
    const value = e.target.value;
    setQuery(value); setShowDropdown(true); setActiveSuggestionIndex(-1);
    if (debounceTimeoutRef.current) clearTimeout(debounceTimeoutRef.current);
    debounceTimeoutRef.current = setTimeout(() => fetchSuggestions(value), 300);
  };

  const handleKeyDown = (e) => {
    if (!showDropdown || suggestions.length === 0) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); setActiveSuggestionIndex(p => (p + 1) % suggestions.length); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActiveSuggestionIndex(p => (p - 1 + suggestions.length) % suggestions.length); }
    else if (e.key === 'Enter') {
      e.preventDefault();
      if (activeSuggestionIndex >= 0) handleSearchSubmit(suggestions[activeSuggestionIndex]);
      else handleSearchSubmit(query);
    } else if (e.key === 'Escape') setShowDropdown(false);
  };

  useEffect(() => {
    const handler = (e) => { if (dropdownRef.current && !dropdownRef.current.contains(e.target)) setShowDropdown(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  useEffect(() => { fetchSystemState(); const t = setInterval(fetchSystemState, 2500); return () => clearInterval(t); }, []);
  useEffect(() => () => { if (debounceTimeoutRef.current) clearTimeout(debounceTimeoutRef.current); }, []);

  // ── RING MATH ─────────────────────────────────────────────────────────
  const getCoords = (hashVal) => {
    const pct = (hashVal - (-2147483648)) / (2147483647 - (-2147483648));
    const angle = pct * 2 * Math.PI - Math.PI / 2;
    return { cx: 50 + 40 * Math.cos(angle), cy: 50 + 40 * Math.sin(angle) };
  };

  const nodeColor = (name) => ({
    RedisNode1: '#00e5ff', RedisNode2: '#00ffb3',
    RedisNode3: '#b845ff', RedisNode4: '#ffd600', RedisNode5: '#ff2d78',
  }[name] || '#7a8099');

  const clientHash = (q) => {
    let h = 0;
    for (let i = 0; i < q.length; i++) { h = ((h << 5) - h) + q.charCodeAt(i); h = h & h; }
    return h;
  };

  // ── RENDER ────────────────────────────────────────────────────────────
  return (
    <div className="app-container">

      {/* HEADER */}
      <header className="app-header">
        <div className="header-brand">
          <div className="brand-icon">
            <Zap size={20} />
          </div>
          <div>
            <h1>Search Typeahead Engine</h1>
            <p>Consistent Hashing · Cache-Aside · Write Buffer</p>
          </div>
        </div>
        <div className="header-status">
          <span className="status-dot"></span>
          Cluster Healthy
        </div>
      </header>

      {/* ERROR */}
      {error && (
        <div className="error-banner">
          <AlertCircle size={16} />
          <span>{error}</span>
          <button className="close-btn" onClick={() => setError(null)}>×</button>
        </div>
      )}

      {/* GRID */}
      <main className="dashboard-grid">

        {/* LEFT COLUMN */}
        <section className="dashboard-column">

          {/* SEARCH */}
          <div className="dashboard-card main-search-card" ref={dropdownRef}>
            <div className="card-header">
              <div className="card-icon icon-cyan"><Search size={15} /></div>
              <h2>Search Bar</h2>
            </div>

            <div className="search-box-wrapper">
              <input
                type="text"
                placeholder="Try: iphone, java, react, docker…"
                value={query}
                onChange={handleInputChange}
                onKeyDown={handleKeyDown}
                className="search-input"
                autoComplete="off"
                id="main-search-input"
              />
              <div className="search-buttons">
                {loading && <Loader2 size={16} className="icon-spin text-muted" />}
                <button onClick={() => handleSearchSubmit(query)} className="search-btn" disabled={!query.trim()} id="search-submit-btn">
                  Search
                </button>
              </div>

              {/* DROPDOWN */}
              {showDropdown && query.trim() && (
                <div className="dropdown-panel" id="suggestions-dropdown">
                  {suggestions.length > 0 ? (
                    <ul className="suggestions-list">
                      {suggestions.map((s, i) => (
                        <li
                          key={i}
                          onClick={() => handleSearchSubmit(s)}
                          className={`suggestion-item ${i === activeSuggestionIndex ? 'active' : ''}`}
                        >
                          <Search size={13} className="text-muted" />
                          <span>{s}</span>
                          <ArrowRight size={12} className="arrow-hover" />
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <div className="dropdown-empty">
                      {loading ? 'Fetching suggestions…' : 'No suggestions found'}
                    </div>
                  )}
                  {routedQueryInfo && (
                    <div className="routing-preview">
                      <Database size={11} style={{ color: nodeColor(routedQueryInfo.mappedNode) }} />
                      <span>
                        Prefix <strong>"{query.trim().toLowerCase()}"</strong> → hashed to{' '}
                        <strong style={{ color: nodeColor(routedQueryInfo.mappedNode) }}>{routedQueryInfo.mappedNode}</strong>
                      </span>
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="card-footer">
              <HelpCircle size={13} style={{ color: 'var(--neon-cyan)', flexShrink: 0 }} />
              <p><strong>Debounce (300ms)</strong>: API calls fire only after 300ms of inactivity — reduces backend load by ~80%.</p>
            </div>
          </div>

          {/* TRENDING */}
          <div className="dashboard-card">
            <div className="card-header">
              <div className="card-icon icon-amber"><TrendingUp size={15} /></div>
              <h2>Trending Searches</h2>
            </div>
            <div className="card-content">
              {trending.length > 0 ? (
                <div className="trending-table-wrapper">
                  <table className="trending-table">
                    <thead>
                      <tr>
                        <th>Query</th>
                        <th className="align-right">Lifetime</th>
                        <th className="align-right">Recent</th>
                        <th className="align-right">Score</th>
                      </tr>
                    </thead>
                    <tbody>
                      {trending.map((item, idx) => {
                        const score = Math.round((0.7 * item.totalCount + 0.3 * item.recentCount) * 10) / 10;
                        return (
                          <tr key={item.id} className="trending-row">
                            <td className="query-text">
                              <span className="trending-rank">#{idx + 1}</span>
                              {item.queryText}
                            </td>
                            <td className="align-right font-mono">{item.totalCount}</td>
                            <td className="align-right text-cyan font-mono">{item.recentCount}</td>
                            <td className="align-right text-green font-bold font-mono">{score}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              ) : <p className="empty-message">No queries in database yet.</p>}
            </div>
            <div className="card-footer">
              <Cpu size={13} style={{ color: 'var(--neon-amber)', flexShrink: 0 }} />
              <p><strong>Score</strong> = <code>0.7 × lifetime + 0.3 × recent</code> — balances history with real-time spikes.</p>
            </div>
          </div>

          {/* HISTORY */}
          <div className="dashboard-card">
            <div className="card-header">
              <div className="card-icon icon-purple"><ShieldCheck size={15} /></div>
              <h2>Recent Submissions</h2>
            </div>
            <div className="card-content">
              {searchHistory.length > 0 ? (
                <ul className="history-list">
                  {searchHistory.map((h, i) => (
                    <li key={i} className="history-item">
                      <Zap size={13} className="text-cyan" />
                      <span>Searched: <strong>"{h.query}"</strong></span>
                      <span className="history-time">{h.timestamp}</span>
                    </li>
                  ))}
                </ul>
              ) : <p className="empty-message">Submit a search above to watch live write-buffering.</p>}
            </div>
          </div>
        </section>

        {/* RIGHT COLUMN */}
        <section className="dashboard-column">

          {/* HASHING RING */}
          <div className="dashboard-card">
            <div className="card-header">
              <div className="card-icon icon-purple"><Cpu size={15} /></div>
              <h2>Consistent Hashing Ring</h2>
            </div>

            <div className="ring-visualization-container">
              <div className="svg-wrapper">
                <svg viewBox="0 0 100 100" className="hashing-svg">
                  <defs>
                    <linearGradient id="ringGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stopColor="#00e5ff" stopOpacity="0.6" />
                      <stop offset="100%" stopColor="#b845ff" stopOpacity="0.6" />
                    </linearGradient>
                  </defs>
                  <circle cx="50" cy="50" r="40" className="main-ring-path" />
                  <circle cx="50" cy="50" r="40" className="ring-glow-path" />

                  {ringState.map((vn, i) => {
                    const { cx, cy } = getCoords(vn.hash);
                    return (
                      <circle key={i} cx={cx} cy={cy} r="1.8" fill={nodeColor(vn.physicalNode)} className="vn-dot">
                        <title>{`${vn.virtualNodeName}\nHash: ${vn.hash}`}</title>
                      </circle>
                    );
                  })}

                  {routedQueryInfo?.query && (() => {
                    const h = clientHash(routedQueryInfo.query.toLowerCase().trim());
                    const { cx, cy } = getCoords(h);
                    return (
                      <>
                        <line x1="50" y1="50" x2={cx} y2={cy} className="routing-ray" />
                        <circle cx={cx} cy={cy} r="3" fill="#ffd600" className="routed-query-dot">
                          <title>{`Query: "${routedQueryInfo.query}"\nMapped: ${routedQueryInfo.mappedNode}`}</title>
                        </circle>
                      </>
                    );
                  })()}
                </svg>
                <div className="ring-center-label">
                  <span className="center-title">Nodes</span>
                  <span className="center-value">{activeNodes.length}</span>
                </div>
              </div>

              {routedQueryInfo && (
                <div className="routed-box">
                  <div className="routed-badge">
                    <span className="pulse-yellow"></span>
                    <span>Prefix: <strong>"{routedQueryInfo.query}"</strong></span>
                  </div>
                  <div className="routed-details">
                    <span>Hash: <code>{clientHash(routedQueryInfo.query.toLowerCase().trim())}</code></span>
                    <span>→ <strong style={{ color: nodeColor(routedQueryInfo.mappedNode) }}>{routedQueryInfo.mappedNode}</strong></span>
                  </div>
                </div>
              )}
            </div>

            <div className="node-manager-panel">
              <h3>Cluster Nodes</h3>
              <form onSubmit={handleAddNode} className="add-node-form">
                <input
                  type="text"
                  placeholder="e.g. RedisNode4"
                  value={newNodeName}
                  onChange={e => setNewNodeName(e.target.value)}
                  className="node-input"
                  id="new-node-input"
                />
                <button type="submit" className="add-node-btn" id="add-node-btn">
                  <Plus size={14} /> Add
                </button>
              </form>
              <div className="active-nodes-list">
                {activeNodes.map(node => (
                  <div key={node} className="node-chip" style={{ borderLeftColor: nodeColor(node) }}>
                    <span className="node-chip-name">{node}</span>
                    {activeNodes.length > 1 && (
                      <button onClick={() => handleRemoveNode(node)} className="delete-node-btn" title="Remove node">
                        <Trash2 size={11} />
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* WRITE BUFFER */}
          <div className="dashboard-card">
            <div className="card-header">
              <div className="card-icon icon-green"><HardDrive size={15} /></div>
              <h2>Write Buffer Monitor</h2>
            </div>
            <div className="card-content">
              {Object.keys(buffer).length > 0 ? (
                <div className="buffer-visual-box">
                  <div className="buffer-badge">
                    <span className="pulse-green"></span>
                    IN MEMORY — Flushes every 30s
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
                  <Loader2 size={15} className="icon-spin text-muted" />
                  <span>Buffer empty — search queries accumulate here before flushing.</span>
                </div>
              )}
            </div>
            <div className="card-footer">
              <HelpCircle size={13} style={{ color: 'var(--neon-green)', flexShrink: 0 }} />
              <p><strong>Batch Persist</strong>: Searches are held in RAM. Every 30s a background job bulk-writes to Postgres, shielding it from constant disk I/O.</p>
            </div>
          </div>

          {/* CACHE TELEMETRY */}
          <div className="dashboard-card">
            <div className="card-header">
              <div className="card-icon icon-cyan"><Database size={15} /></div>
              <h2>Cache Telemetry</h2>
              <button onClick={handleResetCacheStats} className="reset-stats-btn" title="Reset stats" id="reset-stats-btn">
                <RefreshCw size={13} />
              </button>
            </div>
            <div className="card-content">
              <div className="stats-row">
                <div className="stat-card">
                  <span className="stat-label">Cache Hits</span>
                  <span className="stat-value text-green">{cacheStats.hits}</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label">Cache Misses</span>
                  <span className="stat-value text-pink">{cacheStats.misses}</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label">Hit Rate</span>
                  <span className={`stat-value font-bold ${cacheStats.hitRate >= 75 ? 'text-green' : 'text-amber'}`}>
                    {cacheStats.hitRate}%
                  </span>
                </div>
              </div>
            </div>
            <div className="card-footer">
              <HelpCircle size={13} style={{ color: 'var(--neon-cyan)', flexShrink: 0 }} />
              <p><strong>Cache-Aside</strong>: Reads check Redis first. On a miss, Postgres is queried and Redis is populated. Flush events invalidate stale keys.</p>
            </div>
          </div>

        </section>
      </main>
    </div>
  );
}
