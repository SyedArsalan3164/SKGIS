package com.skgis.rules;

import com.skgis.graph.GraphRepository;
import com.skgis.model.RiskReason;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SharedDeviceRule implements RiskRule {

    private static final int SHARED_DEVICE_THRESHOLD = 3;

    @Override
    public String getRuleName() {
        return "SharedDeviceRule";
    }

    @Override
    public Optional<RiskReason> evaluate(List<String> clusterEntityIds, GraphRepository graphRepository) {
        if (clusterEntityIds == null || clusterEntityIds.isEmpty()) {
            return Optional.empty();
        }

        String cypher = """
            MATCH (c:Customer)-[:USED_DEVICE]->(d:Device)
            WHERE c.id IN $ids OR d.id IN $ids
            WITH d, collect(DISTINCT c.id) AS customers, count(DISTINCT c.id) AS custCount
            WHERE custCount >= $threshold
            RETURN d.id AS deviceId, customers, custCount ORDER BY custCount DESC
        """;

        List<Map<String, Object>> results = graphRepository.executeCypher(
                cypher,
                Map.of("ids", clusterEntityIds, "threshold", SHARED_DEVICE_THRESHOLD)
        );

        if (!results.isEmpty()) {
            Map<String, Object> firstMatch = results.get(0);
            String deviceId = (String) firstMatch.get("deviceId");
            long custCount = ((Number) firstMatch.get("custCount")).longValue();
            @SuppressWarnings("unchecked")
            List<String> customerList = (List<String>) firstMatch.get("customers");

            String explanation = String.format("%d customers share Device %s", custCount, deviceId);
            List<String> evidence = new ArrayList<>();
            evidence.add(deviceId);
            if (customerList != null) evidence.addAll(customerList);

            return Optional.of(new RiskReason(getRuleName(), explanation, evidence));
        }

        return Optional.empty();
    }
}
