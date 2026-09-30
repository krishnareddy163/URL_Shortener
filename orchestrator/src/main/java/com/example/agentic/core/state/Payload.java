package com.example.agentic.core.state;

import com.example.agentic.core.graph.GraphPatch;
import com.example.agentic.core.graph.WorkflowGraph;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Typed body of every event type. Writers build a record and readers decode one, so a misspelled key is a
 * compile error rather than a silent {@code null}. The event store keeps the JSON form ({@link #toDetails()}),
 * and decoding rejects unknown fields so persisted events and code cannot drift apart unnoticed.
 */
public sealed interface Payload {

    /** The event type this payload belongs to. */
    EventType type();

    /** JSON-friendly form stored in {@code events.details_json}; null fields are omitted. */
    default Map<String, Object> toDetails() {
        return Codec.MAPPER.convertValue(this, Codec.MAP);
    }

    /** Decodes an event's details into the payload record for its type. */
    static Payload decode(Event event) {
        return Codec.MAPPER.convertValue(event.details(), classFor(event.type()));
    }

    /** The payload record class for each event type (exhaustive: adding an event type forces an entry here). */
    static Class<? extends Payload> classFor(EventType type) {
        return switch (type) {
            case RUN_STARTED -> RunStarted.class;
            case RESUMED -> Resumed.class;
            case NODE_STARTED -> NodeStarted.class;
            case AGENT_CALLED -> AgentCalled.class;
            case GATE_PASSED -> GatePassed.class;
            case GATE_FAILED -> GateFailed.class;
            case ATTEMPT_DISCARDED -> AttemptDiscarded.class;
            case FALLBACK -> Fallback.class;
            case NODE_FAILED -> NodeFailed.class;
            case APPROVAL_REQUESTED -> ApprovalRequested.class;
            case APPROVED -> Approved.class;
            case REJECTED -> Rejected.class;
            case CLARIFICATION_REQUESTED -> ClarificationRequested.class;
            case ANSWERED -> Answered.class;
            case INVALIDATED -> Invalidated.class;
            case UPSTREAM_REJECTED -> UpstreamRejected.class;
            case REPLAN -> Replan.class;
            case NODE_DONE -> NodeDone.class;
            case RUN_PAUSED -> RunPaused.class;
            case SAFE_STOP -> SafeStopped.class;
            case RUN_COMPLETED -> RunCompleted.class;
        };
    }

    /** Shared JSON codec; strict on unknown fields, lenient on absent ones. */
    final class Codec {
        static final ObjectMapper MAPPER = JsonMapper.builder()
                .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

        private Codec() {
        }
    }

    /**
     * A run began.
     *
     * @param workflow          workflow name
     * @param graph             the complete initial graph (the fold rebuilds state from it)
     * @param workspaceTreeHash hash of the starting workspace
     * @param metadata          provenance needed to reopen the run
     */
    record RunStarted(String workflow, WorkflowGraph graph, String workspaceTreeHash, RunMetadata metadata) implements Payload {
        @Override
        public EventType type() {
            return EventType.RUN_STARTED;
        }
    }

    /**
     * A run continued after a pause, stop or crash.
     *
     * @param previousStatus run status before resuming
     */
    record Resumed(String previousStatus) implements Payload {
        @Override
        public EventType type() {
            return EventType.RESUMED;
        }
    }

    /**
     * A node began executing.
     *
     * @param agent    primary agent
     * @param variant  fixture variant selected by clarification answers
     * @param upstream nodes whose artifacts the node may read
     */
    record NodeStarted(String agent, String variant, List<String> upstream) implements Payload {
        public NodeStarted {
            upstream = upstream == null ? List.of() : List.copyOf(upstream);
        }

        @Override
        public EventType type() {
            return EventType.NODE_STARTED;
        }
    }

    /**
     * An agent was invoked (successfully or not).
     *
     * @param agent         agent id
     * @param attempt       attempt number for this node, agent and input hash
     * @param fallbackRound whether this was the fallback agent's round
     * @param promptHash    hash of everything the agent was shown
     * @param responseHash  hash of the proposal, or {@code null} if the agent failed
     * @param error         failure message, or {@code null}
     * @param agentMetadata agent-supplied audit data (for example the model); never secrets
     * @param usage         model tokens and time for this attempt, or {@code null} if no model was called
     */
    record AgentCalled(String agent, int attempt, boolean fallbackRound, String promptHash, String responseHash,
                       String error, Map<String, String> agentMetadata, ModelUsage usage) implements Payload {
        public AgentCalled {
            agentMetadata = agentMetadata == null ? Map.of() : Map.copyOf(agentMetadata);
        }

        @Override
        public EventType type() {
            return EventType.AGENT_CALLED;
        }
    }

    /**
     * A gate passed.
     *
     * @param gate   gate id
     * @param phase  {@code entry}, {@code apply} or {@code exit}
     * @param detail evidence the gate recorded (for example measured coverage), or {@code null}
     */
    record GatePassed(String gate, String phase, String detail) implements Payload {
        @Override
        public EventType type() {
            return EventType.GATE_PASSED;
        }
    }

    /**
     * A gate failed.
     *
     * @param gate      gate id
     * @param phase     {@code entry}, {@code apply} or {@code exit}
     * @param reason    failure text (fed back to the agent)
     * @param signature normalized failure signature
     */
    record GateFailed(String gate, String phase, String reason, String signature) implements Payload {
        @Override
        public EventType type() {
            return EventType.GATE_FAILED;
        }
    }

    /**
     * An attempt was rolled back (its staging discarded).
     *
     * @param agent     agent id
     * @param attempt   attempt number
     * @param gate      what failed ({@code agent}, a gate id, {@code graph-patch}, ...)
     * @param signature normalized failure signature
     * @param reason    failure text
     */
    record AttemptDiscarded(String agent, int attempt, String gate, String signature, String reason) implements Payload {
        @Override
        public EventType type() {
            return EventType.ATTEMPT_DISCARDED;
        }
    }

    /**
     * The node switched to its fallback agent.
     *
     * @param from  primary agent
     * @param to    fallback agent
     * @param cause circuit breaker or exhausted retries
     */
    record Fallback(String from, String to, String cause) implements Payload {
        @Override
        public EventType type() {
            return EventType.FALLBACK;
        }
    }

    /**
     * A node failed terminally.
     *
     * @param agent          last agent tried
     * @param gate           what failed
     * @param signature      failure signature
     * @param reason         failure text
     * @param breakerTripped whether the circuit breaker ended the attempts
     */
    record NodeFailed(String agent, String gate, String signature, String reason, boolean breakerTripped) implements Payload {
        @Override
        public EventType type() {
            return EventType.NODE_FAILED;
        }
    }

    /**
     * A human checkpoint was requested.
     *
     * @param summary    agent rationale
     * @param autonomy   the node's autonomy level
     * @param reasons    why approval is required
     * @param files      files that will be promoted (sorted)
     * @param baseHash   hash of the agent's raw output
     * @param baseHashes hash of each proposed file at staging time
     * @param diffStat   per-file change counts
     * @param diff       unified diff (possibly truncated)
     */
    record ApprovalRequested(String summary, String autonomy, List<String> reasons, List<String> files, String baseHash,
                             Map<String, String> baseHashes, String diffStat, String diff) implements Payload {
        public ApprovalRequested {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            files = files == null ? List.of() : List.copyOf(files);
            baseHashes = baseHashes == null ? Map.of() : Map.copyOf(baseHashes);
        }

        @Override
        public EventType type() {
            return EventType.APPROVAL_REQUESTED;
        }
    }

    /**
     * A human approved an exact artifact.
     *
     * @param by      approver
     * @param comment approval comment
     * @param hash    approved artifact hash
     */
    record Approved(String by, String comment, String hash) implements Payload {
        @Override
        public EventType type() {
            return EventType.APPROVED;
        }
    }

    /**
     * A human rejected an artifact.
     *
     * @param by      reviewer
     * @param comment what must change (becomes the agent's feedback)
     * @param hash    rejected artifact hash
     */
    record Rejected(String by, String comment, String hash) implements Payload {
        @Override
        public EventType type() {
            return EventType.REJECTED;
        }
    }

    /**
     * An agent asked a blocking question.
     *
     * @param questionId             question id
     * @param question               question text
     * @param blocking               always {@code true} for recorded questions
     * @param options                suggested answers
     * @param assumptionIfUnanswered fallback assumption, if any
     * @param baseHashes             hash of each proposed file at staging time
     * @param riskReasons            risk flags computed before pausing
     * @param diffStat               per-file change counts
     */
    record ClarificationRequested(String questionId, String question, boolean blocking, List<String> options,
                                  String assumptionIfUnanswered, Map<String, String> baseHashes, List<String> riskReasons,
                                  String diffStat) implements Payload {
        public ClarificationRequested {
            options = options == null ? List.of() : List.copyOf(options);
            baseHashes = baseHashes == null ? Map.of() : Map.copyOf(baseHashes);
            riskReasons = riskReasons == null ? List.of() : List.copyOf(riskReasons);
        }

        @Override
        public EventType type() {
            return EventType.CLARIFICATION_REQUESTED;
        }
    }

    /**
     * A human answered (or re-answered) a question.
     *
     * @param questionId     question id
     * @param answer         the answer
     * @param previousAnswer the answer it replaced, if any
     */
    record Answered(String questionId, String answer, String previousAnswer) implements Payload {
        @Override
        public EventType type() {
            return EventType.ANSWERED;
        }
    }

    /**
     * A node's work was invalidated by an upstream change.
     *
     * @param reason          why
     * @param upstream        node whose artifact changed
     * @param oldHash         upstream artifact before
     * @param newHash         upstream artifact after
     * @param previousStatus  this node's status before invalidation
     * @param approvalRevoked whether an approval was revoked
     * @param revertedFiles   files restored from pre-images
     */
    record Invalidated(String reason, String upstream, String oldHash, String newHash, String previousStatus,
                       boolean approvalRevoked, List<String> revertedFiles) implements Payload {
        public Invalidated {
            revertedFiles = revertedFiles == null ? List.of() : List.copyOf(revertedFiles);
        }

        @Override
        public EventType type() {
            return EventType.INVALIDATED;
        }
    }

    /**
     * An upstream node was reset to PENDING because a downstream node exhausted its retries.
     *
     * @param downstream node id that triggered the rejection
     * @param feedback   the gate-failure text to feed back to the upstream agent on its next attempt
     */
    record UpstreamRejected(String downstream, String feedback) implements Payload {
        @Override
        public EventType type() {
            return EventType.UPSTREAM_REJECTED;
        }
    }

    /**
     * The plan changed: a graph patch was applied, or an invalidation cascade was recorded.
     *
     * @param kind       {@link #GRAPH_PATCH} or {@link #INVALIDATION}
     * @param reason     why
     * @param patch      the applied patch (graph-patch only; the fold applies it)
     * @param addedNodes nodes the patch added (graph-patch only)
     * @param upstream   node whose change caused the cascade (invalidation only)
     * @param oldHash    upstream artifact before (invalidation only)
     * @param newHash    upstream artifact after (invalidation only)
     * @param cascade    invalidated nodes (invalidation only)
     */
    record Replan(String kind, String reason, GraphPatch patch, List<String> addedNodes, String upstream,
                  String oldHash, String newHash, List<String> cascade) implements Payload {
        public static final String GRAPH_PATCH = "graph-patch";
        public static final String INVALIDATION = "invalidation";

        public Replan {
            addedNodes = addedNodes == null ? List.of() : List.copyOf(addedNodes);
            cascade = cascade == null ? List.of() : List.copyOf(cascade);
        }

        public static Replan ofGraphPatch(String reason, GraphPatch patch, List<String> addedNodes) {
            return new Replan(GRAPH_PATCH, reason, patch, addedNodes, null, null, null, List.of());
        }

        public static Replan ofInvalidation(String reason, String upstream, String oldHash, String newHash,
                                          List<String> cascade) {
            return new Replan(INVALIDATION, reason, null, List.of(), upstream, oldHash, newHash, cascade);
        }

        @JsonIgnore
        public boolean isInvalidation() {
            return INVALIDATION.equals(kind);
        }

        @Override
        public EventType type() {
            return EventType.REPLAN;
        }
    }

    /**
     * A node's artifact became accepted work.
     *
     * @param files      promoted files
     * @param baseHash   hash of the agent's raw output (differs from the artifact hash when answers were folded in)
     * @param approvedBy approver, if the node needed approval
     * @param commit     workspace git commit of the promoted files, or {@code null} if there was none
     */
    record NodeDone(List<String> files, String baseHash, String approvedBy, String commit) implements Payload {
        public NodeDone {
            files = files == null ? List.of() : List.copyOf(files);
        }

        @Override
        public EventType type() {
            return EventType.NODE_DONE;
        }
    }

    /**
     * Nothing can run until a human acts.
     *
     * @param waiting node id to its waiting status
     */
    record RunPaused(Map<String, String> waiting) implements Payload {
        public RunPaused {
            waiting = waiting == null ? Map.of() : Map.copyOf(waiting);
        }

        @Override
        public EventType type() {
            return EventType.RUN_PAUSED;
        }
    }

    /**
     * The run safe-stopped.
     *
     * @param reason    why
     * @param node      failing node, or {@code null} for run-level stops
     * @param doneNodes nodes whose work is preserved
     */
    record SafeStopped(String reason, String node, List<String> doneNodes) implements Payload {
        public SafeStopped {
            doneNodes = doneNodes == null ? List.of() : List.copyOf(doneNodes);
        }

        @Override
        public EventType type() {
            return EventType.SAFE_STOP;
        }
    }

    /**
     * Every node is DONE.
     *
     * @param nodes number of nodes in the final graph
     */
    record RunCompleted(int nodes) implements Payload {
        @Override
        public EventType type() {
            return EventType.RUN_COMPLETED;
        }
    }
}
