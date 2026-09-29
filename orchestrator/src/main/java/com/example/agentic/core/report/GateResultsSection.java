package com.example.agentic.core.report;

import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.TypedEvent;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.example.agentic.core.report.MarkdownText.cell;

/** Pass/fail totals per gate and the first lines of every failure. */
final class GateResultsSection implements ReportSection {

    @Override
    public void render(ReportInput input, StringBuilder out) {
        Map<String, int[]> totals = new TreeMap<>();
        input.events(Payload.GatePassed.class)
                .forEach(passed -> totals.computeIfAbsent(passed.payload().gate(), key -> new int[2])[0]++);
        List<TypedEvent<Payload.GateFailed>> failures = input.events(Payload.GateFailed.class);
        failures.forEach(failed -> totals.computeIfAbsent(failed.payload().gate(), key -> new int[2])[1]++);
        out.append("## Policy and gate results\n\n| Gate | Passed | Failed |\n|---|---:|---:|\n");
        totals.forEach((gate, counts) -> out.append("| ").append(gate).append(" | ").append(counts[0]).append(" | ")
                .append(counts[1]).append(" |\n"));
        out.append("\nFailures:\n\n");
        for (TypedEvent<Payload.GateFailed> failed : failures) {
            out.append("- seq ").append(failed.event().seq()).append(" `").append(failed.event().nodeId()).append("` / `")
                    .append(failed.payload().gate()).append("`: ")
                    .append(cell(MarkdownText.firstLines(failed.payload().reason(), 3))).append('\n');
        }
        out.append(failures.isEmpty() ? "- none\n\n" : "\n");
    }
}
