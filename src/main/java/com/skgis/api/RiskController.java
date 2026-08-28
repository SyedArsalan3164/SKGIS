package com.skgis.api;

import com.skgis.graph.GraphRepository;
import com.skgis.model.FlaggedCluster;
import com.skgis.model.RiskReason;
import com.skgis.rules.RiskRuleEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/risk")
@CrossOrigin(origins = "*")
public class RiskController {

    private final RiskRuleEngine riskRuleEngine;
    private final GraphRepository graphRepository;

    public RiskController(RiskRuleEngine riskRuleEngine, GraphRepository graphRepository) {
        this.riskRuleEngine = riskRuleEngine;
        this.graphRepository = graphRepository;
    }

    @PostMapping("/detect")
    public ResponseEntity<Map<String, Object>> detectRiskClusters() {
        List<FlaggedCluster> flagged = riskRuleEngine.detectAndFlagRiskClusters();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "count", flagged.size(),
                "clusters", flagged
        ));
    }

    @GetMapping("/flagged-clusters")
    public ResponseEntity<List<FlaggedCluster>> getFlaggedClusters() {
        String cypher = """
            MATCH (rc:RiskCluster)
            OPTIONAL MATCH (c:Customer)-[:MEMBER_OF]->(rc)
            WITH rc, collect(c.id) AS customers
            RETURN rc.id AS clusterId, rc.reason AS reason, rc.score AS score, customers
            ORDER BY rc.score DESC, size(customers) DESC, rc.id ASC
            LIMIT 10
        """;

        List<Map<String, Object>> rows = graphRepository.executeCypher(cypher, Map.of());
        List<FlaggedCluster> list = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String clusterId = (String) row.get("clusterId");
            String reasonText = (String) row.get("reason");
            double score = ((Number) row.get("score")).doubleValue();
            @SuppressWarnings("unchecked")
            List<String> customerIds = (List<String>) row.get("customers");

            List<RiskReason> reasons = parseReasonText(reasonText);
            list.add(new FlaggedCluster(clusterId, score, reasons, customerIds, null));
        }

        if (list.isEmpty() && graphRepository.isFallbackMode()) {
            for (Map<String, Object> rc : graphRepository.getInMemoryRiskClusters().values()) {
                String clusterId = (String) rc.get("clusterId");
                String reasonText = (String) rc.get("reason");
                double score = ((Number) rc.get("score")).doubleValue();
                @SuppressWarnings("unchecked")
                List<String> customerIds = (List<String>) rc.get("customers");
                list.add(new FlaggedCluster(clusterId, score, parseReasonText(reasonText), customerIds, null));
            }
            list.sort((a, b) -> {
                int scoreComp = Double.compare(b.getScore(), a.getScore());
                if (scoreComp != 0) return scoreComp;
                int sizeA = a.getCustomerIds() != null ? a.getCustomerIds().size() : 0;
                int sizeB = b.getCustomerIds() != null ? b.getCustomerIds().size() : 0;
                int sizeComp = Integer.compare(sizeB, sizeA);
                if (sizeComp != 0) return sizeComp;
                return a.getClusterId().compareTo(b.getClusterId());
            });
            if (list.size() > 10) {
                list = list.subList(0, 10);
            }
        }

        return ResponseEntity.ok(list);
    }

    private List<RiskReason> parseReasonText(String reasonText) {
        if (reasonText == null || reasonText.isBlank()) return Collections.emptyList();
        String[] parts = reasonText.split(" \\| ");
        return Arrays.stream(parts)
                .map(p -> new RiskReason("RuleEngineFlag", p, Collections.emptyList()))
                .collect(Collectors.toList());
    }
}
