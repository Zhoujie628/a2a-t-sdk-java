package net.openan.a2at.sdk.prompt.analysis.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import org.junit.jupiter.api.Test;

class DefaultStructuredPromptSlotValueExtractorTest {

    @Test
    void extractSlotsParsesFormattedStructuredJsonPayload() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A",
                    "additional_notes": null,
                    "limit": "5",
                    "severity": "high"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(
                                new PromptSlotDefinition("site", true, "string", null, null, null, null, null, null),
                                new PromptSlotDefinition(
                                        "additional_notes", false, "string", null, null, null, null, null, null),
                                new PromptSlotDefinition(
                                        "limit", false, "integer", null, 1.0d, 10.0d, null, null, null),
                                new PromptSlotDefinition(
                                        "severity",
                                        false,
                                        "string",
                                        null,
                                        null,
                                        null,
                                        List.of("low", "medium", "high"),
                                        null,
                                        null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        StructuredSlotExtractionResult result =
                extractor.extractSlots("Analyze Site A with critical severity.", "energy-saving", "en-US");

        assertEquals(
                Map.of(
                        "site", "Site A",
                        "additional_notes", "",
                        "limit", "5",
                        "severity", "high"),
                result.slots());
        assertEquals(List.of(), result.slotErrors());
    }

    @Test
    void extractSlotsPreservesSlotTextContainingClosingBrace() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A",
                    "additional_notes": "Need } fallback"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(
                                new PromptSlotDefinition("site", true, "string", null, null, null, null, null, null),
                                new PromptSlotDefinition(
                                        "additional_notes", false, "string", null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        StructuredSlotExtractionResult result =
                extractor.extractSlots("Analyze Site A with fallback note.", "energy-saving", "en-US");

        assertEquals(Map.of("site", "Site A", "additional_notes", "Need } fallback"), result.slots());
        assertEquals(List.of(), result.slotErrors());
    }

    @Test
    void should_injectDataSchemaSection_When_schemaIsProvided() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition("site", true, "string", null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        Map<String, Object> dataSchema = Map.of(
                "site", "The target site name",
                "severity", "The severity level");

        extractor.extractSlots("Analyze Site A.", "energy-saving", "en-US", dataSchema);

        String userMessage = llmClient.lastUserContent();
        assertTrue(userMessage.contains("[data_schema]"));
        assertTrue(userMessage.contains("- site: The target site name"));
        assertTrue(userMessage.contains("- severity: The severity level"));
    }

    @Test
    void should_notIncludeDataSchemaSection_When_schemaIsNull() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition("site", true, "string", null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        extractor.extractSlots("Analyze Site A.", "energy-saving", "en-US", null);

        String userMessage = llmClient.lastUserContent();
        assertFalse(userMessage.contains("[data_schema]"));
    }

    @Test
    void should_notIncludeDataSchemaSection_When_schemaIsEmpty() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition("site", true, "string", null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        extractor.extractSlots("Analyze Site A.", "energy-saving", "en-US", Map.of());

        String userMessage = llmClient.lastUserContent();
        assertFalse(userMessage.contains("[data_schema]"));
    }

    @Test
    void buildMessagesIncludesFullSlotDescriptions() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                "site",
                                true,
                                "string",
                                null,
                                null,
                                null,
                                List.of("Site A", "Site B"),
                                "The target site name",
                                "The site must be a physical location"))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        extractor.extractSlots("Analyze Site A.", "energy-saving", "en-US");

        String userMessage = llmClient.lastUserContent();
        assertTrue(userMessage.contains("[slots]"));
        assertTrue(userMessage.contains("\"name\""));
        assertTrue(userMessage.contains("\"site\""));
        assertTrue(userMessage.contains("\"required\""));
        assertTrue(userMessage.contains("\"type\""));
        assertTrue(userMessage.contains("\"string\""));
        assertTrue(userMessage.contains("\"description\""));
        assertTrue(userMessage.contains("\"The target site name\""));
        assertTrue(userMessage.contains("\"enum\""));
        assertTrue(userMessage.contains("\"Site A\""));
        assertTrue(userMessage.contains("\"x-a2at-value-constraint\""));
        assertTrue(userMessage.contains("\"The site must be a physical location\""));
        assertTrue(userMessage.contains("["));
    }

    @Test
    void defaultExtractSlotsWithSchemaDelegatesToThreeArg() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition("site", true, "string", null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        PromptSlotValueExtractor lambdaExtractor = (input, code, lang) -> extractor.extractSlots(input, code, lang);

        StructuredSlotExtractionResult result =
                lambdaExtractor.extractSlots("Analyze Site A.", "energy-saving", "en-US", Map.of("site", "desc"));

        String userMessage = llmClient.lastUserContent();
        assertFalse(userMessage.contains("[data_schema]"));
        assertEquals(Map.of("site", "Site A"), result.slots());
    }

    private static final class RecordingClient implements LLMClient {

        private final String payload;

        private List<Map<String, String>> lastMessages;

        private RecordingClient(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            this.lastMessages = messages;
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }

        String lastUserContent() {
            for (Map<String, String> message : lastMessages) {
                if ("user".equals(message.get("role"))) {
                    return message.get("content");
                }
            }
            return "";
        }
    }
}
