package com.example.agentic.core.report;

import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.Payload;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One-line human description of an event, shared by the report timeline and the console trace. */
public final class EventDescriber {
    private static final Pattern FAILING_TEST = Pattern.compile("^\\[ERROR]\\s+([\\w$.]+:\\d+)(?:\\s|$)");
    private static final Pattern EXCEPTION_PREFIX = Pattern.compile("^[\\w.$]+(?:Error|Exception):\\s*");

    private EventDescriber() {
    }

    public static String describe(Event event) {
        return switch (event.payload()) {
            case Payload.RunStarted started -> "workflow " + started.workflow();
            case Payload.NodeStarted started -> "agent " + started.agent() + ", variant " + started.variant();
            case Payload.AgentCalled called -> called.agent() + " attempt " + called.attempt()
                    + (called.usage() == null ? "" : String.format(Locale.ROOT, " (%d in / %d out tokens, %.1f s)",
                            called.usage().inputTokens(), called.usage().outputTokens(), called.usage().millis() / 1000.0))
                    + (called.error() != null ? " error: " + called.error() : "");
            case Payload.GatePassed passed -> passed.gate();
            case Payload.GateFailed failed -> failed.gate() + ": " + summary(failed.reason());
            case Payload.AttemptDiscarded discarded -> "rolled back attempt " + discarded.attempt() + " ("
                    + discarded.gate() + ", sig " + Hashing.shortHash(discarded.signature()) + ")";
            case Payload.Fallback fallback -> fallback.from() + " → " + fallback.to() + ": " + fallback.cause();
            case Payload.NodeFailed failed -> summary(failed.reason());
            case Payload.SafeStopped stopped -> MarkdownText.firstLine(stopped.reason());
            case Payload.ApprovalRequested requested -> "hash " + Hashing.shortHash(event.outputHash()) + "; "
                    + requested.reasons();
            case Payload.Approved approved -> decision(approved.by(), approved.hash(), approved.comment());
            case Payload.Rejected rejected -> decision(rejected.by(), rejected.hash(), rejected.comment());
            case Payload.ClarificationRequested requested -> requested.questionId() + ": " + requested.question();
            case Payload.Answered answered -> answered.questionId() + " = " + answered.answer();
            case Payload.Invalidated invalidated -> "upstream " + invalidated.upstream() + " changed; reverted "
                    + invalidated.revertedFiles().size() + " file(s)"
                    + (invalidated.approvalRevoked() ? "; approval revoked" : "");
            case Payload.Replan replan -> replan.kind() + ": " + replan.reason();
            case Payload.NodeDone done -> "artifact " + Hashing.shortHash(event.outputHash()) + ", "
                    + done.files().size() + " file(s)" + (done.commit() == null ? "" : ", commit " + Hashing.shortHash(done.commit()));
            case Payload.RunPaused paused -> "waiting " + paused.waiting();
            case Payload.Resumed _, Payload.RunCompleted _ -> "";
        };
    }

    /**
     * One line for a failure reason. Build-tool output starts with a generic line ("mvn test failed (exit 1):"),
     * so the first failing test ({@code [ERROR]   Class.method:line}) and the first assertion message are
     * appended when present.
     */
    static String summary(String reason) {
        if (reason == null) {
            return "";
        }
        List<String> lines = reason.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (lines.size() < 2) {
            return MarkdownText.firstLine(reason);
        }
        StringBuilder out = new StringBuilder(lines.getFirst());
        lines.stream().map(FAILING_TEST::matcher).filter(Matcher::find).findFirst()
                .ifPresent(match -> out.append(' ').append(match.group(1)));
        lines.stream().skip(1).filter(line -> !line.startsWith("[")).findFirst()
                .ifPresent(message -> out.append(' ')
                        .append(EXCEPTION_PREFIX.matcher(message).replaceFirst("").replaceFirst("\\s+in:$", "")));
        return out.toString();
    }

    private static String decision(String by, String hash, String comment) {
        return "by " + by + " on " + Hashing.shortHash(hash) + ": " + comment;
    }
}
