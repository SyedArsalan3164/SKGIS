package com.skgis.api;

import com.skgis.graph.GraphRepository;
import com.skgis.model.GraphData;
import com.skgis.model.RiskReason;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/graph")
@CrossOrigin(origins = "*")
public class GraphController {

    private final GraphRepository graphRepository;

    public GraphController(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @GetMapping("/cluster/{clusterId}")
    public ResponseEntity<GraphData> getClusterSubgraph(@PathVariable String clusterId) {
        String clusterCypher = """
            MATCH (rc:RiskCluster {id: $clusterId})<-[:MEMBER_OF]-(c:Customer)
            OPTIONAL MATCH (c)-[:USED_DEVICE]->(d:Device)
            OPTIONAL MATCH (c)-[:OWNS_ACCOUNT]->(a:BankAccount)
            OPTIONAL MATCH (c)-[:PERFORMED]->(t:Transaction)-[:PAID_TO]->(dest)
            RETURN rc, c, d, a, t, dest
        """;

        List<Map<String, Object>> rows = graphRepository.executeCypher(clusterCypher, Map.of("clusterId", clusterId));

        if (rows.isEmpty()) {
            return getInMemoryClusterSubgraph(clusterId);
        }

        Set<GraphData.NodeDto> nodes = new HashSet<>();
        Set<GraphData.EdgeDto> edges = new HashSet<>();
        Set<String> nodeIds = new HashSet<>();

        String reasonText = "";
        double score = 0.85;

        nodes.add(new GraphData.NodeDto(clusterId, clusterId, "RiskCluster"));
        nodeIds.add(clusterId);

        for (Map<String, Object> row : rows) {
            Map<String, Object> rcMap = toMap(row.get("rc"));
            if (rcMap != null) {
                reasonText = (String) rcMap.getOrDefault("reason", "");
                if (rcMap.containsKey("score")) {
                    score = ((Number) rcMap.get("score")).doubleValue();
                }
            }

            Map<String, Object> cMap = toMap(row.get("c"));
            if (cMap != null) {
                String cid = (String) cMap.get("id");
                if (cid != null && nodeIds.add(cid)) {
                    nodes.add(new GraphData.NodeDto(cid, cid, "Customer"));
                }
                if (cid != null) {
                    edges.add(new GraphData.EdgeDto(cid, clusterId, "MEMBER_OF"));
                }

                Map<String, Object> dMap = toMap(row.get("d"));
                if (dMap != null) {
                    String did = (String) dMap.get("id");
                    if (did != null && nodeIds.add(did)) {
                        nodes.add(new GraphData.NodeDto(did, did, "Device"));
                    }
                    if (cid != null && did != null) {
                        edges.add(new GraphData.EdgeDto(cid, did, "USED_DEVICE"));
                    }
                }

                Map<String, Object> aMap = toMap(row.get("a"));
                if (aMap != null) {
                    String aid = (String) aMap.get("id");
                    if (aid != null && nodeIds.add(aid)) {
                        nodes.add(new GraphData.NodeDto(aid, aid, "BankAccount"));
                    }
                    if (cid != null && aid != null) {
                        edges.add(new GraphData.EdgeDto(cid, aid, "OWNS_ACCOUNT"));
                    }
                }

                Map<String, Object> tMap = toMap(row.get("t"));
                if (tMap != null) {
                    String tid = (String) tMap.get("id");
                    if (tid != null && nodeIds.add(tid)) {
                        nodes.add(new GraphData.NodeDto(tid, tid, "Transaction"));
                    }
                    if (cid != null && tid != null) {
                        edges.add(new GraphData.EdgeDto(cid, tid, "PERFORMED"));
                    }

                    Map<String, Object> destMap = toMap(row.get("dest"));
                    if (destMap != null) {
                        String destId = (String) destMap.get("id");
                        if (destId != null) {
                            String destType = destId.startsWith("M") ? "Merchant" : "Customer";
                            if (nodeIds.add(destId)) {
                                nodes.add(new GraphData.NodeDto(destId, destId, destType));
                            }
                            if (tid != null) {
                                edges.add(new GraphData.EdgeDto(tid, destId, "PAID_TO"));
                            }
                        }
                    }
                }
            }
        }

        List<RiskReason> reasons = List.of(new RiskReason("LouvainRuleEngine", reasonText, new ArrayList<>(nodeIds)));
        return ResponseEntity.ok(new GraphData(clusterId, score, reasons, new ArrayList<>(nodes), new ArrayList<>(edges)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        if (obj instanceof org.neo4j.driver.types.Node node) {
            return node.asMap();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<GraphData> getInMemoryClusterSubgraph(String clusterId) {
        Map<String, Map<String, Object>> riskClusters = graphRepository.getInMemoryRiskClusters();
        Map<String, Object> clusterObj = riskClusters.get(clusterId);
        if (clusterObj == null) {
            return ResponseEntity.notFound().build();
        }

        String reasonText = (String) clusterObj.getOrDefault("reason", "Shared Resource Fraud Ring");
        double score = ((Number) clusterObj.getOrDefault("score", 0.85)).doubleValue();
        List<String> customerIds = (List<String>) clusterObj.getOrDefault("customers", Collections.emptyList());
        Set<String> custSet = new HashSet<>(customerIds);

        Map<String, GraphData.NodeDto> nodesMap = new LinkedHashMap<>();
        Map<String, GraphData.EdgeDto> edgesMap = new LinkedHashMap<>();
        Set<String> nodeIds = new LinkedHashSet<>();

        // 1. Root Cluster Node
        nodesMap.put(clusterId, new GraphData.NodeDto(clusterId, clusterId, "RiskCluster"));
        nodeIds.add(clusterId);

        // 2. Map resource -> connected customers within cluster
        Map<String, Set<String>> resourceToCustMap = new HashMap<>();
        Map<String, String> resourceTypeMap = new HashMap<>();
        for (Map<String, Object> edge : graphRepository.getInMemoryEdges()) {
            String type = (String) edge.get("type");
            if ("USED_DEVICE".equals(type) || "OWNS_ACCOUNT".equals(type)) {
                String c = (String) edge.get("from");
                String r = (String) edge.get("to");
                if (custSet.contains(c)) {
                    resourceToCustMap.computeIfAbsent(r, k -> new HashSet<>()).add(c);
                    resourceTypeMap.put(r, type);
                }
            }
        }

        // 3. Select top shared resources (with fallback for clean 1-to-1 resources)
        List<Map.Entry<String, Set<String>>> topResources = resourceToCustMap.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(4)
                .toList();

        if (topResources.isEmpty()) {
            topResources = resourceToCustMap.entrySet().stream()
                    .filter(e -> !e.getValue().isEmpty())
                    .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                    .limit(6)
                    .toList();
        }

        // 4. Build formal connected graph: Resource -> Customers -> Cluster
        for (Map.Entry<String, Set<String>> entry : topResources) {
            String resourceId = entry.getKey();
            String edgeType = resourceTypeMap.get(resourceId);
            String nodeType = determineNodeType(resourceId);

            nodesMap.put(resourceId, new GraphData.NodeDto(resourceId, resourceId, nodeType));
            nodeIds.add(resourceId);

            String flagEdgeKey = clusterId + "->" + resourceId + ":FLAGGED_RESOURCE";
            edgesMap.put(flagEdgeKey, new GraphData.EdgeDto(clusterId, resourceId, "FLAGGED_RESOURCE"));

            // Sample up to 6 connected customers for this exact resource
            List<String> connectedCustomers = entry.getValue().stream().limit(6).toList();
            for (String cid : connectedCustomers) {
                nodesMap.put(cid, new GraphData.NodeDto(cid, cid, "Customer"));
                nodeIds.add(cid);

                if ("OWNS_ACCOUNT".equals(edgeType)) {
                    String eKey = cid + "->" + resourceId + ":OWNS_ACCOUNT";
                    edgesMap.put(eKey, new GraphData.EdgeDto(cid, resourceId, "OWNS_ACCOUNT"));
                } else {
                    String eKey = cid + "->" + resourceId + ":USED_DEVICE";
                    edgesMap.put(eKey, new GraphData.EdgeDto(cid, resourceId, "USED_DEVICE"));
                }

                String memberEdgeKey = cid + "->" + clusterId + ":MEMBER_OF";
                edgesMap.put(memberEdgeKey, new GraphData.EdgeDto(cid, clusterId, "MEMBER_OF"));
            }
        }

        List<RiskReason> reasons = List.of(new RiskReason("LouvainRuleEngine", reasonText, new ArrayList<>(nodeIds)));
        int totalCustomers = customerIds.size();
        return ResponseEntity.ok(new GraphData(clusterId, score, reasons, new ArrayList<>(nodesMap.values()), new ArrayList<>(edgesMap.values()), totalCustomers));
    }

    private String determineNodeType(String id) {
        if (id.startsWith("DEV-")) return "Device";
        if (id.startsWith("ACC-")) return "BankAccount";
        if (id.startsWith("TXN-")) return "Transaction";
        if (id.startsWith("M")) return "Merchant";
        if (id.startsWith("C")) return "Customer";
        return "Entity";
    }

    @GetMapping("/{entityId}/subgraph")
    public ResponseEntity<GraphData> getEntitySubgraph(@PathVariable String entityId) {
        return ResponseEntity.ok(new GraphData(entityId, 0.5, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
    }
}
