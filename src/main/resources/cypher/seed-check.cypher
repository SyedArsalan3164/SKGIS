MATCH (c:Customer) WITH count(c) AS customers
MATCH (m:Merchant) WITH customers, count(m) AS merchants
MATCH (d:Device) WITH customers, merchants, count(d) AS devices
MATCH (a:BankAccount) WITH customers, merchants, devices, count(a) AS accounts
MATCH (t:Transaction) WITH customers, merchants, devices, accounts, count(t) AS transactions
OPTIONAL MATCH (rc:RiskCluster) WITH customers, merchants, devices, accounts, transactions, count(rc) AS riskClusters
RETURN customers, merchants, devices, accounts, transactions, riskClusters;
