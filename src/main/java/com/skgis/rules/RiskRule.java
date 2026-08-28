package com.skgis.rules;

import com.skgis.graph.GraphRepository;
import com.skgis.model.RiskReason;

import java.util.List;
import java.util.Optional;

public interface RiskRule {
    String getRuleName();
    Optional<RiskReason> evaluate(List<String> clusterEntityIds, GraphRepository graphRepository);
}
