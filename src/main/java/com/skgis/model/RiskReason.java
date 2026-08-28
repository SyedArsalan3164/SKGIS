package com.skgis.model;

import java.util.List;

public class RiskReason {
    private String rule;
    private String explanation;
    private List<String> evidenceEntityIds;

    public RiskReason() {}

    public RiskReason(String rule, String explanation, List<String> evidenceEntityIds) {
        this.rule = rule;
        this.explanation = explanation;
        this.evidenceEntityIds = evidenceEntityIds;
    }

    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public List<String> getEvidenceEntityIds() { return evidenceEntityIds; }
    public void setEvidenceEntityIds(List<String> evidenceEntityIds) { this.evidenceEntityIds = evidenceEntityIds; }
}
