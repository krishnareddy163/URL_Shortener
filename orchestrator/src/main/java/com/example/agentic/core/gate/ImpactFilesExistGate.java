package com.example.agentic.core.gate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Quality gate for codebase analysis: the impact report ({@code data.impact[]} of {@code file}/{@code reason})
 * must be non-empty, every entry needs a reason, Java files must appear in the analyst's real scan
 * ({@code data.scan.files}) and other files must exist in the workspace. This stops an agent from
 * reasoning about files that do not exist.
 */
public final class ImpactFilesExistGate implements Gate {

    @Override
    public String id() {
        return "impact-files-exist";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        if (context.isEntry()) {
            return GateResult.fail(id(), "impact-files-exist is an exit gate and needs a proposal");
        }
        Map<String, Object> data = context.proposal().data();
        if (!(data.get("impact") instanceof List<?> impact) || impact.isEmpty()) {
            return GateResult.fail(id(), "impact report is missing or empty");
        }
        if (!(data.get("scan") instanceof Map<?, ?> scan) || !(scan.get("files") instanceof List<?> scannedFiles)) {
            return GateResult.fail(id(), "codebase scan (data.scan.files) is missing");
        }
        for (Object item : impact) {
            if (!(item instanceof Map<?, ?> entry) || !(entry.get("file") instanceof String file)
                    || !(entry.get("reason") instanceof String reason) || reason.isBlank()) {
                return GateResult.fail(id(), "each impact entry needs file and reason");
            }
            boolean known = file.endsWith(".java") ? scannedFiles.contains(file) : existsInWorkspace(context.workspace(), file);
            if (!known) {
                return GateResult.fail(id(), "impacted file not found in workspace scan: " + file);
            }
        }
        return GateResult.pass();
    }

    private static boolean existsInWorkspace(Path workspace, String file) {
        Path candidate = workspace.resolve(file).normalize();
        return candidate.startsWith(workspace) && Files.isRegularFile(candidate);
    }
}
