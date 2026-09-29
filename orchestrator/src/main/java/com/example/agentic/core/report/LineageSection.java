package com.example.agentic.core.report;

import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.RunState;

import java.util.Optional;

import static com.example.agentic.core.report.MarkdownText.cell;

/** Decision lineage from each sink artifact back to the requirement, and the artifact of every node. */
final class LineageSection implements ReportSection {

    @Override
    public void render(ReportInput input, StringBuilder out) {
        RunState state = input.state();
        out.append("## Decision lineage\n\n");
        if (state.graph() == null) {
            return;
        }
        LineageWalker walker = new LineageWalker(input.artifacts());
        for (String id : state.graph().topologicalOrder()) {
            RunState.NodeState node = state.node(id);
            if (node.status() == NodeStatus.DONE && state.graph().dependentsOf(id).isEmpty()) {
                walker.chain(node.currentHash(), state.graph().requirement()).forEach(line -> out.append(line).append('\n'));
                out.append('\n');
            }
        }
        out.append("Artifacts by node:\n\n| Node | Artifact | Files | Rationale |\n|---|---|---:|---|\n");
        for (String id : state.graph().topologicalOrder()) {
            String hash = state.node(id).currentHash();
            if (hash == null) {
                continue;
            }
            Optional<Artifact> artifact = input.artifacts().get(hash);
            out.append("| ").append(id).append(" | `").append(Hashing.shortHash(hash)).append("` | ")
                    .append(artifact.map(value -> value.files().size()).orElse(0)).append(" | ")
                    .append(cell(artifact.map(Artifact::rationale).orElse(""))).append(" |\n");
        }
        out.append('\n');
    }
}
