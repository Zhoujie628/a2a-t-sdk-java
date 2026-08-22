package net.openan.a2at.sdk.server.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;

/**
 * LLM-backed semantic validator aligned with the Python server-side compliance flow.
 *
 * @since 2026-06
 */
public final class LlmBackedPromptSemanticValidator implements ServerPromptSemanticValidator {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final LLMClient llmClient;

    private final PromptSlotSchemaLoader slotSchemaLoader;

    private final String systemPrompt;

    private final String userPrompt;

    /**
     * Creates an LLM-backed semantic validator.
     *
     * @param llmClient LLM client for structured generation
     * @param slotSchemaLoader loader for slot schemas
     * @param systemPrompt system prompt for the LLM
     * @param userPrompt user prompt template for the LLM
     */
    public LlmBackedPromptSemanticValidator(
            LLMClient llmClient, PromptSlotSchemaLoader slotSchemaLoader, String systemPrompt, String userPrompt) {
        this.llmClient = llmClient;
        this.slotSchemaLoader = slotSchemaLoader;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    @Override
    public void validate(String processedPromptText, ProcessedPromptMetadata metadata) {
        PromptSlotSchema slotSchema = slotSchemaLoader.loadSlotSchema(metadata.scenarioCode(), metadata.language());
        String payload = llmClient
                .structured(
                        toStructuredMessages(List.of(
                                new PromptMessage("system", systemPrompt),
                                new PromptMessage("user", buildUserPrompt(slotSchema, metadata.slots())))),
                        schema(),
                        null,
                        null)
                .content();
        validateResponse(payload);
    }

    private String buildUserPrompt(PromptSlotSchema slotSchema, Map<String, String> extractedSlots) {
        try {
            return userPrompt
                    + "\n\n{\n"
                    + "  \"slot_json_schema\": "
                    + OBJECT_MAPPER.writeValueAsString(slotSchema)
                    + ",\n"
                    + "  \"extracted_slots\": "
                    + OBJECT_MAPPER.writeValueAsString(extractedSlots)
                    + "\n}";
        } catch (JsonProcessingException error) {
            throw new PromptComplianceCheckException(
                    A2ATErrorCodes.SLOT_VALIDATION_ERROR, "Failed to serialize slot schema", "slot_validation");
        }
    }

    private static Map<String, Object> schema() {
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        itemSchema.put("required", List.of("slot_name", "code", "message"));
        itemSchema.put(
                "properties",
                Map.of(
                        "slot_name", Map.of("type", "string"),
                        "code", Map.of("type", "string"),
                        "message", Map.of("type", "string")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("passed", "errors"));
        schema.put(
                "properties",
                Map.of(
                        "passed", Map.of("type", "boolean"),
                        "errors", Map.of("type", "array", "items", itemSchema)));
        return schema;
    }

    private static List<Map<String, String>> toStructuredMessages(List<PromptMessage> messages) {
        return messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
    }

    private void validateResponse(String payload) {
        Map<String, Object> response = parseResponse(payload);
        boolean passed = Boolean.TRUE.equals(response.get("passed"));
        boolean hasErrors = response.get("errors") instanceof List<?> errors && !errors.isEmpty();
        if (passed && !hasErrors) {
            return;
        }

        Optional<String> message = extractFirstMessage(response.get("errors"));
        throw new PromptComplianceCheckException(
                A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                message.filter(text -> !text.isBlank()).orElse("Slot semantic validation failed."),
                "slot_validation");
    }

    private static Map<String, Object> parseResponse(String payload) {
        try {
            Map<String, Object> response =
                    OBJECT_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
            return Optional.ofNullable(response).orElseGet(Map::of);
        } catch (JsonProcessingException error) {
            throw new PromptComplianceCheckException(
                    A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                            "semantic validation returned invalid JSON",
                            "slot_validation");
        }
    }

    private static Optional<String> extractFirstMessage(Object errorsValue) {
        if (!(errorsValue instanceof List<?> errors) || errors.isEmpty()) {
            return Optional.empty();
        }
        Object firstError = errors.get(0);
        if (!(firstError instanceof Map<?, ?> errorMap)) {
            return Optional.empty();
        }
        Object message = errorMap.get("message");
        return message instanceof String text ? Optional.of(text) : Optional.empty();
    }
}
