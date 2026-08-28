# SKGIS Graph Ontology & Schema

The Semantic Knowledge Graph Intelligence System models financial transaction ecosystems to expose multi-entity relationships and shared resource utilization.

## Node Types

- **Customer** (`id`: String): Customer entity initiating or receiving transactions.
- **Merchant** (`id`: String): Merchant entity receiving transaction payments (IDs starting with `M`).
- **Device** (`id`: String): Device fingerprint used by customers during transactions.
- **BankAccount** (`id`: String): Financial bank account linked to customers.
- **Transaction** (`id`: String, `amount`: Double, `timestamp`: Long, `type`: String, `isFraudLabel`: Integer): Individual payment/transfer event.
- **RiskCluster** (`id`: String, `reason`: String, `score`: Double, `createdAt`: Datetime): High-risk community flagged by Louvain community detection and rule evaluation.

## Relationships

```
              (Customer) -------[:OWNS_ACCOUNT]-------> (BankAccount)
                  |
            [:USED_DEVICE]
                  |
                  v
              (Device)

                  ^
                  |
            [:PERFORMED]
                  |
              (Customer) ------[:PERFORMED]------> (Transaction)
                                                       |
                                                  [:PAID_TO]
                                                       |
                                                       v
                                            (Merchant) / (Customer)

              (Customer) -------[:MEMBER_OF]-------> (RiskCluster)
```

## Explanation of Detection Mechanics

Traditional row-based machine learning scores each `Transaction` independently. However, organized fraud syndicates operate by sharing single mobile devices or bank accounts across multiple synthetic or stolen customer identities. 

By projecting an undirected bipartite graph of `Customer - USED_DEVICE - Device` and `Customer - OWNS_ACCOUNT - BankAccount`, GDS Louvain community detection partitions entities into densely connected resource clusters. The rule engine then evaluates cluster characteristics (e.g. `>3` distinct customers on one device) and materializes an explicit `RiskCluster` node attached via `MEMBER_OF` edges.
