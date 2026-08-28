package com.skgis.graph;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class GraphRepository {
    private static final Logger log = LoggerFactory.getLogger(GraphRepository.class);

    private final Driver driver;

    // In-memory fallback graph store when Neo4j container is offline
    private final Map<String, Map<String, Object>> inMemoryNodes = new HashMap<>();
    private final List<Map<String, Object>> inMemoryEdges = new ArrayList<>();
    private final Map<String, Map<String, Object>> inMemoryRiskClusters = new HashMap<>();
    private boolean useFallback = false;

    public GraphRepository(Driver driver) {
        this.driver = driver;
    }

    public boolean isFallbackMode() {
        return useFallback;
    }

    public void setFallbackMode(boolean fallback) {
        this.useFallback = fallback;
    }

    public void applySchemaConstraints() {
        if (useFallback) return;
        List<String> constraints = List.of(
                "CREATE CONSTRAINT customer_id IF NOT EXISTS FOR (c:Customer) REQUIRE c.id IS UNIQUE",
                "CREATE CONSTRAINT merchant_id IF NOT EXISTS FOR (m:Merchant) REQUIRE m.id IS UNIQUE",
                "CREATE CONSTRAINT device_id IF NOT EXISTS FOR (d:Device) REQUIRE d.id IS UNIQUE",
                "CREATE CONSTRAINT account_id IF NOT EXISTS FOR (a:BankAccount) REQUIRE a.id IS UNIQUE",
                "CREATE CONSTRAINT txn_id IF NOT EXISTS FOR (t:Transaction) REQUIRE t.id IS UNIQUE"
        );

        try (Session session = driver.session()) {
            for (String cypher : constraints) {
                session.run(cypher);
            }
            log.info("Schema constraints successfully verified/created in Neo4j.");
        } catch (Exception e) {
            log.warn("Neo4j database connection unavailable ({}). Operating in In-Memory Standalone Graph mode.", e.getMessage());
            this.useFallback = true;
        }
    }

    public List<Map<String, Object>> executeCypher(String cypher, Map<String, Object> params) {
        if (useFallback) {
            return executeInMemoryCypherQuery(cypher, params);
        }
        try (Session session = driver.session()) {
            var result = session.run(cypher, params);
            List<Map<String, Object>> rows = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                rows.add(record.asMap());
            }
            return rows;
        } catch (Exception e) {
            log.warn("Neo4j execution failed ({}); switching to In-Memory Graph store.", e.getMessage());
            this.useFallback = true;
            return executeInMemoryCypherQuery(cypher, params);
        }
    }

    public void executeWriteCypher(String cypher, Map<String, Object> params) {
        if (useFallback) return;
        try (Session session = driver.session()) {
            session.run(cypher, params);
        } catch (Exception e) {
            log.warn("Neo4j write failed ({}); operating in In-Memory Standalone Graph mode.", e.getMessage());
            this.useFallback = true;
        }
    }

    public void clearGraph() {
        inMemoryNodes.clear();
        inMemoryEdges.clear();
        inMemoryRiskClusters.clear();
        if (!useFallback) {
            try {
                executeWriteCypher("MATCH (n) DETACH DELETE n", Collections.emptyMap());
            } catch (Exception e) {
                log.warn("Failed to clear Neo4j graph: {}", e.getMessage());
            }
        }
        log.info("Graph memory successfully cleared for fresh dataset ingestion.");
    }

    public void addInMemoryNode(String label, String id, Map<String, Object> properties) {
        Map<String, Object> nodeProps = new HashMap<>();
        if (properties != null) nodeProps.putAll(properties);
        nodeProps.put("id", id);
        nodeProps.put("type", label);
        inMemoryNodes.put(label + ":" + id, nodeProps);
    }

    public void addInMemoryEdge(String fromId, String toId, String type) {
        Map<String, Object> edge = Map.of("from", fromId, "source", fromId, "to", toId, "target", toId, "type", type);
        if (!inMemoryEdges.contains(edge)) {
            inMemoryEdges.add(edge);
        }
    }

    public void mergeRiskCluster(String clusterId, String reason, double score, List<String> customerIds) {
        Map<String, Object> clusterObj = Map.of(
                "id", clusterId,
                "clusterId", clusterId,
                "reason", reason,
                "score", score,
                "customers", customerIds
        );
        inMemoryRiskClusters.put(clusterId, clusterObj);

        if (!useFallback) {
            String cypher = """
                MERGE (rc:RiskCluster {id: $clusterId})
                SET rc.reason = $reason, rc.score = $score, rc.createdAt = datetime()
                WITH rc
                UNWIND $customerIds AS cid
                MATCH (c:Customer {id: cid})
                MERGE (c)-[:MEMBER_OF]->(rc)
            """;
            executeWriteCypher(cypher, Map.of("clusterId", clusterId, "reason", reason, "score", score, "customerIds", customerIds));
        }
    }

    public Map<String, Map<String, Object>> getInMemoryNodes() { return inMemoryNodes; }
    public List<Map<String, Object>> getInMemoryEdges() { return inMemoryEdges; }
    public Map<String, Map<String, Object>> getInMemoryRiskClusters() { return inMemoryRiskClusters; }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeInMemoryCypherQuery(String cypher, Map<String, Object> params) {
        List<Map<String, Object>> rows = new ArrayList<>();

        if (cypher.contains("gds.graph.drop") || cypher.contains("gds.graph.project")) {
            // Signal GDS unavailable to invoke native fallback
            throw new RuntimeException("GDS unavailable in In-Memory Standalone mode");
        } else if (cypher.contains("gds.louvain.stream") || cypher.contains("MATCH (c:Customer)-[:USED_DEVICE]->(d:Device)<-[:USED_DEVICE]-(c2:Customer)")) {
            // Build adjacency list for shared resources
            Map<String, Set<String>> adj = new HashMap<>();
            for (Map<String, Object> edge : inMemoryEdges) {
                String type = (String) edge.get("type");
                if ("USED_DEVICE".equals(type) || "OWNS_ACCOUNT".equals(type)) {
                    String u = (String) edge.get("from");
                    String v = (String) edge.get("to");
                    adj.computeIfAbsent(u, k -> new HashSet<>()).add(v);
                    adj.computeIfAbsent(v, k -> new HashSet<>()).add(u);
                }
            }

            // BFS Connected Components
            Set<String> visited = new HashSet<>();
            long commId = 1;
            for (String startNode : adj.keySet()) {
                if (visited.contains(startNode)) continue;

                Set<String> component = new HashSet<>();
                Queue<String> q = new LinkedList<>();
                q.add(startNode);
                visited.add(startNode);

                while (!q.isEmpty()) {
                    String curr = q.poll();
                    component.add(curr);
                    for (String neighbor : adj.getOrDefault(curr, Collections.emptySet())) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            q.add(neighbor);
                        }
                    }
                }

                // Collect connected component nodes
                long custCount = component.stream().filter(id -> id.startsWith("C")).count();
                if (custCount >= 1) {
                    for (String entityId : component) {
                        rows.add(Map.of("entityId", entityId, "communityId", commId, "resourceId", startNode, "customer1", entityId, "customer2", entityId));
                    }
                    commId++;
                }
            }
        } else if (cypher.contains("MATCH (c:Customer) WHERE c.id IN $ids")) {
            List<String> ids = (List<String>) params.get("ids");
            Map<String, Long> degreeMap = new HashMap<>();
            if (ids != null) {
                Set<String> idSet = new HashSet<>(ids);
                for (Map<String, Object> edge : inMemoryEdges) {
                    String from = (String) edge.get("from");
                    String to = (String) edge.get("to");
                    if (idSet.contains(from)) {
                        degreeMap.merge(to, 1L, (v1, v2) -> v1 + v2);
                    }
                    if (idSet.contains(to)) {
                        degreeMap.merge(from, 1L, (v1, v2) -> v1 + v2);
                    }
                }
            }
            degreeMap.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .ifPresent(entry -> rows.add(Map.of("resourceId", entry.getKey(), "degree", entry.getValue())));
        } else if (cypher.contains("MATCH (c:Customer)-[:USED_DEVICE]->(d:Device)")) {
            List<String> ids = (List<String>) params.get("ids");
            long threshold = params.containsKey("threshold") ? ((Number) params.get("threshold")).longValue() : 3L;

            Map<String, Set<String>> deviceToCust = new HashMap<>();
            for (Map<String, Object> edge : inMemoryEdges) {
                if ("USED_DEVICE".equals(edge.get("type"))) {
                    String c = (String) edge.get("from");
                    String d = (String) edge.get("to");
                    if (ids == null || ids.isEmpty() || ids.contains(c) || ids.contains(d)) {
                        deviceToCust.computeIfAbsent(d, k -> new HashSet<>()).add(c);
                    }
                }
            }

            for (Map.Entry<String, Set<String>> entry : deviceToCust.entrySet()) {
                if (entry.getValue().size() >= threshold) {
                    rows.add(Map.of("deviceId", entry.getKey(), "custCount", (long) entry.getValue().size(), "customers", new ArrayList<>(entry.getValue())));
                }
            }
            rows.sort((r1, r2) -> Long.compare(((Number) r2.get("custCount")).longValue(), ((Number) r1.get("custCount")).longValue()));
        } else if (cypher.contains("MATCH (c:Customer)-[:OWNS_ACCOUNT]->(a:BankAccount)")) {
            List<String> ids = (List<String>) params.get("ids");
            long threshold = params.containsKey("threshold") ? ((Number) params.get("threshold")).longValue() : 2L;

            Map<String, Set<String>> accToCust = new HashMap<>();
            for (Map<String, Object> edge : inMemoryEdges) {
                if ("OWNS_ACCOUNT".equals(edge.get("type"))) {
                    String c = (String) edge.get("from");
                    String a = (String) edge.get("to");
                    if (ids == null || ids.isEmpty() || ids.contains(c) || ids.contains(a)) {
                        accToCust.computeIfAbsent(a, k -> new HashSet<>()).add(c);
                    }
                }
            }

            for (Map.Entry<String, Set<String>> entry : accToCust.entrySet()) {
                if (entry.getValue().size() >= threshold) {
                    rows.add(Map.of("accountId", entry.getKey(), "custCount", (long) entry.getValue().size(), "customers", new ArrayList<>(entry.getValue())));
                }
            }
            rows.sort((r1, r2) -> Long.compare(((Number) r2.get("custCount")).longValue(), ((Number) r1.get("custCount")).longValue()));
        } else if (cypher.contains("MATCH (rc:RiskCluster)<-[:MEMBER_OF]-(c:Customer)")) {
            for (Map<String, Object> rc : inMemoryRiskClusters.values()) {
                rows.add(rc);
            }
        }

        return rows;
    }
}
