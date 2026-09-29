package com.example.agentic.core.report;

/** One {@code ##} section of {@code report.md}. */
interface ReportSection {
    void render(ReportInput input, StringBuilder out);
}
