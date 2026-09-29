package com.example.agentic.core;

import com.example.agentic.core.agent.AgentResolver;
import com.example.agentic.core.gate.GateRegistry;
import com.example.agentic.core.policy.PolicyConfig;
import com.example.agentic.core.policy.RiskRule;
import com.example.agentic.core.state.Event;

import java.time.Clock;
import java.util.List;
import java.util.function.Consumer;

/**
 * Engine collaborators that are the same for every run of a process.
 *
 * @param policy    governance configuration
 * @param gates     available gates
 * @param riskRules risk rules
 * @param agents    agent resolver
 * @param clock     the only time source (R9)
 * @param listener  receives every appended event (console trace); may be {@code null}
 */
public record EngineConfig(
        PolicyConfig policy,
        GateRegistry gates,
        List<RiskRule> riskRules,
        AgentResolver agents,
        Clock clock,
        Consumer<Event> listener) {

    public EngineConfig {
        riskRules = List.copyOf(riskRules);
    }
}
