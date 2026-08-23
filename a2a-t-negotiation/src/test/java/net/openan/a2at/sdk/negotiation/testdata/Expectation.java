package net.openan.a2at.sdk.negotiation.testdata;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The validated expectation block of one corpus record, already checked for completeness by the loader.
 *
 * <p>The success and failure shapes are a discriminated union in the corpus JSON; on the loaded record both live on
 * one object and the loader guarantees the field combinations that the outcome allows: a failure expectation always
 * carries an exception name or an error code, and success-only fields never appear on a failure expectation or the
 * other way round.
 *
 * @param success true for the success outcome, false for the failure outcome
 * @param exception expected exception simple name, failure expectations only
 * @param code expected error code, failure expectations only
 * @param messageContains substrings the failure message must contain, failure expectations only
 * @param slotErrors expected slot errors as slot and code pairs, failure expectations only
 * @param llmCalls expected exact LLM call count, or null when the record does not pin it
 * @param promptTextEqualsGolden golden fixture name the generated prompt text must equal, success expectations only
 * @param metadata expected metadata entries, success expectations only
 * @param params expected merged parameter map, success expectations only
 * @param contracts behavior contract names to assert, both outcomes
 * @param differential true when the engine runs the from-text and from-data double assertion, success expectations
 * @since 2026-08
 */
public record Expectation(
        boolean success,
        @Nullable String exception,
        @Nullable String code,
        List<String> messageContains,
        List<SlotError> slotErrors,
        @Nullable Integer llmCalls,
        @Nullable String promptTextEqualsGolden,
        @Nullable Metadata metadata,
        Map<String, Object> params,
        List<String> contracts,
        boolean differential) {

    public Expectation {
        messageContains = List.copyOf(messageContains);
        slotErrors = List.copyOf(slotErrors);
        params = Map.copyOf(params);
        contracts = List.copyOf(contracts);
    }

    /**
     * One expected slot error as a slot and code pair.
     *
     * @param slot slot name such as {@code round}
     * @param code expected slot error code such as {@code out_of_range}
     */
    public record SlotError(String slot, String code) {}

    /**
     * The expected metadata entries of a success expectation.
     *
     * @param templateUriEcho expected template URI metadata echo, or null when not asserted
     * @param contextEcho true when the negotiation context must travel in the metadata, or null when not asserted
     */
    public record Metadata(@Nullable String templateUriEcho, @Nullable Boolean contextEcho) {}
}
