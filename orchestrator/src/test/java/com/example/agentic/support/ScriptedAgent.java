package com.example.agentic.support;

import com.example.agentic.core.agent.Agent;
import com.example.agentic.core.agent.AgentContext;
import com.example.agentic.core.agent.AgentException;
import com.example.agentic.core.agent.Proposal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Agent whose behavior is a lambda; counts calls per node. */
public final class ScriptedAgent implements Agent {

    /** Produces a proposal for a context. */
    @FunctionalInterface
    public interface Script {
        Proposal propose(AgentContext context) throws AgentException;
    }

    private final String id;
    private final Script script;
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    public ScriptedAgent(String id, Script script) {
        this.id = id;
        this.script = script;
    }

    /** An agent that proposes the given files, derived from all upstream nodes. */
    public static ScriptedAgent writing(String id, Map<String, String> files) {
        return new ScriptedAgent(id, context -> new Proposal(files, id + " output",
                context.upstream().isEmpty() ? List.of("requirement") : List.copyOf(context.upstream().keySet()), Map.of()));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Proposal propose(AgentContext context) throws AgentException {
        calls.computeIfAbsent(context.nodeId(), key -> new AtomicInteger()).incrementAndGet();
        return script.propose(context);
    }

    public int calls(String nodeId) {
        AtomicInteger count = calls.get(nodeId);
        return count == null ? 0 : count.get();
    }

    public int totalCalls() {
        return calls.values().stream().mapToInt(AtomicInteger::get).sum();
    }
}
