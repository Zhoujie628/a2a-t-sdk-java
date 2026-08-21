package net.openan.a2at.sdk.prompt.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.json.JacksonJsonValueParser;
import net.openan.a2at.sdk.core.json.JsonValueParser;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.SemanticValidator;
import net.openan.a2at.sdk.core.validation.TemplateUri;
import net.openan.a2at.sdk.core.validation.ValidationResult;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.resources.loader.PromptResourceAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default LLM-backed semantic validator that delegates to a structured LLM call for content validation and parameter
 * extraction.
 *
 * @since 2026-08
 */
public final class DefaultSemanticValidator implements SemanticValidator<TemplateUri> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSemanticValidator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LLMClient llmClient;
    private final String systemPrompt;
    private final String userPromptTemplate;
    private final JsonValueParser jsonValueParser;

    /**
     * Creates a semantic validator backed by the given LLM client and prompt resources for the specified language.
     *
     * @param llmClient LLM client for structured calls; may be {@code null} and set later
     * @param language language code for prompt resource loading
     * @param promptResourceAccess prompt resource access for loading validation prompts
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if prompt resources are missing
     */
    public DefaultSemanticValidator(
            LLMClient llmClient, String language, PromptResourceAccess promptResourceAccess) {
        this.llmClient = llmClient;
        this.systemPrompt = promptResourceAccess.loadPrompt("content_validation", language, "system");
        this.userPromptTemplate = promptResourceAccess.loadPrompt("content_validation", language, "user");
        this.jsonValueParser = new JacksonJsonValueParser();
    }

    @Override
    public ValidationResult validate(String prompt, Map<String, Object> schema, TemplateUri reference) {
        if (llmClient == null) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR,
                    "LLM client is not configured for semantic validation.");
        }

        String userPrompt = fillUserPrompt(prompt, schema, reference);
        Map<String, Object> outputSchema = buildOutputSchema();
        List<Map<String, String>> messages = toStructuredMessages(
                List.of(new PromptMessage("system", systemPrompt), new PromptMessage("user", userPrompt)));

        LLMResponse response = llmClient.structured(messages, outputSchema, null, null);
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

        boolean verdict = Boolean.TRUE.equals(parsed.get("semantic_verdict"));
        List<SlotValidationError> errors = parseErrors(parsed.get("errors"));
        Map<String, Object> params = parseParams(parsed.get("params"));

        LOGGER.atDebug().log(
                "semantic_validation_completed verdict={} error_count={} param_count={}",
                verdict,
                errors.size(),
                params.size());
        return new ValidationResult(verdict, errors, params);
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

        Map<String, Object> errorsProp = new LinkedHashMap<>();
        errorsProp.put("type", "array");
        properties.put("errors", errorsProp);

        Map<String, Object> paramsProp = new LinkedHashMap<>();
        paramsProp.put("type", "object");
        properties.put("params", paramsProp);

        schema.put("properties", properties);
        schema.put("required", List.of("semantic_verdict"));
        return schema;
    }

    private static List<Map<String, String>> toStructuredMessages(List<PromptMessage> messages) {
        return messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<SlotValidationError> parseErrors(Object errorsValue) {
        if (!(errorsValue instanceof List<?> errors)) {
            return List.of();
        }
        List<SlotValidationError> normalized = new ArrayList<>();
        for (Object errorValue : errors) {
            if (!(errorValue instanceof Map<?, ?> errorMap)) {
                continue;
            }
            String slotName = asString(errorMap.get("slot_name"));
            String code = asString(errorMap.get("code"));
            String message = asString(errorMap.get("message"));
            normalized.add(new SlotValidationError(slotName, code, message));
        }
        return List.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseParams(Object paramsValue) {
        if (!(paramsValue instanceof Map<?, ?> params)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        params.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return Map.copyOf(normalized);
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : "";
    }
}