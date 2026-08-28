package com.skgis.rules;

import com.skgis.graph.CentralityService;
import com.skgis.graph.CommunityDetectionService;
import com.skgis.graph.GraphRepository;
import com.skgis.model.FlaggedCluster;
import com.skgis.model.RiskReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RiskRuleEngine {
    private static final Logger log = LoggerFactory.getLogger(RiskRuleEngine.class);

    private final CommunityDetectionService communityDetectionService;
    private final CentralityService centralityService;
    private final GraphRepository graphRepository;
    private final List<RiskRule> rules;

    public RiskRuleEngine(CommunityDetectionService communityDetectionService,
                          CentralityService centralityService,
                          GraphRepository graphRepository,
                          List<RiskRule> rules) {
        this.communityDetectionService = communityDetectionService;
        this.centralityService = centralityService;
        this.graphRepository = graphRepository;
        this.rules = rules;
    }

    public List<FlaggedCluster> detectAndFlagRiskClusters() {
        log.info("Starting detection pipeline: Louvain + Explainable Rules...");
        graphRepository.executeWriteCypher("MATCH (rc:RiskCluster) DETACH DELETE rc", Map.of());

        Map<Long, List<String>> communities = communityDetectionService.runLouvainCommunityDetection();
        List<FlaggedCluster> flaggedClusters = new ArrayList<>();

        int clusterCounter = 1;

        for (Map.Entry<Long, List<String>> entry : communities.entrySet()) {
            List<String> entities = entry.getValue();
            if (entities == null || entities.isEmpty()) continue;

            List<RiskReason> activeReasons = new ArrayList<>();
            for (RiskRule rule : rules) {
                Optional<RiskReason> reasonOpt = rule.evaluate(entities, graphRepository);
                reasonOpt.ifPresent(activeReasons::add);
            }

            boolean hasSharedResource = activeReasons.stream()
                    .anyMatch(r -> "SharedDeviceRule".equals(r.getRule()) || "SharedAccountRule".equals(r.getRule()));

            // Flag cluster as high-risk ONLY if shared resource rules (device/account) trigger
            if (hasSharedResource) {
                String clusterId = "CLUSTER-" + clusterCounter++;
                double score = calculateRiskScore(activeReasons, entities.size());

                List<String> customerIds = entities.stream()
                        .filter(id -> id != null && id.startsWith("C"))
                        .distinct()
                        .collect(Collectors.toList());

                String hubEntityId = centralityService.findHubEntityInCluster(entities);

                FlaggedCluster cluster = new FlaggedCluster(
                        clusterId, score, activeReasons, customerIds, hubEntityId
                );
                flaggedClusters.add(cluster);

                // Write RiskCluster back into Neo4j
                String combinedReasons = activeReasons.stream()
                        .map(r -> r.getExplanation())
                        .collect(Collectors.joining(" | "));

                graphRepository.mergeRiskCluster(clusterId, combinedReasons, score, customerIds);
                log.info("Flagged Cluster [{}] created with score {} and reasons: {}", clusterId, score, combinedReasons);
            }
        }

        log.info("Detection pipeline completed. Identified {} flagged risk clusters.", flaggedClusters.size());
        return flaggedClusters;
    }

    private double calculateRiskScore(List<RiskReason> reasons, int entityCount) {
        double baseScore = 0.5;
        double incrementPerRule = 0.2;
        double score = baseScore + (reasons.size() * incrementPerRule);

        if (entityCount > 5) score += 0.1;
        return Math.min(1.0, Math.round(score * 100.0) / 100.0);
    }
}
