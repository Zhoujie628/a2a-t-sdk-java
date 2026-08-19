package net.openan.a2at.sdk.negotiation.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.json.JacksonJsonValueParser;
import net.openan.a2at.sdk.core.json.JsonValueParser;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InfoEndingContent;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContentException;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;

/**
 * Default content extractor backed by one structured LLM call.
 *
 * <p>The extractor loads the system and user prompts of the addressed negotiation type from the classpath, asks the LLM
 * for the snake_case JSON described by the extraction schema, and maps the response onto the typed content records.
 * Failure mapping: transport failures map to {@code negotiation_llm_infrastructure_error}, responses that cannot be
 * parsed as the expected content map to {@code negotiation_content_extract_failed}, missing required fields map to the
 * non-retryable {@code negotiation_slot_missing}, and content that contradicts the addressed phase or action maps to
 * {@code negotiation_invalid_input}.
 *
 * @since 2026-06
 */
public final class DefaultNegotiationContentExtractor implements NegotiationContentExtractor {

    private static final String CATEGORY_INFORMATION = "information_negotiation";

    private static final String CATEGORY_TARGET = "target_negotiation";

    private static final String CATEGORY_FEASIBILITY = "feasibility_negotiation";

    private final LLMClient llmClient;

    private final NegotiationMessageBuilder messageBuilder;

    private final NegotiationJsonSchemaBuilder schemaBuilder;

    private final JsonValueParser parser;

    /**
     * Creates an extractor backed by one LLM client and the default message and schema builders.
     *
     * @param llmClient LLM client used for the structured content extraction call
     */
    public DefaultNegotiationContentExtractor(LLMClient llmClient) {
        this(
                llmClient,
                new NegotiationMessageBuilder(),
                new NegotiationJsonSchemaBuilder(),
                new JacksonJsonValueParser());
    }

    /**
     * Creates an extractor with injectable collaborators.
     *
     * @param llmClient LLM client used for the structured content extraction call
     * @param messageBuilder builder assembling the LLM messages of the extraction step
     * @param schemaBuilder builder providing the extraction JSON Schema
     * @param parser parser turning the LLM response content into a key-value map
     */
    public DefaultNegotiationContentExtractor(
            LLMClient llmClient,
            NegotiationMessageBuilder messageBuilder,
            NegotiationJsonSchemaBuilder schemaBuilder,
            JsonValueParser parser) {
        this.llmClient = llmClient;
        this.messageBuilder = messageBuilder;
        this.schemaBuilder = schemaBuilder;
        this.parser = parser;
    }

    /**
     * Extracts the typed content of one negotiation message from free text.
     *
     * @param text free-text input describing the message content
     * @param reference reference identifying the negotiation type, phase and language to extract for
     * @return typed negotiation content matching the reference
     * @throws NegotiationContentException if the reference is null or the text is blank
     * @throws NegotiationGenerationException with one of the codes {@code negotiation_llm_infrastructure_error},
     *     {@code negotiation_content_extract_failed}, {@code negotiation_slot_missing} or
     *     {@code negotiation_invalid_input}
     */
    @Override
    public NegotiationContent extract(String text, NegotiationReference reference) {
        if (reference == null) {
            throw new NegotiationContentException("Negotiation reference must not be null.", "reference");
        }
        if (text == null || text.isBlank()) {
            throw new NegotiationGenerationException(
                    A2ATErrorCodes.NEGOTIATION_INVALID_INPUT,
                    "Input text for negotiation content extraction must not" + " be blank.");
        }
        Map<String, String> tokens = Map.of(
                NegotiationMessageBuilder.TOKEN_PHASE,
                phaseToken(reference.phase()),
                NegotiationMessageBuilder.TOKEN_INPUT,
                text);
        List<Map<String, String>> messages =
                messageBuilder.buildMessages(promptCategory(reference.type()), reference.language(), tokens);
        Map<String, Object> schema = schemaBuilder.buildExtractionSchema(reference.type(), reference.phase());
        Map<String, Object> payload = invokeLlm(messages, schema);
        return mapContent(payload, reference.type(), reference.phase());
    }

    private Map<String, Object> invokeLlm(List<Map<String, String>> messages, Map<String, Object> schema) {
        if (llmClient == null) {
            throw new NegotiationGenerationException(
                    A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR,
                    "Negotiation content extraction requires an LLM client but none is configured.");
        }
        LLMResponse response;
        try {
            response = llmClient.structured(messages, schema, null, null);
        } catch (RuntimeException error) {
            throw new NegotiationGenerationException(
                    A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR,
                    "Negotiation content extraction LLM call failed.",
                    error);
        }
        String content = response == null ? null : response.content();
        if (content == null || content.isBlank()) {
            throw new NegotiationGenerationException(
                    A2ATErrorCodes.NEGOTIATION_CONTENT_EXTRACT_FAILED,
                    "Negotiation content extraction returned an empty response.");
        }
        try {
            return parser.parseObject(content);
        } catch (RuntimeException error) {
            throw new NegotiationGenerationException(
                    A2ATErrorCodes.NEGOTIATION_CONTENT_EXTRACT_FAILED,
                    "Negotiation content extraction response is not a JSON object.",
                    error);
        }
    }

    private static NegotiationContent mapContent(
            Map<String, Object> payload, NegotiationType type, NegotiationPhase phase) {
        if (phase == NegotiationPhase.PROPOSE) {
            return mapProposeContent(payload, type);
        }
        return mapEndingContent(payload, type, phase);
    }

    private static NegotiationContent mapProposeContent(Map<String, Object> payload, NegotiationType type) {
        return switch (type) {
            case INFORMATION -> new InfoProposeContent(
                    requiredItems(payload, "items"), optionalString(payload, "relationship"));
            case TARGET -> new TargetProposeContent(
                    requiredString(payload, "target_negotiation_description"),
                    optionalItems(payload, "intent_understanding"),
                    optionalItems(payload, "alignment_and_clarification"),
                    optionalItems(payload, "request_for_clarification"));
            case FEASIBILITY -> mapFeasibilityProposeContent(payload);
        };
    }

    private static NegotiationContent mapFeasibilityProposeContent(Map<String, Object> payload) {
        String description = requiredString(payload, "feasibility_negotiation_description");
        NegotiationAction action = feasibilityAction(payload);
        List<NegotiationItem> contentsToEvaluate = optionalItems(payload, "contents_to_evaluate");
        List<NegotiationItem> infeasibilityDetails = optionalItems(payload, "infeasibility_details_and_proposal");
        if (action == NegotiationAction.REQUEST_FEASIBILITY_EVALUATION) {
            if (contentsToEvaluate == null || contentsToEvaluate.isEmpty()) {
                throw invalidInput(
                        "Feasibility evaluation request extracted no contents to evaluate; the driven section would"
                                + " be empty.");
            }
        } else if (infeasibilityDetails == null || infeasibilityDetails.isEmpty()) {
            throw invalidInput(
                    "Alternative proposal on failure extracted no infeasibility details; the driven section would be"
                            + " empty.");
        }
        return new FeasibilityProposeContent(description, action, contentsToEvaluate, infeasibilityDetails);
    }

    private static NegotiationContent mapEndingContent(
            Map<String, Object> payload, NegotiationType type, NegotiationPhase phase) {
        NegotiationConclusion conclusion = requiredConclusion(payload);
        requireConclusionMatchesPhase(conclusion, phase);
        return switch (type) {
            case INFORMATION -> new InfoEndingContent(conclusion, requiredItems(payload, "items"));
            case TARGET -> mapTargetEndingContent(payload, conclusion);
            case FEASIBILITY -> new FeasibilityEndingContent(
                    conclusion, requiredString(payload, "feasibility_summary"));
        };
    }

    private static NegotiationContent mapTargetEndingContent(
            Map<String, Object> payload, NegotiationConclusion conclusion) {
        String confirmedIntent = optionalString(payload, "confirmed_intent");
        String failureReason = optionalString(payload, "failure_reason");
        if (conclusion == NegotiationConclusion.ACCEPT && (confirmedIntent == null || confirmedIntent.isBlank())) {
            throw slotMissing("confirmed_intent of an accepting target negotiation message");
        }
        if (conclusion == NegotiationConclusion.REJECT && (failureReason == null || failureReason.isBlank())) {
            throw slotMissing("failure_reason of a rejecting target negotiation message");
        }
        return new TargetEndingContent(conclusion, confirmedIntent, failureReason);
    }

    private static void requireConclusionMatchesPhase(NegotiationConclusion conclusion, NegotiationPhase phase) {
        NegotiationConclusion expected =
                phase == NegotiationPhase.ACCEPT ? NegotiationConclusion.ACCEPT : NegotiationConclusion.REJECT;
        if (conclusion != expected) {
            throw invalidInput("Extracted conclusion " + conclusion.literal() + " does not match the " + phase
                    + " phase; the" + " expected conclusion is " + expected.literal() + ".");
        }
    }

    private static NegotiationAction feasibilityAction(Map<String, Object> payload) {
        Object value = payload.get("action");
        if (value == null) {
            throw invalidInput(
                    "Feasibility negotiation content extraction produced no action; the action drives the conditional"
                            + " sections of the message.");
        }
        if (value instanceof String action) {
            for (NegotiationAction candidate : NegotiationAction.values()) {
                if (candidate.name().equals(action)) {
                    return candidate;
                }
            }
        }
        throw extractFailed("Feasibility negotiation action must be one of the two action names but was: " + value);
    }

    private static NegotiationConclusion requiredConclusion(Map<String, Object> payload) {
        Object value = payload.get("conclusion");
        if (value == null) {
            throw slotMissing("conclusion of a terminal negotiation message");
        }
        if (value instanceof String conclusion) {
            for (NegotiationConclusion candidate : NegotiationConclusion.values()) {
                if (candidate.literal().equals(conclusion)) {
                    return candidate;
                }
            }
        }
        throw extractFailed("Negotiation conclusion must be Accept or Reject but was: " + value);
    }

    private static String requiredString(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw slotMissing(field);
        }
        if (value instanceof String text) {
            return text;
        }
        throw extractFailed("Field " + field + " must be a string but was: " + value);
    }

    private static String optionalString(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw extractFailed("Field " + field + " must be a string or null but was: " + value);
    }

    private static List<NegotiationItem> requiredItems(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null) {
            throw slotMissing(field);
        }
        return itemsOf(value, field);
    }

    private static List<NegotiationItem> optionalItems(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null) {
            return null;
        }
        return itemsOf(value, field);
    }

    private static List<NegotiationItem> itemsOf(Object value, String field) {
        if (!(value instanceof List<?> entries)) {
            throw extractFailed("Field " + field + " must be an array of items but was: " + value);
        }
        List<NegotiationItem> items = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> entryMap)) {
                throw extractFailed("Field " + field + " must contain item objects but contained: " + entry);
            }
            Object name = entryMap.get("name");
            if (!(name instanceof String itemName) || itemName.isBlank()) {
                throw extractFailed("Field " + field + " contained an item without a name: " + entry);
            }
            Object itemValue = entryMap.get("value");
            if (itemValue != null && !(itemValue instanceof String)) {
                throw extractFailed("Field " + field + " contained an item whose value is not a string: " + entry);
            }
            items.add(new NegotiationItem(itemName, (String) itemValue));
        }
        return List.copyOf(items);
    }

    private static String phaseToken(NegotiationPhase phase) {
        return switch (phase) {
            case PROPOSE -> "propose";
            case ACCEPT -> "accept";
            case REJECT -> "reject";
        };
    }

    private static String promptCategory(NegotiationType type) {
        return switch (type) {
            case INFORMATION -> CATEGORY_INFORMATION;
            case TARGET -> CATEGORY_TARGET;
            case FEASIBILITY -> CATEGORY_FEASIBILITY;
        };
    }

    private static NegotiationGenerationException slotMissing(String field) {
        return new NegotiationGenerationException(
                A2ATErrorCodes.NEGOTIATION_SLOT_MISSING,
                "Negotiation content extraction response is missing the required field: " + field);
    }

    private static NegotiationGenerationException extractFailed(String message) {
        return new NegotiationGenerationException(A2ATErrorCodes.NEGOTIATION_CONTENT_EXTRACT_FAILED, message);
    }

    private static NegotiationGenerationException invalidInput(String message) {
        return new NegotiationGenerationException(A2ATErrorCodes.NEGOTIATION_INVALID_INPUT, message);
    }
}
