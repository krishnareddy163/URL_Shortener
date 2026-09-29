package com.example.agentic.core.gate;

import com.example.agentic.core.policy.DependencyAllowlistGate;
import com.example.agentic.core.policy.ForbiddenApiGate;
import com.example.agentic.core.policy.NoRawIpLoggingGate;
import com.example.agentic.core.policy.PathAllowlistGate;
import com.example.agentic.core.policy.PolicyConfig;
import com.example.agentic.core.policy.SecretScanGate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Gates by id. Workflows may only reference registered gates. */
public final class GateRegistry {
    private final Map<String, Gate> gates = new LinkedHashMap<>();

    public GateRegistry register(Gate gate) {
        if (gates.putIfAbsent(gate.id(), gate) != null) {
            throw new IllegalArgumentException("duplicate gate id " + gate.id());
        }
        return this;
    }

    public Optional<Gate> get(String id) {
        return Optional.ofNullable(gates.get(id));
    }

    public Set<String> ids() {
        return Set.copyOf(gates.keySet());
    }

    /** The built-in quality, compliance, security and build gates. */
    public static GateRegistry builtIns(PolicyConfig policy, BuildRunner builds) {
        return new GateRegistry()
                .register(new ArtifactMetadataGate())
                .register(new RequirementsCompleteGate())
                .register(new SchemaValidGate())
                .register(new PathAllowlistGate(policy))
                .register(new SecretScanGate(policy))
                .register(new ForbiddenApiGate(policy))
                .register(new DependencyAllowlistGate(policy))
                .register(new NoRawIpLoggingGate(policy))
                .register(new MavenGate("compile", "compile", builds))
                .register(new MavenGate("unit-tests", "test", builds))
                .register(new MavenGate("regression-tests", "test", builds))
                .register(new ReproducesDefectGate(builds))
                .register(new ImpactFilesExistGate())
                .register(new ReviewGoGate())
                .register(new ReviewCompleteGate())
                .register(new DesignDiagramsGate())
                .register(new TestCoverageGate(builds, policy.coverage()))
                .register(new FunctionalCoverageGate());
    }
}
