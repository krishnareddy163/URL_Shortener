package com.example.agentic.core;

import com.example.agentic.core.approval.ApprovalService;
import com.example.agentic.core.approval.ClarificationService;
import com.example.agentic.core.engine.Engine;
import com.example.agentic.core.engine.RunLock;
import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.engine.RunPaths;
import com.example.agentic.core.graph.GraphValidator;
import com.example.agentic.core.graph.WorkflowGraph;
import com.example.agentic.core.graph.WorkflowValidationException;
import com.example.agentic.core.state.ArtifactStore;
import com.example.agentic.core.state.EventStore;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.state.SqliteEventStore;
import com.example.agentic.core.workspace.FileTrees;
import com.example.agentic.core.workspace.Workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point to the engine for one run. It opens the stores, delegates wiring to {@link Engine}, and offers
 * the run lifecycle (start, resume) plus the human decision services. Every operation that changes the run
 * first takes its {@link RunLock}, so two processes can never drive one run at the same time. One generic engine runs every workflow
 * (R7); scenario specifics live only in YAML and fixtures.
 */
public final class Orchestrator implements AutoCloseable {
    private final EventStore store;
    private final ArtifactStore artifacts;
    private final RunPaths paths;
    private final RunLog log;
    private final Workspace workspace;
    private final GraphValidator validator;
    private final Engine engine;
    private final Object writerLockGuard = new Object();
    private RunLock writerLock;

    private Orchestrator(Path runDir, String runId, EngineConfig config, EventStore store, ArtifactStore artifacts) {
        this.store = store;
        this.artifacts = artifacts;
        this.paths = new RunPaths(runDir);
        this.log = new RunLog(store, runId, config.clock(), config.listener());
        this.workspace = new Workspace(paths.workspace(), paths.staging());
        this.validator = new GraphValidator(config.agents().agentIds(), config.gates().ids());
        this.engine = new Engine(config, paths, log, workspace, artifacts, validator);
    }

    /** Opens (or creates) a run backed by {@code <runDir>/events.db}. */
    public static Orchestrator open(Path runDir, String runId, EngineConfig config) {
        SqliteEventStore sqlite = new SqliteEventStore(new RunPaths(runDir).events());
        return new Orchestrator(runDir, runId, config, sqlite, sqlite);
    }

    /** Opens a run over caller-supplied stores (for example an in-memory store in tests). */
    public static Orchestrator open(Path runDir, String runId, EngineConfig config, EventStore store,
                                    ArtifactStore artifacts) {
        return new Orchestrator(runDir, runId, config, store, artifacts);
    }

    /**
     * Starts a new run: validates the graph against the registered agents and gates, prepares the workspace,
     * records RUN_STARTED (including the full graph) and executes.
     */
    public RunOutcome start(WorkflowGraph graph, Path repoRoot, RunMetadata metadata)
            throws IOException, WorkflowValidationException {
        if (log.state().status() != RunState.RunStatus.NEW) {
            throw new IllegalStateException("run " + log.runId() + " already exists; use resume");
        }
        validator.validateOrThrow(graph);
        lockForWriting();
        prepareWorkspace(graph.workspace(), repoRoot);
        log.append(null, new Payload.RunStarted(graph.name(), graph, workspace.treeHash(), metadata));
        return engine.scheduler().run();
    }

    /** Continues after approvals, answers, a safe-stop fix, or a crash. DONE nodes are never re-executed. */
    public RunOutcome resume() throws IOException {
        RunState state = log.state();
        if (state.status() == RunState.RunStatus.NEW) {
            throw new IllegalStateException("run " + log.runId() + " has not been started");
        }
        if (state.status() == RunState.RunStatus.COMPLETED && state.allDone()) {
            return RunOutcome.COMPLETED;
        }
        lockForWriting();
        workspace.clearStaging();
        log.append(null, new Payload.Resumed(state.status().name()));
        return engine.scheduler().run();
    }

    /** Nodes waiting for approval, with what a reviewer needs; read-only, never locks. */
    public List<ApprovalService.PendingApproval> pendingApprovals() {
        return engine.approvals().pending();
    }

    /** Clarification questions not yet answered; read-only, never locks. */
    public List<RunState.Question> openQuestions() {
        return engine.clarifications().openQuestions();
    }

    /** Human approval decisions; takes the run's writer lock (see {@link RunLock}). */
    public ApprovalService approvals() {
        lockForWriting();
        return engine.approvals();
    }

    /** Clarification answers; takes the run's writer lock (see {@link RunLock}). */
    public ClarificationService clarifications() {
        lockForWriting();
        return engine.clarifications();
    }

    public RunLog log() {
        return log;
    }

    public RunState state() {
        return log.state();
    }

    public ArtifactStore artifacts() {
        return artifacts;
    }

    public Path runDir() {
        return paths.root();
    }

    public Workspace workspace() {
        return workspace;
    }

    @Override
    public void close() {
        synchronized (writerLockGuard) {
            try {
                store.close();
            } finally {
                if (writerLock != null) {
                    writerLock.close();
                    writerLock = null;
                }
            }
        }
    }

    /** Takes the per-run writer lock once; read-only use (status, pending, report, lineage) never locks. */
    private void lockForWriting() {
        synchronized (writerLockGuard) {
            if (writerLock == null) {
                writerLock = RunLock.acquire(paths.root());
            }
        }
    }

    private void prepareWorkspace(String spec, Path repoRoot) throws IOException {
        Path root = workspace.root();
        if (Files.exists(root) && !FileTrees.listFiles(root).isEmpty()) {
            throw new IllegalStateException("workspace " + root + " is not empty");
        }
        Files.createDirectories(root);
        if (spec.startsWith("copy:")) {
            Path base = repoRoot.toAbsolutePath().normalize();
            Path source = base.resolve(spec.substring("copy:".length())).normalize();
            if (!source.startsWith(base) || !Files.isDirectory(source)) {
                throw new IllegalArgumentException("workspace source must be a directory inside the repository: " + spec);
            }
            FileTrees.copyTree(source, root);
        }
    }
}
