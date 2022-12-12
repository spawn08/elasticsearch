/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.health.stats;

import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.nodes.BaseNodeResponse;
import org.elasticsearch.action.support.nodes.BaseNodesRequest;
import org.elasticsearch.action.support.nodes.BaseNodesResponse;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.metrics.Counters;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.health.HealthStatus;
import org.elasticsearch.transport.TransportRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This class collects the stats of the health API from every node
 */
public class HealthApiStatsAction extends ActionType<HealthApiStatsAction.Response> {

    public static final HealthApiStatsAction INSTANCE = new HealthApiStatsAction();
    public static final String NAME = "cluster:monitor/health_api/stats";

    private HealthApiStatsAction() {
        super(NAME, Response::new);
    }

    public static class Request extends BaseNodesRequest<Request> {

        public Request() {
            super((String[]) null);
        }

        public Request(StreamInput in) throws IOException {
            super(in);
        }

        @Override
        public ActionRequestValidationException validate() {
            return null;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
        }

        @Override
        public String toString() {
            return "health_api_stats";
        }

        public static class Node extends TransportRequest {

            public Node(StreamInput in) throws IOException {
                super(in);
            }

            public Node(Request ignored) {}

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                super.writeTo(out);
            }
        }
    }

    public static class Response extends BaseNodesResponse<Response.Node> {

        public Response(StreamInput in) throws IOException {
            super(in);
        }

        public Response(ClusterName clusterName, List<Node> nodes, List<FailedNodeException> failures) {
            super(clusterName, nodes, failures);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
        }

        @Override
        protected List<Node> readNodesFrom(StreamInput in) throws IOException {
            return in.readList(Node::new);
        }

        @Override
        protected void writeNodesTo(StreamOutput out, List<Node> nodes) throws IOException {
            out.writeList(nodes);
        }

        public Counters getStats() {
            List<Counters> counters = getNodes().stream().map(Node::getStats).filter(Objects::nonNull).toList();
            return Counters.merge(counters);
        }

        public Set<HealthStatus> getStatuses() {
            Set<HealthStatus> statuses = new HashSet<>();
            for (Node node : getNodes()) {
                statuses.addAll(node.statuses);
            }
            return statuses;
        }

        public Map<HealthStatus, Set<String>> getIndicators() {
            Map<HealthStatus, Set<String>> indicators = new HashMap<>();
            for (Node node : getNodes()) {
                for (HealthStatus status : node.indicators.keySet()) {
                    indicators.computeIfAbsent(status, s -> new HashSet<>()).addAll(node.indicators.get(status));
                }
            }
            return indicators;
        }

        public Map<HealthStatus, Set<String>> getDiagnoses() {
            Map<HealthStatus, Set<String>> diagnoses = new HashMap<>();
            for (Node node : getNodes()) {
                for (HealthStatus status : node.diagnoses.keySet()) {
                    diagnoses.computeIfAbsent(status, s -> new HashSet<>()).addAll(node.diagnoses.get(status));
                }
            }
            return diagnoses;
        }

        public static class Node extends BaseNodeResponse {
            @Nullable
            private Counters stats;
            private Set<HealthStatus> statuses = Set.of();
            private Map<HealthStatus, Set<String>> indicators = Map.of();
            private Map<HealthStatus, Set<String>> diagnoses = Map.of();

            public Node(StreamInput in) throws IOException {
                super(in);
                stats = in.readOptionalWriteable(Counters::new);
                statuses = in.readSet(HealthStatus::read);
                indicators = in.readMap(HealthStatus::read, input -> input.readSet(StreamInput::readString));
                diagnoses = in.readMap(HealthStatus::read, input -> input.readSet(StreamInput::readString));
            }

            public Node(DiscoveryNode node) {
                super(node);
            }

            public Counters getStats() {
                return stats;
            }

            public void setStats(Counters stats) {
                this.stats = stats;
            }

            public void setStatuses(Set<HealthStatus> statuses) {
                this.statuses = statuses;
            }

            public void setIndicators(Map<HealthStatus, Set<String>> indicators) {
                this.indicators = indicators;
            }

            public void setDiagnoses(Map<HealthStatus, Set<String>> diagnoses) {
                this.diagnoses = diagnoses;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                super.writeTo(out);
                out.writeOptionalWriteable(stats);
                out.writeCollection(statuses);
                out.writeMap(indicators, StreamOutput::writeEnum, (o, v) -> o.writeCollection(v, StreamOutput::writeString));
                out.writeMap(diagnoses, StreamOutput::writeEnum, (o, v) -> o.writeCollection(v, StreamOutput::writeString));
            }
        }
    }
}
