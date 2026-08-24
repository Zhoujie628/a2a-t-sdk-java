package net.openan.a2at.sdk.negotiation.testdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;

/**
 * Scripted LLM client of the negotiation test corpus: the single scripted seam of the otherwise fully production-wired
 * engine.
 *
 * <p>The client consumes the resolved script steps of one corpus case strictly step by step: a payload step answers one
 * {@link LLMResponse} carrying the payload text, a failure step replays the marker's real failure form. Every call
 * records the received messages and the call count, so the {@code llmCalls} expectation stays the only calibration
 * point.
 *
 * <p>{@link LlmFailMarker#ASSERTION} is the zero-call proof: any call throws an {@link AssertionError}, which is how the
 * engine proves that the from-data and the differential runs never touch the LLM.
 *
 * <p><strong>Fail-on-overconsumption is on by default</strong> (review risk 6 of the design document): once the script is
 * exhausted the next call fails instead of silently repeating the last answer, because repeat-last semantics would mask
 * a wrong {@code llmCalls} expectation. The flag exists only as an explicit escape hatch for engine self-tests.
 *
 * @since 2026-08
 */
public final class ScriptedNegotiationLlmClient implements LLMClient {

    /** Raw infrastructure detail of the {@code runtime-exception} marker; must never reach a user-visible message. */
    public static final String RUNTIME_EXCEPTION_DETAIL = "scripted-llm-transport-failure";

    /** Raw infrastructure detail of the {@code llm-error} marker; must never reach a user-visible message. */
    public static final String LLM_ERROR_DETAIL = "scripted-llm-error-response";

    /** Unparseable payload content of the {@code non-json} marker. */
    public static final String NON_JSON_CONTENT = "<not a json object>";

    private final List<LlmScriptStep> steps;

    private final boolean failOnOverconsumption;

    private int cursor;

    private int callCount;

    private final List<List<Map<String, String>>> recordedMessages = new ArrayList<>();

    private final List<Map<String, Object>> recordedSchemas = new ArrayList<>();

    private final List<String> leakedFailureDetails = new ArrayList<>();

    /**
     * Creates a strictly consuming scripted client.
     *
     * @param steps resolved script steps, consumed strictly step by step
     */
    public ScriptedNegotiationLlmClient(List<LlmScriptStep> steps) {
        this(steps, true);
    }

    /**
     * Creates a scripted client with an explicit over-consumption policy.
     *
     * @param steps resolved script steps, consumed strictly step by step
     * @param failOnOverconsumption true fails once the script is exhausted; false repeats the last step (escape hatch)
     */
    public ScriptedNegotiationLlmClient(List<LlmScriptStep> steps, boolean failOnOverconsumption) {
        this.steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        this.failOnOverconsumption = failOnOverconsumption;
    }

    /**
     * Creates the zero-call proof client: any call throws an {@link AssertionError}.
     *
     * @return assertion-only scripted client
     */
    public static ScriptedNegotiationLlmClient assertionOnly() {
        return new ScriptedNegotiationLlmClient(List.of(new LlmScriptStep.Fail(LlmFailMarker.ASSERTION)));
    }

    @Override
    public LLMResponse structured(
            List<Map<String, String>> messages,
            Map<String, Object> jsonSchema,
            Double temperature,
            Integer maxTokens) {
        callCount++;
        recordedMessages.add(messages == null ? List.of() : List.copyOf(messages));
        recordedSchemas.add(jsonSchema);
        if (cursor >= steps.size()) {
            if (failOnOverconsumption) {
                throw new IllegalStateException("The scripted LLM client consumed its whole script of "
                        + steps.size() + " step(s); an exhausted script fails instead of repeating the last answer"
                        + " (the llmCalls expectation is the only calibration point).");
            }
            cursor = steps.size() - 1;
        }
        LlmScriptStep step = steps.get(cursor);
        cursor++;
        if (step instanceof LlmScriptStep.Payload payload) {
            return new LLMResponse(
                    payload.json(), "scripted-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
        return failAs(((LlmScriptStep.Fail) step).marker());
    }

    /**
     * Returns the number of LLM calls made through this client, including the calls that failed.
     *
     * @return non-negative call count
     */
    public int callCount() {
        return callCount;
    }

    /**
     * Returns the messages of every call, one entry per call.
     *
     * @return recorded messages per call
     */
    public List<List<Map<String, String>>> recordedMessages() {
        return List.copyOf(recordedMessages);
    }

    /**
     * Returns the messages of the most recent call.
     *
     * @return messages of the last call, or an empty list before the first call
     */
    public List<Map<String, String>> lastMessages() {
        return recordedMessages.isEmpty() ? List.of() : recordedMessages.get(recordedMessages.size() - 1);
    }

    /**
     * Returns the JSON schema of the most recent call.
     *
     * @return schema of the last call, or null before the first call
     */
    public Map<String, Object> lastSchema() {
        return recordedSchemas.isEmpty() ? null : recordedSchemas.get(recordedSchemas.size() - 1);
    }

    /**
     * Returns the raw infrastructure details of the scripted failures that were replayed, for the
     * {@code noLlmLeakInUserMessage} contract.
     *
     * @return raw failure details that must never surface in a user-visible message
     */
    public List<String> leakedFailureDetails() {
        return List.copyOf(leakedFailureDetails);
    }

    private LLMResponse failAs(LlmFailMarker marker) {
        return switch (marker) {
            case RUNTIME_EXCEPTION -> {
                leakedFailureDetails.add(RUNTIME_EXCEPTION_DETAIL);
                throw new IllegalStateException(RUNTIME_EXCEPTION_DETAIL);
            }
            case LLM_ERROR -> {
                leakedFailureDetails.add(LLM_ERROR_DETAIL);
                throw new LLMRuntimeError(LLM_ERROR_DETAIL);
            }
            case NULL_RESPONSE -> null;
            case BLANK_CONTENT -> new LLMResponse(
                    "", "scripted-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
            case NON_JSON -> new LLMResponse(
                    NON_JSON_CONTENT, "scripted-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
            case ASSERTION -> throw new AssertionError(
                    "No LLM call was expected in this run (assertion-only client), but one happened with messages: "
                            + recordedMessages.get(recordedMessages.size() - 1));
        };
    }
}
