package com.skgis.ingestion;

import com.skgis.graph.GraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraphWriterService {
    private static final Logger log = LoggerFactory.getLogger(GraphWriterService.class);
    private static final int BATCH_SIZE = 500;

    private final EntityResolutionService entityResolutionService;
    private final GraphRepository graphRepository;

    public GraphWriterService(EntityResolutionService entityResolutionService, GraphRepository graphRepository) {
        this.entityResolutionService = entityResolutionService;
        this.graphRepository = graphRepository;
    }

    public int writeRecords(List<PaysimRecord> records) {
        if (records == null || records.isEmpty()) {
            log.info("No records to write.");
            return 0;
        }

        log.info("Starting graph write for {} records in batches of {}", records.size(), BATCH_SIZE);
        int totalWritten = 0;

        for (int i = 0; i < records.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, records.size());
            List<PaysimRecord> batch = records.subList(i, end);
            writeBatch(batch);
            totalWritten += batch.size();
            log.info("Ingested {} / {} records into Graph store", totalWritten, records.size());
        }

        return totalWritten;
    }

    private void writeBatch(List<PaysimRecord> batch) {
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        for (PaysimRecord r : batch) {
            Map<String, Object> map = new HashMap<>();
            map.put("txnId", r.getTxnId());
            map.put("step", r.getStep());
            map.put("type", r.getType());
            map.put("amount", r.getAmount());
            map.put("nameOrig", r.getNameOrig());
            map.put("nameDest", r.getNameDest());
            map.put("isMerchantDest", entityResolutionService.isMerchant(r.getNameDest()));
            map.put("isFraud", r.getIsFraud());
            map.put("deviceId", r.getDeviceId());
            map.put("bankAccountId", r.getBankAccountId());
            rowMaps.add(map);

            // Maintain In-Memory Graph store
            graphRepository.addInMemoryNode("Customer", r.getNameOrig(), null);
            graphRepository.addInMemoryNode("Device", r.getDeviceId(), null);
            graphRepository.addInMemoryNode("BankAccount", r.getBankAccountId(), null);
            graphRepository.addInMemoryNode("Transaction", r.getTxnId(), Map.of("amount", r.getAmount(), "type", r.getType()));

            String destType = entityResolutionService.isMerchant(r.getNameDest()) ? "Merchant" : "Customer";
            graphRepository.addInMemoryNode(destType, r.getNameDest(), null);

            graphRepository.addInMemoryEdge(r.getNameOrig(), r.getDeviceId(), "USED_DEVICE");
            graphRepository.addInMemoryEdge(r.getNameOrig(), r.getBankAccountId(), "OWNS_ACCOUNT");
            graphRepository.addInMemoryEdge(r.getNameOrig(), r.getTxnId(), "PERFORMED");
            graphRepository.addInMemoryEdge(r.getTxnId(), r.getNameDest(), "PAID_TO");
        }

        if (!graphRepository.isFallbackMode()) {
            String cypherNodesAndResources = """
                UNWIND $rows AS row
                MERGE (cOrig:Customer {id: row.nameOrig})
                MERGE (dev:Device {id: row.deviceId})
                MERGE (acc:BankAccount {id: row.bankAccountId})
                MERGE (cOrig)-[:USED_DEVICE]->(dev)
                MERGE (cOrig)-[:OWNS_ACCOUNT]->(acc)
                
                CREATE (t:Transaction {
                    id: row.txnId,
                    amount: row.amount,
                    step: row.step,
                    type: row.type,
                    isFraudLabel: row.isFraud
                })
                MERGE (cOrig)-[:PERFORMED]->(t)
            """;
            graphRepository.executeWriteCypher(cypherNodesAndResources, Map.of("rows", rowMaps));

            String cypherDestinations = """
                UNWIND $rows AS row
                MATCH (t:Transaction {id: row.txnId})
                FOREACH (x IN CASE WHEN row.isMerchantDest THEN [1] ELSE [] END |
                    MERGE (m:Merchant {id: row.nameDest})
                    MERGE (t)-[:PAID_TO]->(m)
                )
                FOREACH (x IN CASE WHEN NOT row.isMerchantDest THEN [1] ELSE [] END |
                    MERGE (cDest:Customer {id: row.nameDest})
                    MERGE (t)-[:PAID_TO]->(cDest)
                )
            """;
            graphRepository.executeWriteCypher(cypherDestinations, Map.of("rows", rowMaps));
        }
    }
}
