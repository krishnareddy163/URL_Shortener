package com.example.agentic.cli;

import picocli.CommandLine.Option;

import java.nio.file.Path;

/** Options shared by every command. */
public final class CommonOptions {

    @Option(names = "--runs-dir", defaultValue = "runs", description = "Directory holding run folders (default: ${DEFAULT-VALUE}).")
    Path runsDir;

    @Option(names = "--policies", defaultValue = "policies/policies.yaml",
            description = "Governance policy file (default: ${DEFAULT-VALUE}).")
    Path policies;

    @Option(names = "--repo-root", defaultValue = ".",
            description = "Repository root used to resolve 'copy:' workspaces (default: ${DEFAULT-VALUE}).")
    Path repoRoot;

    @Option(names = "--no-color", description = "Disable ANSI colors in the trace.")
    boolean noColor;
}
