# SKGIS Business Case & Strategic Positioning

## Executive Summary

Traditional fraud detection platforms process payments row-by-row. When evaluating a transaction, a standard ML model looks at feature vectors like transaction amount, time, merchant category code, and user velocity. 

While effective at catching simple opportunistic fraud, **row-based ML models are blind to organized fraud rings**. Syndicates operate by creating dozens of synthetic user profiles that individually perform low-risk, small-value transactions. In isolation, every transaction scores clean. However, behind the scenes, these 30 synthetic accounts might all operate from 2 physical devices or route payouts into 1 shared bank account.

**SKGIS (Semantic Knowledge Graph Intelligence System)** provides a graph-native intelligence layer designed to run alongside row-based scoring systems.

## The Blindspot of Row-Based Scoring

```
[ Row-Based ML Model ] 
  Transaction #101 (User A -> Merchant X, $25)  => LOW RISK (Passed)
  Transaction #102 (User B -> Merchant X, $30)  => LOW RISK (Passed)
  Transaction #103 (User C -> Merchant X, $20)  => LOW RISK (Passed)

[ SKGIS Graph Detection Layer ]
  (User A) ---\a
  (User B) ----+---> [ Device D-99 ] ===> CRITICAL FRAUD RING FLAGGED!
  (User C) ---/
```

## Value Proposition & Key Metrics

1. **Complementary to Existing Infrastructure**: Does not replace existing transaction scoring (e.g. Thirdwatch / Razorpay Risk Engine); acts as an asynchronous relationship risk layer.
2. **Explainable by Design**: Risk analysts receive actionable explanations ("Flagged: 6 customers share Device D-0042 and Bank Account A-0091") rather than opaque black-box probability scores.
3. **Reduced False Positives**: Differentiates between normal customer behavior and shared-infrastructure syndicate operations.
