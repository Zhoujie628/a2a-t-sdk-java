package net.openan.a2at.sdk.corpus;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The validated live expectation block of one {@link LiveCase}, already checked for completeness by the loader.
 *
 * <p>The live family asserts a key-field subset against the output of a real LLM instead of the full offline
 * equivalence: {@code paramsContains} is a subset check (the named slots must be filled with the expected values,
 * other slots are not asserted), {@code paramsAbsent} names the slots that must be null or missing, and {@code
 * maxLlmCalls} is an upper bound rather than the exact call count of the offline {@link Expectation#llmCalls()} —
 * a real model may trigger pipeline-internal retries. There is no golden fixture and no differential run.
 *
 * @param success expected operation outcome; live phase 1 expects the success path only
 * @param scenarioCode expected recognized scenario code, or null when the record does not pin it
 * @param paramsContains slots that must be filled with exactly these values (subset check), empty when the record
 *     states none
 * @param paramsAbsent slots that must be null or missing, empty when the record states none
 * @param promptTextContains structural fragments the generated task prompt text must contain, empty when the record
 *     states none (generate API only; the loader rejects it on validate records)
 * @param maxLlmCalls upper bound of LLM calls the case may trigger; the loader defaults a record that omits it to 4,
 *     so null appears only on hand-built instances and means unbounded
 * @since 2026-08
 */
public record LiveExpectation(
        boolean success,
        @Nullable String scenarioCode,
        Map<String, Object> paramsContains,
        List<String> paramsAbsent,
        List<String> promptTextContains,
        @Nullable Integer maxLlmCalls) {

    public LiveExpectation {
        paramsContains = Map.copyOf(paramsContains);
        paramsAbsent = List.copyOf(paramsAbsent);
        promptTextContains = List.copyOf(promptTextContains);
    }
}
