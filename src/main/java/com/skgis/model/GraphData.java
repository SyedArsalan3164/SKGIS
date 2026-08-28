package com.skgis.model;

import java.util.ArrayList;
import java.util.List;

public class GraphData {
    private String clusterId;
    private double score;
    private List<RiskReason> reasons = new ArrayList<>();
    private List<NodeDto> nodes = new ArrayList<>();
    private List<EdgeDto> edges = new ArrayList<>();
    private int totalCustomers;

    public GraphData() {}

    public GraphData(String clusterId, double score, List<RiskReason> reasons, List<NodeDto> nodes, List<EdgeDto> edges) {
        this(clusterId, score, reasons, nodes, edges, 0);
    }

    public GraphData(String clusterId, double score, List<RiskReason> reasons, List<NodeDto> nodes, List<EdgeDto> edges, int totalCustomers) {
        this.clusterId = clusterId;
        this.score = score;
        this.reasons = reasons;
        this.nodes = nodes;
        this.edges = edges;
        this.totalCustomers = totalCustomers;
    }

    public int getTotalCustomers() { return totalCustomers; }
    public void setTotalCustomers(int totalCustomers) { this.totalCustomers = totalCustomers; }

    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public List<RiskReason> getReasons() { return reasons; }
    public void setReasons(List<RiskReason> reasons) { this.reasons = reasons; }

    public List<NodeDto> getNodes() { return nodes; }
    public void setNodes(List<NodeDto> nodes) { this.nodes = nodes; }

    public List<EdgeDto> getEdges() { return edges; }
    public void setEdges(List<EdgeDto> edges) { this.edges = edges; }

    public static class NodeDto {
        private String id;
        private String label;
        private String type;

        public NodeDto() {}

        public NodeDto(String id, String label, String type) {
            this.id = id;
            this.label = label;
            this.type = type;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeDto nodeDto = (NodeDto) o;
            return java.util.Objects.equals(id, nodeDto.id);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id);
        }
    }

    public static class EdgeDto {
        private String source;
        private String target;
        private String from;
        private String to;
        private String type;

        public EdgeDto() {}

        public EdgeDto(String source, String target, String type) {
            this.source = source;
            this.target = target;
            this.from = source;
            this.to = target;
            this.type = type;
        }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; this.from = source; }

        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; this.to = target; }

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; this.source = from; }

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; this.target = to; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EdgeDto edgeDto = (EdgeDto) o;
            return java.util.Objects.equals(from, edgeDto.from) &&
                   java.util.Objects.equals(to, edgeDto.to) &&
                   java.util.Objects.equals(type, edgeDto.type);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(from, to, type);
        }
    }
}
