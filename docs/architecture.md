# SKGIS Architecture & Design Specification

## System Overview

The **Semantic Knowledge Graph Intelligence System (SKGIS)** is a graph-native anti-fraud and risk intelligence platform. It complements traditional row-based ML models (e.g. Thirdwatch) by modeling multi-entity relationships between customers, merchants, devices, and bank accounts in Neo4j and using graph community detection + explainable rules to identify fraud syndicates.

```
+-------------------+      +-------------------------+      +--------------------------+
| PaySim CSV Data   | ---> | Ingestion & Resolution  | ---> | Neo4j 5.x Graph Database |
| (5k-20k records)  |      | CsvReader / GraphWriter |      | Customer, Device, Account|
+-------------------+      +-------------------------+      +--------------------------+
                                                                         |
                                                                         v
+-------------------+      +-------------------------+      +--------------------------+
| Static Vis.js UI  | <--- | Spring Boot REST API    | <--- | Risk Analytics Pipeline  |
| (Interactive Visual)     | Graph & Risk Controllers|      | Louvain + PageRank + Rules|
+-------------------+      +-------------------------+      +--------------------------+
```

## Component Architecture

1. **Ingestion Engine (`com.skgis.ingestion`)**:
   - `PaysimRecord`: Mapped POJO for CSV rows.
   - `CsvReaderService`: Parses CSV records and applies synthetic hashing with planted device/account overlap pools (creating intentional fraud rings).
   - `EntityResolutionService`: Distinguishes customer IDs (`C...`) vs merchant IDs (`M...`) and normalizes format.
   - `GraphWriterService`: Executes high-throughput batched Cypher `UNWIND` queries (~500 items per batch) to create nodes and relationships.

2. **Graph Analytics & GDS Engine (`com.skgis.graph`)**:
   - `CommunityDetectionService`: Projects an in-memory graph `sharedResourceGraph` containing `Customer`, `Device`, and `BankAccount` nodes linked by `USED_DEVICE` and `OWNS_ACCOUNT` edges. Executes Neo4j GDS **Louvain Community Detection** to isolate shared-resource clusters.
   - `CentralityService`: Executes GDS **PageRank** within flagged clusters to locate high-degree hub devices or accounts.

3. **Rule & Explainability Engine (`com.skgis.rules`)**:
   - `RiskRuleEngine`: Evaluates each detected community cluster against pluggable rules:
     - `SharedDeviceRule`: Flags clusters where >3 customers share a device.
     - `SharedAccountRule`: Flags clusters where >2 customers share a bank account.
     - `ClusterSizeRule`: Flags abnormally large clusters.
   - Aggregates rule flags, builds structured `RiskReason` explanations, and writes `RiskCluster` nodes and `MEMBER_OF` edges back into the graph.

4. **REST API Layer (`com.skgis.api`)**:
   - `IngestController`: Endpoint `POST /api/ingest/run`.
   - `RiskController`: Endpoints `POST /api/risk/detect` and `GET /api/risk/flagged-clusters`.
   - `GraphController`: Endpoints `GET /api/graph/cluster/{clusterId}` and `GET /api/graph/{entityId}/subgraph` returning vis.js compatible JSON structures.

5. **Frontend Layer (`frontend/index.html`)**:
   - Single-page html visualizer powered by vis.js Network. Renders color-coded entity nodes and highlights flagged fraud rings.
