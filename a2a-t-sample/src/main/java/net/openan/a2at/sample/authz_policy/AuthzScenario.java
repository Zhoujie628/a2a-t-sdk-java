package net.openan.a2at.sample.authz_policy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;

/**
 * Immutable configuration item for a single demo scenario.
 *
 * @param label human-readable scenario label
 * @param entry client entry point ({@code from_text} or {@code from_data_with_schema})
 * @param input scenario input data
 * @param expected expected outcome carrying the outcome code, expected filled params, expected prompt
 *     text and expected slot-level error details
 * @since 2026-08
 */
public record AuthzScenario(String label, String entry, Map<String, Object> input, AuthzExpected expected) {

    static final String FROM_TEXT = "from_text";
    static final String FROM_DATA_WITH_SCHEMA = "from_data_with_schema";
    static final String EXPECTED_SUCCESS = "success";

    static final Set<String> VALID_OUTCOMES = Set.of(
            EXPECTED_SUCCESS,
            A2ATErrorCodes.SLOT_VALIDATION_ERROR,
            A2ATErrorCodes.VALIDATION_SEMANTIC_REJECTED,
            A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR,
            A2ATErrorCodes.VALIDATION_PROMPT_RESOURCE_NOT_FOUND,
            A2ATErrorCodes.LLM_INVOCATION_FAILED,
            A2ATErrorCodes.SLOT_SCHEMA_NOT_FOUND,
            A2ATErrorCodes.TEMPLATE_NOT_FOUND,
            A2ATErrorCodes.RENDER_FAILED,
            A2ATErrorCodes.PROMPT_RESOURCE_LOAD_ERROR);

    /**
     * Expected outcome for a scenario.
     *
     * @param outcome expected outcome code ({@code success} or a specific {@code A2ATErrorCodes} value)
     * @param filledParams expected slot values extracted by the server; may be {@code null} for error
     *     scenarios or when no param comparison is needed
     * @param promptText expected rendered prompt text from the client SDK; may be {@code null} for
     *     error scenarios
     * @param slotErrors expected per-slot error details; may be {@code null} or empty when only the
     *     outcome code matters
     */
    public record AuthzExpected(
            String outcome,
            Map<String, Object> filledParams,
            String promptText,
            List<SlotErrorExpectation> slotErrors) {}

    /**
     * Expected error detail for a single slot.
     *
     * @param slotName name of the slot expected to fail
     * @param code machine-readable error code expected for this slot
     */
    public record SlotErrorExpectation(String slotName, String code) {}

    /**
     * Validates that the scenario configuration is well-formed.
     *
     * @param scenario the scenario to validate
     * @throws IllegalArgumentException if the configuration is invalid
     */
    public static void validate(AuthzScenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario must not be null");
        }
        if (scenario.input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (scenario.expected == null) {
            throw new IllegalArgumentException("expected must not be null");
        }
        String outcome = scenario.expected.outcome();
        if (outcome == null || !VALID_OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Invalid expected outcome: " + outcome);
        }
        if (!FROM_TEXT.equals(scenario.entry) && !FROM_DATA_WITH_SCHEMA.equals(scenario.entry)) {
            throw new IllegalArgumentException("Invalid entry: " + scenario.entry);
        }
        if (FROM_TEXT.equals(scenario.entry)) {
            Object text = scenario.input.get("text");
            if (!(text instanceof String) || ((String) text).isBlank()) {
                throw new IllegalArgumentException("from_text scenario requires a non-blank input.text");
            }
        }
        if (FROM_DATA_WITH_SCHEMA.equals(scenario.entry)) {
            Object data = scenario.input.get("data");
            if (!(data instanceof Map) || ((Map<?, ?>) data).isEmpty()) {
                throw new IllegalArgumentException("from_data_with_schema scenario requires a non-empty input.data");
            }
            Object schema = scenario.input.get("schema");
            if (!(schema instanceof Map) || ((Map<?, ?>) schema).isEmpty()) {
                throw new IllegalArgumentException("from_data_with_schema scenario requires a non-empty input.schema");
            }
        }
    }
}
