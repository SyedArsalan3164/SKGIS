package com.skgis.rules;

import com.skgis.graph.GraphRepository;
import com.skgis.model.RiskReason;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SharedAccountRule implements RiskRule {

    private static final int SHARED_ACCOUNT_THRESHOLD = 2;

    @Override
    public String getRuleName() {
        return "SharedAccountRule";
    }

    @Override
    public Optional<RiskReason> evaluate(List<String> clusterEntityIds, GraphRepository graphRepository) {
        if (clusterEntityIds == null || clusterEntityIds.isEmpty()) {
            return Optional.empty();
        }

        String cypher = """
            MATCH (c:Customer)-[:OWNS_ACCOUNT]->(a:BankAccount)
            WHERE c.id IN $ids OR a.id IN $ids
            WITH a, collect(DISTINCT c.id) AS customers, count(DISTINCT c.id) AS custCount
            WHERE custCount >= $threshold
            RETURN a.id AS accountId, customers, custCount ORDER BY custCount DESC
        """;

        List<Map<String, Object>> results = graphRepository.executeCypher(
                cypher,
                Map.of("ids", clusterEntityIds, "threshold", SHARED_ACCOUNT_THRESHOLD)
        );

        if (!results.isEmpty()) {
            Map<String, Object> firstMatch = results.get(0);
            String accountId = (String) firstMatch.get("accountId");
            long custCount = ((Number) firstMatch.get("custCount")).longValue();
            @SuppressWarnings("unchecked")
            List<String> customerList = (List<String>) firstMatch.get("customers");

            String explanation = String.format("%d customers share Bank Account %s", custCount, accountId);
            List<String> evidence = new ArrayList<>();
            evidence.add(accountId);
            if (customerList != null) evidence.addAll(customerList);

            return Optional.of(new RiskReason(getRuleName(), explanation, evidence));
        }

        return Optional.empty();
    }
}
