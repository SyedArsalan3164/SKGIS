package com.skgis.model;

import java.util.ArrayList;
import java.util.List;

public class FlaggedCluster {
    private String clusterId;
    private double score;
    private List<RiskReason> reasons = new ArrayList<>();
    private List<String> customerIds = new ArrayList<>();
    private String hubEntityId;
    private int nodeCount;

    public FlaggedCluster() {}

    public FlaggedCluster(String clusterId, double score, List<RiskReason> reasons, List<String> customerIds, String hubEntityId) {
        this.clusterId = clusterId;
        this.score = score;
        this.reasons = (reasons != null) ? reasons : new ArrayList<>();
        this.customerIds = (customerIds != null) ? customerIds : new ArrayList<>();
        this.hubEntityId = hubEntityId;
        this.nodeCount = this.customerIds.size();
    }

    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public List<RiskReason> getReasons() { return reasons; }
    public void setReasons(List<RiskReason> reasons) { this.reasons = reasons; }

    public List<String> getCustomerIds() { return customerIds; }
    public void setCustomerIds(List<String> customerIds) { 
        this.customerIds = customerIds; 
        this.nodeCount = (customerIds != null) ? customerIds.size() : 0;
    }

    public String getHubEntityId() { return hubEntityId; }
    public void setHubEntityId(String hubEntityId) { this.hubEntityId = hubEntityId; }

    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }
}
