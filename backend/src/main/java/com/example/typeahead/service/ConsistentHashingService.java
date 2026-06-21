package com.example.typeahead.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <h1>ConsistentHashingService</h1>
 * 
 * <h3>Why this class exists:</h3>
 * This service implements the <b>Consistent Hashing</b> algorithm to route search queries (keys)
 * to a specific cache server (simulated Redis nodes).
 * 
 * <h3>What problem it solves:</h3>
 * In a distributed caching system:
 * <ul>
 *   <li><b>Normal Hashing</b>: <code>node = hash(key) % N</code> (where N is the number of cache servers).
 *       If N changes (a node crashes or a new node is added), the formula changes. Consequently,
 *       <b>almost all keys (approx. N / (N + 1)) map to different nodes</b>. This invalidates the entire
 *       cache, leading to a cache stampede that can crash the underlying database.</li>
 *   <li><b>Consistent Hashing</b>: Resolves this by mapping both servers and keys onto a circular ring.
 *       When a node is added or removed, <b>only a fraction of the keys (1/N) need to be moved</b>.</li>
 * </ul>
 * 
 * <h3>How it works internally:</h3>
 * <ol>
 *   <li>We hash physical servers multiple times using different virtual node indexes (e.g., "RedisNode1-VN-0", "RedisNode1-VN-1")
 *       to ensure even distribution.</li>
 *   <li>These hashes are stored in a {@link TreeMap} which represents the circular ring. The keys are the 32-bit integer hashes,
 *       and the values are the physical node names.</li>
 *   <li>To find the node responsible for a query, we hash the query, locate the closest higher hash on the ring using
 *       <code>TreeMap.ceilingKey()</code> (which is a O(log K) operation where K is the number of virtual nodes), and wrap around
 *       to the first key if no higher hash exists.</li>
 * </ol>
 * 
 * <h3>ASCII Ring Representation:</h3>
 * <pre>
 *                0 (Hash Space)
 *           .---' `---.
 *      VN-0 |         | VN-2 (Node 2)
 *  (Node 1) \         /
 *            `---. .---'
 *               VN-1 (Node 3)
 * </pre>
 */
@Service
public class ConsistentHashingService {

    private static final Logger logger = LoggerFactory.getLogger(ConsistentHashingService.class);
    
    // Number of virtual nodes per physical node to prevent uneven data distribution ("hotspots")
    private static final int VIRTUAL_NODES_PER_PHYSICAL = 10;

    // The Hash Ring represented by a TreeMap (sorted by hash value)
    private final TreeMap<Integer, VirtualNodeInfo> ring = new TreeMap<>();
    
    // Track unique physical nodes currently active
    private final Set<String> physicalNodes = new CopyOnWriteArraySet<>();

    public ConsistentHashingService() {
        // Initialize with default three simulated Redis nodes
        addNode("RedisNode1");
        addNode("RedisNode2");
        addNode("RedisNode3");
    }

    /**
     * Adds a physical cache node to the ring, generating its virtual nodes.
     */
    public synchronized void addNode(String nodeName) {
        if (physicalNodes.contains(nodeName)) {
            return;
        }
        physicalNodes.add(nodeName);
        
        for (int i = 0; i < VIRTUAL_NODES_PER_PHYSICAL; i++) {
            String vnName = nodeName + "-VN-" + i;
            int hashVal = generateHash(vnName);
            ring.put(hashVal, new VirtualNodeInfo(nodeName, vnName, hashVal));
            logger.info("Added Virtual Node [{}] with hash [{}] to ring", vnName, hashVal);
        }
        logger.info("Successfully added Physical Node [{}] to the consistent hashing ring", nodeName);
    }

    /**
     * Removes a physical cache node from the ring, removing all its virtual nodes.
     */
    public synchronized void removeNode(String nodeName) {
        if (!physicalNodes.contains(nodeName)) {
            return;
        }
        physicalNodes.remove(nodeName);
        
        // Remove all virtual nodes associated with this physical node
        ring.values().removeIf(vn -> vn.getPhysicalNode().equals(nodeName));
        logger.info("Successfully removed Physical Node [{}] and all its virtual nodes from the ring", nodeName);
    }

    /**
     * Routes a search query (key) to its responsible cache node on the ring.
     */
    public String routeKey(String key) {
        if (ring.isEmpty()) {
            logger.warn("Consistent Hashing Ring is empty! Cannot route key [{}]", key);
            return null;
        }
        
        int keyHash = generateHash(key);
        
        // ceilingKey returns the least key greater than or equal to the given key, or null if there is no such key.
        Integer targetHash = ring.ceilingKey(keyHash);
        
        // If targetHash is null, it means we have reached the end of the ring. Wrap around to the start (firstKey).
        if (targetHash == null) {
            targetHash = ring.firstKey();
        }
        
        VirtualNodeInfo vnInfo = ring.get(targetHash);
        String selectedNode = vnInfo.getPhysicalNode();
        
        // CRITICAL EDUCATIONAL LOG
        logger.info("Routing Key [{}] (Hash: {}) -> Clockwise Node: [{}] (VN: {}, Hash: {})", 
                key, keyHash, selectedNode, vnInfo.getVirtualNodeName(), targetHash);
        
        return selectedNode;
    }

    /**
     * Helper to get list of active physical nodes.
     */
    public List<String> getActiveNodes() {
        return new ArrayList<>(physicalNodes);
    }

    /**
     * Returns the current state of the ring for visualization on the UI.
     */
    public synchronized List<VirtualNodeInfo> getRingState() {
        return new ArrayList<>(ring.values());
    }

    /**
     * Generates a 32-bit integer hash from a string key using MD5.
     * MD5 distributes hashes more uniformly than standard Java String.hashCode().
     */
    private int generateHash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            
            // Convert first 4 bytes of MD5 to 32-bit integer (big-endian)
            return ((digest[3] & 0xFF) << 24)
                    | ((digest[2] & 0xFF) << 16)
                    | ((digest[1] & 0xFF) << 8)
                    | (digest[0] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            // Fallback to Java hashCode in case MD5 is unavailable (unlikely)
            logger.error("MD5 algorithm not found, falling back to standard hashCode", e);
            return key.hashCode();
        }
    }

    /**
     * Inner class representing virtual node details on the ring.
     */
    public static class VirtualNodeInfo {
        private final String physicalNode;
        private final String virtualNodeName;
        private final int hash;

        public VirtualNodeInfo(String physicalNode, String virtualNodeName, int hash) {
            this.physicalNode = physicalNode;
            this.virtualNodeName = virtualNodeName;
            this.hash = hash;
        }

        public String getPhysicalNode() {
            return physicalNode;
        }

        public String getVirtualNodeName() {
            return virtualNodeName;
        }

        public int getHash() {
            return hash;
        }
    }
}

/*
 ==================================================================================
 VIVA NOTES: CONSISTENT HASHING EXPLAINED
 ==================================================================================
 1. What is the Hash Space?
    The hash space ranges from Integer.MIN_VALUE (-2,147,483,648) to Integer.MAX_VALUE (2,147,483,647) 
    representing a 32-bit circular space.
 
 2. Why Virtual Nodes?
    If we map only physical nodes directly (e.g. 3 nodes), they might hash to positions very close to each other.
    This creates an uneven partition where one node covers 80% of the hash ring (hotspot).
    Virtual nodes divide the ring into smaller segments, distributing keys uniformly across physical servers.

 3. O-notation complexity:
    - Adding a node: O(V log(V*N)) where V is virtual nodes count and N is physical nodes.
    - Removing a node: O(V log(V*N)).
    - Routing a key: O(log(V*N)) due to binary search inside TreeMap ceilingKey().
 ==================================================================================
*/
