package com.skgis.rules;

import com.skgis.graph.GraphRepository;
import com.skgis.model.RiskReason;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ClusterSizeRule implements RiskRule {

    private static final int CLUSTER_SIZE_THRESHOLD = 25;

    @Override
    public String getRuleName() {
        return "ClusterSizeRule";
    }

    @Override
    public Optional<RiskReason> evaluate(List<String> clusterEntityIds, GraphRepository graphRepository) {
        if (clusterEntityIds == null) return Optional.empty();

        List<String> customerIds = clusterEntityIds.stream()
                .filter(id -> id != null && id.startsWith("C"))
                .collect(Collectors.toList());

        if (customerIds.size() >= CLUSTER_SIZE_THRESHOLD) {
            String explanation = String.format("High cluster density detected with %d linked customer entities", customerIds.size());
            return Optional.of(new RiskReason(getRuleName(), explanation, new ArrayList<>(customerIds)));
        }

        return Optional.empty();
    }
}
