package net.openan.a2at.sdk.negotiation.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMError;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default LLM-backed semantic validator for negotiation messages.
 *
 * <p>One structured LLM call performs the semantic validation and the parameter extraction together. The call loads the
 * semantic validation prompt resources of the reference language from the classpath, fills the literal bracket tokens
 * of the user prompt with the declared negotiation type, the template URI, the serialized caller schema and the message
 * text, and passes the caller schema merged into the semantic validation output contract as the output schema.
 *
 * <p>After the call the validator enforces the output contract in code: the response must contain the four required
 * keys with the expected shapes, otherwise the internal {@link NegotiationValidationException} is thrown for the
 * orchestration layer to map to the retryable LLM infrastructure error code. When the verdict is true, the reported
 * negotiation type must be present and must match the type declared by the template reference; a null or mismatching
 * type turns the outcome into a semantic rejection carrying a {@code section.*} error. Phase consistency between the
 * declared template and the message sections is part of the semantic tasks performed by the LLM and surfaces through
 * the returned errors.
 *
 * @since 2026-06
 */
public final class DefaultNegotiationSemanticValidator implements NegotiationSemanticValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNegotiationSemanticValidator.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PROMPT_RESOURCE_ROOT = "prompt_resources/prompts/negotiation_semantic_validation/";

    private static final String SYSTEM_PROMPT_FILE = "system.md";

    private static final String USER_PROMPT_FILE = "user.md";

    private static final String KEY_SEMANTIC_VERDICT = "semantic_verdict";

    private static final String KEY_NEGOTIATION_TYPE = "negotiation_type";

    private static final String KEY_ERRORS = "errors";

    private static final String KEY_PARAMS = "params";

    private final LLMClient llmClient;

    private final SemanticSchemaBuilder schemaBuilder;

    /**
     * Creates the default semantic validator.
     *
     * @param llmClient LLM client used for the single structured call
     * @param schemaBuilder collaborator merging the caller schema into the semantic validation output contract; the
     *     orchestrator builder wires the generation-package schema builder to this seam
     * @throws NullPointerException if the LLM client or the schema builder is null
     */
    public DefaultNegotiationSemanticValidator(LLMClient llmClient, SemanticSchemaBuilder schemaBuilder) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.schemaBuilder = Objects.requireNonNull(schemaBuilder, "schemaBuilder");
    }

    /**
     * Validates one rendered negotiation message semantically and extracts its parameters in a single LLM call.
     *
     * @param prompt rendered negotiation message text
     * @param callerSchema caller-provided parameter JSON schema embedded into the structured-call output contract
     * @param reference template reference the message is validated against, carrying the declared type, phase and
     *     language
     * @return semantic validation outcome carrying the verdict, the implied negotiation type, the semantic errors and
     *     the extracted parameters
     * @throws NegotiationValidationException if the LLM invocation fails or the response misses a required key or has
     *     the wrong shape
     * @throws ResourceNotFoundException if the semantic validation prompt resources of the reference language are
     *     missing
     */
    @Override
    public SemanticValidationResult validateNegotiation(
            String prompt, Map<String, Object> callerSchema, NegotiationReference reference) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(reference, "reference");
        List<Map<String, String>> messages = buildMessages(prompt, callerSchema, reference);
        Map<String, Object> mergedSchema = schemaBuilder.buildSemanticValidationSchema(callerSchema);
        LLMResponse response;
        try {
            response = llmClient.structured(messages, mergedSchema, null, null);
        } catch (LLMError error) {
            throw new NegotiationValidationException(
                    "Semantic validation LLM invocation failed: " + error.getMessage(), error);
        }
        SemanticValidationResult result = interpret(parseResponse(response.content()), reference);
        LOGGER.atInfo().log(
                "negotiation_semantic_validation_completed verdict={} error_count={}",
                result.verdict(),
                result.errors().size());
        return result;
    }

    private static List<Map<String, String>> buildMessages(
            String prompt, Map<String, Object> callerSchema, NegotiationReference reference) {
        String language = reference.language();
        String systemPrompt = loadPromptResource(SYSTEM_PROMPT_FILE, language);
        String userPrompt = loadPromptResource(USER_PROMPT_FILE, language);
        String filledUserPrompt = userPrompt
                .replace("[phase]", reference.phase().name().toLowerCase(Locale.ROOT))
                .replace("[input]", prompt)
                .replace("[template_uri]", reference.uri())
                .replace("[negotiation_type]", reference.type().name().toLowerCase(Locale.ROOT))
                .replace("[schema]", toJson(callerSchema));
        return List.of(
                Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", filledUserPrompt));
    }

    private static String loadPromptResource(String fileName, String language) {
        String classpathPath = PROMPT_RESOURCE_ROOT + language + "/" + fileName;
        InputStream stream = ClasspathResourceStreams.open(classpathPath);
        if (stream == null) {
            throw new ResourceNotFoundException(
                    "Negotiation semantic validation prompt resource does not exist for language " + language
                            + "; set A2AT_LANGUAGE to a language with bundled prompt resources (zh-CN or en-US).",
                    classpathPath);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new A2ATError(
                    "Failed to read negotiation semantic validation prompt: " + classpathPath, exception);
        }
    }

    private static String toJson(Map<String, Object> callerSchema) {
        try {
            return OBJECT_MAPPER.writeValueAsString(callerSchema == null ? Map.of() : callerSchema);
        } catch (JsonProcessingException error) {
            throw new NegotiationValidationException("Failed to serialize the caller parameter schema.", error);
        }
    }

    private static Map<String, Object> parseResponse(String content) {
        if (content == null || content.isBlank()) {
            throw new NegotiationValidationException("Semantic validation response is empty.");
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException error) {
            throw new NegotiationValidationException(
                    "Semantic validation response is not a JSON object: " + error.getMessage(), error);
        }
    }

    private static SemanticValidationResult interpret(Map<String, Object> response, NegotiationReference reference) {
        if (!(response.get(KEY_SEMANTIC_VERDICT) instanceof Boolean verdict)) {
            throw new NegotiationValidationException(
                    "Semantic validation response key semantic_verdict must be a boolean.");
        }
        if (!response.containsKey(KEY_NEGOTIATION_TYPE)) {
            throw new NegotiationValidationException(
                    "Semantic validation response is missing the required key negotiation_type.");
        }
        Object typeValue = response.get(KEY_NEGOTIATION_TYPE);
        if (typeValue != null && !(typeValue instanceof String)) {
            throw new NegotiationValidationException(
                    "Semantic validation response key negotiation_type must be a string or null.");
        }
        if (!(response.get(KEY_ERRORS) instanceof List<?> rawErrors)) {
            throw new NegotiationValidationException("Semantic validation response key errors must be an array.");
        }
        if (!(response.get(KEY_PARAMS) instanceof Map<?, ?> rawParams)) {
            throw new NegotiationValidationException("Semantic validation response key params must be an object.");
        }

        List<SlotValidationError> errors = parseErrors(rawErrors);
        Map<String, Object> params = parseParams(rawParams);
        String negotiationType = (String) typeValue;

        if (verdict) {
            if (negotiationType == null) {
                List<SlotValidationError> rejectionErrors = new ArrayList<>(errors);
                rejectionErrors.add(typeConsistencyError(
                        reference.type(),
                        "Semantic verdict is true but negotiation_type is null; the implied type must match the"
                                + " declared template."));
                return new SemanticValidationResult(false, null, rejectionErrors, params);
            }
            NegotiationType impliedType = parseImpliedType(negotiationType);
            if (impliedType != reference.type()) {
                NegotiationType sectionType = impliedType == null ? reference.type() : impliedType;
                List<SlotValidationError> rejectionErrors = new ArrayList<>(errors);
                rejectionErrors.add(typeConsistencyError(
                        sectionType,
                        "Message implies negotiation type " + negotiationType
                                + " but the declared template URI expects " + declaredTypeName(reference) + "."));
                return new SemanticValidationResult(false, negotiationType, rejectionErrors, params);
            }
        }
        return new SemanticValidationResult(verdict, negotiationType, errors, params);
    }

    private static List<SlotValidationError> parseErrors(List<?> rawErrors) {
        List<SlotValidationError> errors = new ArrayList<>();
        for (Object rawError : rawErrors) {
            if (!(rawError instanceof Map<?, ?> errorMap)) {
                throw new NegotiationValidationException(
                        "Semantic validation response errors must be objects with slot_name, code and message.");
            }
            if (!(errorMap.get("slot_name") instanceof String slotName)
                    || !(errorMap.get("code") instanceof String code)
                    || !(errorMap.get("message") instanceof String message)) {
                throw new NegotiationValidationException(
                        "Semantic validation response errors must carry string slot_name, code and message values.");
            }
            errors.add(new SlotValidationError(slotName, code, message));
        }
        return errors;
    }

    private static Map<String, Object> parseParams(Map<?, ?> rawParams) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawParams.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new NegotiationValidationException("Semantic validation response params keys must be strings.");
            }
            params.put(key, entry.getValue());
        }
        return params;
    }

    private static SlotValidationError typeConsistencyError(NegotiationType sectionType, String message) {
        return new SlotValidationError(declaredTypeSectionKey(sectionType), "template_type_mismatch", message);
    }

    /** Parses the negotiation type reported for the message; null when the reported type is none of the known types. */
    private static NegotiationType parseImpliedType(String negotiationType) {
        for (NegotiationType candidate : NegotiationType.values()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(negotiationType)) {
                return candidate;
            }
        }
        return null;
    }

    private static String declaredTypeName(NegotiationReference reference) {
        return reference.type().name().toLowerCase(Locale.ROOT);
    }

    private static String declaredTypeSectionKey(NegotiationType type) {
        switch (type) {
            case INFORMATION:
                return "section.info_static";
            case TARGET:
                return "section.target";
            case FEASIBILITY:
                return "section.feasibility";
            default:
                throw new NegotiationValidationException("Unknown negotiation type " + type + ".");
        }
    }
}
