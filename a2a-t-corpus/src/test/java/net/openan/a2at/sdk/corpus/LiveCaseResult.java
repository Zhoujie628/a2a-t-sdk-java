package net.openan.a2at.sdk.corpus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The per-case verdict of one live corpus run: what the case asserted, what the pipeline extracted from the real
 * model's output, and the LLM calls the case triggered.
 *
 * <p>The record is the unit the live suite feeds into {@link LiveTranscript.Run#appendCase(LiveCaseResult)}: PASS and
 * FAIL are assertion verdicts (a FAIL carries the assertion diff), and ERROR marks a case that could not be judged —
 * an engine failure or an infrastructure failure that survived all retries, rethrown so the build verdict matches the
 * recorded one. SKIP is reserved for a case the run did not execute; the phase-1 engine never records it.
 *
 * @param caseId expanded case id, such as {@code LIVE-GEN-01/zh-CN}
 * @param outcome verdict of the case
 * @param assertionSummary one-line summary of what the case asserted
 * @param inputSummary excerpt of the case's input — the natural-language text of a generation case or the prompt text
 *     of a validation case, truncated to the reporting length — or null when the case declares none
 * @param scenarioCode scenario code the pipeline actually recognized, or null when none was extracted
 * @param params parameters the pipeline actually extracted, or null when the case produced no parameter data
 * @param llmCalls LLM calls the case triggered, recorded by the {@link RecordingLLMClient}
 * @param durationMs wall-clock duration of the case in milliseconds
 * @param failureDiff assertion diff of a failed case, or null otherwise
 * @since 2026-08
 */
public record LiveCaseResult(
        String caseId,
        Outcome outcome,
        String assertionSummary,
        @Nullable String inputSummary,
        @Nullable String scenarioCode,
        @Nullable Map<String, Object> params,
        List<LiveLlmCall> llmCalls,
        long durationMs,
        @Nullable String failureDiff) {

    /** Verdict of one live case. */
    public enum Outcome {
        /** The live expectation block held against the real model's output. */
        PASS,

        /** At least one live expectation assertion failed; {@code failureDiff} carries the diff. */
        FAIL,

        /** The case was not executed in this run; reserved — the phase-1 engine records surviving failures as ERROR. */
        SKIP,

        /** The engine could not judge the case: it failed before the assertions, or an infrastructure failure survived
         * all retries. */
        ERROR
    }

    public LiveCaseResult {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(assertionSummary, "assertionSummary");
        Objects.requireNonNull(llmCalls, "llmCalls");
        // Map.copyOf rejects null values, but the filled parameter data legitimately carries them: a schema slot the
        // prompt misses surfaces as a null-valued entry, exactly what the paramsAbsent probes assert.
        params = params == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        llmCalls = List.copyOf(llmCalls);
    }
}
