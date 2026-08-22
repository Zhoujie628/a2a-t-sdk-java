package net.openan.a2at.sdk.prompt.analysis.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotValidationError;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;

/**
 * Shared LLM-backed structured slot extractor.
 *
 * @since 2026-06
 */
public final class DefaultStructuredPromptSlotValueExtractor implements PromptSlotValueExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ObjectWriter SLOT_SERIALIZER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .writerWithDefaultPrettyPrinter();

    private final LLMClient llmClient;

    private final PromptSlotSchemaLoader slotSchemaLoader;

    private final String systemPrompt;

    private final String userPrompt;

    public DefaultStructuredPromptSlotValueExtractor(
            LLMClient llmClient, PromptSlotSchemaLoader slotSchemaLoader, String systemPrompt, String userPrompt) {
        this.llmClient = llmClient;
        this.slotSchemaLoader = slotSchemaLoader;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    @Override
    public StructuredSlotExtractionResult extractSlots(Object userInput, String scenarioCode, String language) {
        return doExtractSlots(userInput, scenarioCode, language, null);
    }

    @Override
    public StructuredSlotExtractionResult extractSlots(
            Object userInput, String scenarioCode, String language, Map<String, Object> dataSchema) {
        return doExtractSlots(userInput, scenarioCode, language, dataSchema);
    }

    private StructuredSlotExtractionResult doExtractSlots(
            Object userInput, String scenarioCode, String language, Map<String, Object> dataSchema) {
        PromptSlotSchema slotSchema = slotSchemaLoader.loadSlotSchema(scenarioCode, language);
        String payload = llmClient
                .structured(
                        toStructuredMessages(buildMessages(userInput, scenarioCode, language, slotSchema, dataSchema)),
                        buildSchema(slotSchema),
                        null,
                        null)
                .content();
        return parseExtractionResult(slotSchema, payload);
    }

    private List<PromptMessage> buildMessages(
            Object userInput,
            String scenarioCode,
            String language,
            PromptSlotSchema slotSchema,
            Map<String, Object> dataSchema) {
        String slotLines;
        try {
            slotLines = SLOT_SERIALIZER.writeValueAsString(slotSchema.slotDefinitions());
        } catch (JsonProcessingException error) {
            throw new A2ATError("Failed to serialize slot definitions.", error);
        }
        String content = userPrompt
                + "\n\n[scenario_code]\n"
                + scenarioCode
                + "\n\n[language]\n"
                + language
                + "\n\n[input]\n"
                + String.valueOf(userInput)
                + "\n\n[slots]\n"
                + slotLines;
        if (dataSchema != null && !dataSchema.isEmpty()) {
            content += "\n\n[data_schema]\n";
            try {
                content += OBJECT_MAPPER.writeValueAsString(dataSchema);
            } catch (JsonProcessingException e) {
                throw new A2ATError("Failed to serialize data schema to JSON.", e);
            }
        }
        return List.of(new PromptMessage("system", systemPrompt), new PromptMessage("user", content));
    }

    private Map<String, Object> buildSchema(PromptSlotSchema slotSchema) {
        List<String> slotNames = slotSchema.slotDefinitions().stream()
                .map(PromptSlotDefinition::name)
                .toList();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("slots", "slot_errors"));
        schema.put("slotNames", slotNames);
        return schema;
    }

    private static List<Map<String, String>> toStructuredMessages(List<PromptMessage> messages) {
        return messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
    }

    private StructuredSlotExtractionResult parseExtractionResult(PromptSlotSchema slotSchema, String payload) {
        Map<String, Object> response = parseObject(payload);
        Map<String, String> normalized = normalizeSlots(response.get("slots"), slotSchema);
        List<StructuredSlotValidationError> slotErrors = normalizeSlotErrors(response.get("slot_errors"));
        return new StructuredSlotExtractionResult(Map.copyOf(normalized), List.copyOf(slotErrors));
    }

    private static Map<String, Object> parseObject(String payload) {
        try {
            Map<String, Object> response =
                    OBJECT_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
            return response == null ? Map.of() : response;
        } catch (JsonProcessingException error) {
            throw new A2ATError("Structured LLM payload must be a JSON object.", error);
        }
    }

    private static Map<String, String> normalizeSlots(Object slotsValue, PromptSlotSchema slotSchema) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Object> rawSlots = slotsValue instanceof Map<?, ?> mapValue ? normalizeMap(mapValue) : Map.of();
        for (PromptSlotDefinition definition : slotSchema.slotDefinitions()) {
            values.put(definition.name(), normalizeSlotValue(rawSlots.get(definition.name())));
        }
        return values;
    }

    private static List<StructuredSlotValidationError> normalizeSlotErrors(Object errorsValue) {
        if (!(errorsValue instanceof List<?> errors)) {
            return List.of();
        }
        List<StructuredSlotValidationError> normalized = new ArrayList<>();
        for (Object errorValue : errors) {
            if (!(errorValue instanceof Map<?, ?> errorMap)) {
                continue;
            }
            Map<String, Object> fields = normalizeMap(errorMap);
            normalized.add(new StructuredSlotValidationError(
                    asString(fields.get("slot_name")), asString(fields.get("code")), asString(fields.get("message"))));
        }
        return normalized;
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
    }

    private static String normalizeSlotValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        return String.valueOf(value);
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : "";
    }
}
