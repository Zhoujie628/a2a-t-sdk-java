package net.openan.a2at.sdk.prompt.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.json.JacksonJsonValueParser;
import net.openan.a2at.sdk.core.json.JsonValueParser;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.SemanticValidator;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ValidationResult;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMError;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default LLM-backed semantic validator that delegates to a structured LLM call for content validation and parameter
 * extraction.
 *
 * <p>The content_validation prompt resources are internal LLM instructions. Like the negotiation semantic validation
 * prompts, they are always loaded from the classpath regardless of the configured prompt source type — the local
 * resource root only overrides business resources (templates, slots, scenarios).
 *
 * @since 2026-08
 */
final class DefaultSemanticValidator implements SemanticValidator<TemplateUri> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSemanticValidator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PROMPT_RESOURCE_ROOT = "prompt_resources/prompts/content_validation/";

    private final LLMClient llmClient;
    private final String systemPrompt;
    private final String userPromptTemplate;
    private final JsonValueParser jsonValueParser;

    /**
     * Creates a semantic validator backed by the given LLM client and prompt resources for the specified language.
     *
     * @param llmClient LLM client for structured calls; may be {@code null}, in which case {@link #validate}
     *     fails with {@code ContentValidationException} carrying
     *     {@code VALIDATION_LLM_INFRASTRUCTURE_ERROR}; there is no late injection point
     * @param language language code for prompt resource loading
     * @throws ResourceNotFoundException if the content_validation prompt resources of the given language are missing
     *     on the classpath
     */
    DefaultSemanticValidator(@Nullable LLMClient llmClient, @NonNull String language) {
        this.llmClient = llmClient;
        this.systemPrompt = loadPromptResource("system.md", language);
        this.userPromptTemplate = loadPromptResource("user.md", language);
        this.jsonValueParser = new JacksonJsonValueParser();
    }

    @Override
    public ValidationResult validate(
            @NonNull String prompt, @NonNull Map<String, Object> schema, @NonNull TemplateUri reference) {
        if (llmClient == null) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR,
                    "LLM client is not configured for semantic validation.");
        }

        String userPrompt = fillUserPrompt(prompt, schema, reference);
        Map<String, Object> outputSchema = buildOutputSchema();
        List<Map<String, String>> messages = toStructuredMessages(
                List.of(new PromptMessage("system", systemPrompt), new PromptMessage("user", userPrompt)));

        LLMResponse response;
        try {
            response = llmClient.structured(messages, outputSchema, null, null);
        } catch (LLMError error) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR,
                    "Semantic validation LLM invocation failed: " + error.getMessage(),
                    error);
        }
        Map<String, Object> parsed;
        try {
            parsed = jsonValueParser.parseObject(response.content());
        } catch (RuntimeException exception) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR,
                    "Semantic validation LLM response is not valid JSON: " + exception.getMessage(),
                    List.of(new SlotValidationError(
                            "_llm", A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR, exception.getMessage())),
                    exception);
        }

        boolean verdict = parseVerdict(parsed);
        List<SlotValidationError> errors = parseErrors(parsed.get("errors"));
        Map<String, Object> params = parseParams(parsed.get("params"));

        LOGGER.atDebug().log(
                "semantic_validation_completed verdict={} error_count={} param_count={}",
                verdict,
                errors.size(),
                params.size());
        return new ValidationResult(verdict, errors, params);
    }

    private static String loadPromptResource(String fileName, String language) {
        String classpathPath = PROMPT_RESOURCE_ROOT + language + "/" + fileName;
        InputStream stream = ClasspathResourceStreams.open(classpathPath);
        if (stream == null) {
            throw new ResourceNotFoundException(
                    "Content validation prompt resource does not exist for language " + language
                            + "; set A2AT_LANGUAGE to a language with bundled prompt resources (zh-CN or en-US).",
                    classpathPath);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new A2ATError("Failed to read content validation prompt: " + classpathPath, exception);
        }
    }

    private String fillUserPrompt(String prompt, Map<String, Object> schema, TemplateUri reference) {
        String schemaJson;
        try {
            schemaJson = OBJECT_MAPPER.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new A2ATError("Failed to serialize schema to JSON.", exception);
        }

        return userPromptTemplate
                .replaceAll("\\[extension_name\\]", Matcher.quoteReplacement(reference.extensionName()))
                .replaceAll("\\[input\\]", Matcher.quoteReplacement(prompt))
                .replaceAll("\\[template_uri\\]", Matcher.quoteReplacement(reference.uri()))
                .replaceAll("\\[schema\\]", Matcher.quoteReplacement(schemaJson));
    }

    private static Map<String, Object> buildOutputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> verdictProp = new LinkedHashMap<>();
        verdictProp.put("type", "boolean");
        properties.put("semantic_verdict", verdictProp);

        Map<String, Object> errorItemProperties = new LinkedHashMap<>();
        errorItemProperties.put("slot_name", Map.of("type", "string"));
        errorItemProperties.put("code", Map.of("type", "string"));
        errorItemProperties.put("message", Map.of("type", "string"));
        Map<String, Object> errorItem = new LinkedHashMap<>();
        errorItem.put("type", "object");
        errorItem.put("properties", errorItemProperties);
        errorItem.put("required", List.of("slot_name", "code", "message"));
        Map<String, Object> errorsProp = new LinkedHashMap<>();
        errorsProp.put("type", "array");
        errorsProp.put("items", errorItem);
        properties.put("errors", errorsProp);

        Map<String, Object> paramsProp = new LinkedHashMap<>();
        paramsProp.put("type", "object");
        properties.put("params", paramsProp);

        schema.put("properties", properties);
        schema.put("required", List.of("semantic_verdict", "errors", "params"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static List<Map<String, String>> toStructuredMessages(List<PromptMessage> messages) {
        return messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
    }

    private static boolean parseVerdict(Map<String, Object> parsed) {
        if (!(parsed.get("semantic_verdict") instanceof Boolean verdict)) {
            throw contractViolation("semantic_verdict must be a boolean.");
        }
        return verdict;
    }

    private static List<SlotValidationError> parseErrors(Object errorsValue) {
        if (!(errorsValue instanceof List<?> errors)) {
            throw contractViolation("errors must be an array.");
        }
        List<SlotValidationError> normalized = new ArrayList<>();
        for (Object errorValue : errors) {
            if (!(errorValue instanceof Map<?, ?> errorMap)) {
                throw contractViolation("errors must be objects with slot_name, code and message.");
            }
            if (!(errorMap.get("slot_name") instanceof String slotName)
                    || !(errorMap.get("code") instanceof String code)
                    || !(errorMap.get("message") instanceof String message)) {
                throw contractViolation("errors must carry string slot_name, code and message values.");
            }
            normalized.add(new SlotValidationError(slotName, code, message));
        }
        return List.copyOf(normalized);
    }

    /**
     * Normalizes the LLM-extracted parameter map. Keys with a {@code null} value are preserved: a {@code null}
     * parameter is the semantic validator's explicit signal that a schema slot is missing from the content, and
     * downstream missing-parameter detection (negotiation triggering) relies on the key being present with a null
     * value. Dropping the key would make a missing slot indistinguishable from an absent one.
     */
    private static Map<String, Object> parseParams(Object paramsValue) {
        if (!(paramsValue instanceof Map<?, ?> params)) {
            throw contractViolation("params must be an object.");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : params.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw contractViolation("params keys must be strings.");
            }
            normalized.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static ContentValidationException contractViolation(String message) {
        return new ContentValidationException(
                A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR,
                "Semantic validation LLM response violates the output contract: " + message,
                List.of(new SlotValidationError(
                        "_llm", A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR, message)));
    }
}
