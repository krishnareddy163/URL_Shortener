package com.example.agentic.agents;

import com.example.agentic.agents.scan.CodebaseScan;
import com.example.agentic.agents.scan.ImpactReasoner;
import com.example.agentic.agents.scan.JavaCodebaseScanner;
import com.example.agentic.core.agent.Agent;
import com.example.agentic.core.agent.AgentContext;
import com.example.agentic.core.agent.AgentException;
import com.example.agentic.core.agent.Proposal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Codebase reasoning agent. The structural scan is always real (JavaParser over the read-only workspace view) and
 * runs first; an {@link ImpactReasoner} then maps the requirement to impacted files with reasons, and may propose a
 * graph patch. In MOCK mode the reasoning is replayed from fixtures; in LIVE mode a model reasons over the scan. The
 * {@code impact-files-exist} gate checks every claim against the real scan either way.
 */
public final class CodebaseAnalystAgent implements Agent {
    private final String id;
    private final ImpactReasoner reasoner;
    private final JavaCodebaseScanner scanner = new JavaCodebaseScanner();

    public CodebaseAnalystAgent(String id, ImpactReasoner reasoner) {
        this.id = id;
        this.reasoner = reasoner;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Map<String, String> metadata() {
        Map<String, String> metadata = new LinkedHashMap<>(reasoner.metadata());
        metadata.putIfAbsent("mode", "MOCK");
        metadata.put("scanner", "javaparser");
        return metadata;
    }

    @Override
    public Proposal propose(AgentContext context) throws AgentException {
        CodebaseScan scan = scanner.scan(context.workspace());
        Proposal reasoned = reasoner.reason(context, scan);
        Map<String, Object> data = new LinkedHashMap<>(reasoned.data());
        data.put("scan", scan.toMap());
        return new Proposal(reasoned.files(), reasoned.rationale(), reasoned.derivedFrom(), data);
    }
}
