package com.example.agentic.core.engine;

import com.example.agentic.core.EngineConfig;
import com.example.agentic.core.approval.ApprovalService;
import com.example.agentic.core.approval.ClarificationService;
import com.example.agentic.core.graph.GraphValidator;
import com.example.agentic.core.state.ArtifactStore;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.workspace.Workspace;

/**
 * Composition root for one run. Each collaborator receives only what it uses; this is the one place that knows
 * the whole object graph.
 */
public final class Engine {
    private final Scheduler scheduler;
    private final ApprovalService approvals;
    private final ClarificationService clarifications;

    public Engine(EngineConfig config, RunPaths paths, RunLog log, Workspace workspace, ArtifactStore artifacts,
                  GraphValidator validator) {
        ArtifactRecorder recorder = new ArtifactRecorder(artifacts, paths);
        ReplanService replan = new ReplanService(log, workspace, paths, validator);
        NodeCompletion completion = new NodeCompletion(log, workspace, paths, replan);
        SafeStop safeStop = new SafeStop(log, paths);
        BudgetGuard budgets = new BudgetGuard(config.policy().budgets(), config.clock());
        GateRunner gates = new GateRunner(config.gates(), log, workspace.root(), paths.gateLogs());
        AttemptExecutor attempts = new AttemptExecutor(log, config.agents(), workspace, config.policy(), gates, replan);
        Settlement settlement = new Settlement(log, recorder, config.riskRules(), paths, completion);
        NodeRunner runner = new NodeRunner(log, artifacts, gates, attempts, settlement, safeStop, budgets, replan);
        this.scheduler = new Scheduler(log, runner, safeStop, budgets);
        this.approvals = new ApprovalService(log, artifacts, paths, completion, safeStop);
        this.clarifications = new ClarificationService(log, recorder, paths, completion, safeStop);
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public ApprovalService approvals() {
        return approvals;
    }

    public ClarificationService clarifications() {
        return clarifications;
    }
}
