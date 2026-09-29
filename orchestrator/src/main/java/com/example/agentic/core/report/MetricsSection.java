package com.example.agentic.core.report;

import com.example.agentic.core.metrics.MetricsCalculator;

/** Metrics computed from the events alone. */
final class MetricsSection implements ReportSection {

    @Override
    public void render(ReportInput input, StringBuilder out) {
        out.append("## Metrics\n\n").append(MetricsCalculator.calculate(input.events()).toMarkdown()).append('\n');
    }
}
