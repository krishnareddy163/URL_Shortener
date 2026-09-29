package com.example.agentic.core.gate;

/** A check a node must pass before (entry) or after (exit) its agent runs. Gates must not be faked. */
public interface Gate {

    String id();

    GateResult evaluate(GateContext context);
}
