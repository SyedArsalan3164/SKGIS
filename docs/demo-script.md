# SKGIS Demo Video Script (3-4 Minutes)

## Timeline & Script Narration

### 1. Problem Statement (0:00 - 0:30)
- **Visual**: Show slide or `docs/business-case.md` ASCII diagram.
- **Narration**: *"Traditional payment risk engines score transactions row by row. If five new accounts each make a $20 payment, standard ML models pass all five as low risk. But what if those five accounts all share the exact same mobile device? Row-based models miss relational fraud. SKGIS solves this by building a semantic knowledge graph in Neo4j and using graph community detection to catch fraud rings in real time."*

### 2. Graph Ontology (0:30 - 1:00)
- **Visual**: Show `ontology/README.md` diagram.
- **Narration**: *"Here is our graph ontology. We model Customers, Merchants, Transactions, Devices, and Bank Accounts. Relationships like USED_DEVICE and OWNS_ACCOUNT connect customers through shared resources."*

### 3. Execution & Ingestion (1:00 - 1:45)
- **Visual**: Show terminal running `./scripts/run_demo.sh` or hitting `POST /api/ingest/run` and `POST /api/risk/detect` via Postman/curl. Show Neo4j Browser populating.
- **Narration**: *"We trigger our Spring Boot batch ingestion service. It processes 5,000 transaction records, normalizes entities, and executes batch Cypher merges. Next, we call our risk detection endpoint. Neo4j GDS projects a shared resource graph and runs the Louvain community detection algorithm."*

### 4. Interactive Visualization & Explainability (1:45 - 2:45)
- **Visual**: Open `frontend/index.html` in browser. Select a flagged cluster from the dropdown. Hover over nodes and show the right panel.
- **Narration**: *"Here in our vis.js graph viewer, we select Flagged Cluster C1. Notice how 5 blue customer nodes surround orange Device D-0042 and green Bank Account A-0091. Our rule engine evaluated this cluster and automatically generated clear explainability reasons: SharedDeviceRule flagged 5 customers on 1 device, and SharedAccountRule flagged 2 customers on 1 account."*

### 5. API Response JSON & Wrap-Up (2:45 - 3:30)
- **Visual**: Show REST API JSON output for `/api/risk/flagged-clusters`.
- **Narration**: *"Our REST API returns structured, human-readable risk objects ready for risk operations and analyst dashboards. SKGIS provides an explainable, graph-native intelligence layer that catches syndicate fraud row-based models miss."*
