package com.skgis.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CentralityService {
    private static final Logger log = LoggerFactory.getLogger(CentralityService.class);

    private final GraphRepository graphRepository;

    public CentralityService(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    public String findHubEntityInCluster(List<String> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) return null;

        String cypher = """
            MATCH (c:Customer) WHERE c.id IN $ids
            OPTIONAL MATCH (c)-[:USED_DEVICE]->(d:Device)
            OPTIONAL MATCH (c)-[:OWNS_ACCOUNT]->(a:BankAccount)
            WITH coalesce(d.id, a.id) AS resourceId, count(c) AS degree
            WHERE resourceId IS NOT NULL
            RETURN resourceId, degree ORDER BY degree DESC LIMIT 1
        """;

        List<Map<String, Object>> result = graphRepository.executeCypher(cypher, Map.of("ids", entityIds));
        if (!result.isEmpty()) {
            String hubId = (String) result.get(0).get("resourceId");
            log.info("Identified hub entity [{}] for entity cluster size {}", hubId, entityIds.size());
            return hubId;
        }
        String fallbackHub = entityIds.get(0);
        log.info("No shared resource hub found; using fallback hub entity [{}]", fallbackHub);
        return fallbackHub;
    }
}
