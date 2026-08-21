package net.openan.a2at.sdk.client.prompt.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.extractor.ClientSlotValueExtractor;
import net.openan.a2at.sdk.client.prompt.loader.ClientSlotSchemaLoader;
import net.openan.a2at.sdk.client.prompt.loader.ClientTemplateLoader;
import net.openan.a2at.sdk.client.prompt.recognition.ClientScenarioRecognizer;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.FailedParameter;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.taskrendering.api.TaskPromptRenderer;
import org.junit.jupiter.api.Test;

class DefaultClientPromptGenerationOrchestratorTest {

    private static final ClientSlotSchemaLoader EMPTY_SCHEMA_LOADER =
            (scenarioCode, language) -> new PromptSlotSchema(scenarioCode, List.of());

    @Test
    void generateTaskPromptLoadsTemplateAndRendersExtractedSlotsWhenScenarioIsMatched() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}\nNotes: {additional_notes}");
        FakeSlotValueExtractor slotValueExtractor =
                new FakeSlotValueExtractor(Map.of("site", "Site A", "additional_notes", "critical"));
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) ->
                        new ScenarioRecognitionResult(true, "energy-saving", null),
                List.of(new ScenarioDefinition(
                        "energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                templateLoader,
                slotValueExtractor,
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertTrue(result.success());
        assertEquals("Site: Site A\nNotes: critical", result.promptText());
        assertEquals("Analyze Site A.", orchestrator.lastNormalizedInput());
        assertEquals("energy-saving", templateLoader.lastScenarioCode);
        assertEquals("en-US", templateLoader.lastLanguage);
        assertEquals("Analyze Site A.", slotValueExtractor.lastUserInput);
        assertEquals("energy-saving", slotValueExtractor.lastScenarioCode);
        assertEquals("en-US", slotValueExtractor.lastLanguage);
        assertEquals("Site: {site}\nNotes: {additional_notes}", slotValueExtractor.lastTemplateText);
    }

    @Test
    void generateTaskPromptReturnsFailureWhenScenarioIsNotMatched() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) ->
                        new ScenarioRecognitionResult(false, null, "No scenario matched."),
                List.of(new ScenarioDefinition(
                        "energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> "Scenario: {scenario}\nInput: {input}",
                (userInput, scenarioCode, language, templateText) ->
                        Map.of("scenario", scenarioCode, "input", String.valueOf(userInput)),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("scenario_not_matched", result.failure().code());
        assertEquals("scenario", result.failure().stage());
    }

    @Test
    void generateTaskPromptReturnsFailureWhenTemplateIsMissing() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) ->
                        new ScenarioRecognitionResult(true, "energy-saving", null),
                List.of(new ScenarioDefinition(
                        "energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> {
                    throw new ResourceNotFoundException("Prompt resource file does not exist.", scenarioCode);
                },
                (userInput, scenarioCode, language, templateText) ->
                        Map.of("scenario", scenarioCode, "input", String.valueOf(userInput)),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("template_not_found", result.failure().code());
        assertEquals("generation", result.failure().stage());
    }

    @Test
    void generateTaskPromptReturnsFailureWhenRenderingFails() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) ->
                        new ScenarioRecognitionResult(true, "energy-saving", null),
                List.of(new ScenarioDefinition(
                        "energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> "Scenario: {scenario}\nMissing: {missing_slot}",
                (userInput, scenarioCode, language, templateText) -> Map.of("scenario", scenarioCode),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("render_failed", result.failure().code());
        assertEquals("generation", result.failure().stage());
    }

    @Test
    void generateTaskPromptReturnsPromptResourceLoadErrorWhenScenarioPromptsAreMissingForRequestedLanguage() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) -> {
                    throw new ResourceNotFoundException(
                            "Prompt resource file does not exist.",
                            "prompt_resources/prompts/scenario_recognition/zh-CN/system.md");
                },
                List.of(new ScenarioDefinition(
                        "energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "zh-CN",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> "Scenario: {scenario}\nInput: {input}",
                (userInput, scenarioCode, language, templateText) ->
                        Map.of("scenario", scenarioCode, "input", String.valueOf(userInput)),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("prompt_resource_load_error", result.failure().code());
        assertEquals("generation", result.failure().stage());
    }

private static DefaultClientPromptGenerationOrchestrator newTemplateUriOrchestrator(
            ClientScenarioRecognizer recognizer,
            ClientTemplateLoader templateLoader,
            ClientSlotValueExtractor slotValueExtractor) {
        return new DefaultClientPromptGenerationOrchestrator(
                recognizer,
                List.of(new ScenarioDefinition(
                        "energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                templateLoader,
                slotValueExtractor,
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);
    }

    private static final class RecordingScenarioRecognizer implements ClientScenarioRecognizer {
        private int invocationCount;

        @Override
        public ScenarioRecognitionResult recognize(
                String normalizedInput, List<ScenarioDefinition> scenarios, String systemPrompt, String userPrompt) {
            this.invocationCount++;
            return new ScenarioRecognitionResult(true, "energy-saving", null);
        }
    }

    private static final class CountingFailingTemplateLoader implements ClientTemplateLoader {
        private int loadCount;

        @Override
        public String loadTemplate(String scenarioCode, String language) {
            this.loadCount++;
            throw new ResourceNotFoundException("Prompt resource file does not exist.", scenarioCode);
        }
    }

    private static final class FakeTemplateLoader implements ClientTemplateLoader {
        private final String templateText;
        private String lastScenarioCode;
        private String lastLanguage;

        private FakeTemplateLoader(String templateText) {
            this.templateText = templateText;
        }

        @Override
        public String loadTemplate(String scenarioCode, String language) {
            this.lastScenarioCode = scenarioCode;
            this.lastLanguage = language;
            return templateText;
        }
    }

    private static final class FakeSlotValueExtractor implements ClientSlotValueExtractor {
        private final Map<String, String> slots;
        private Object lastUserInput;
        private String lastScenarioCode;
        private String lastLanguage;
        private String lastTemplateText;

        private FakeSlotValueExtractor(Map<String, String> slots) {
            this.slots = slots;
        }

        @Override
        public Map<String, String> extractSlots(
                Object userInput, String scenarioCode, String language, String templateText) {
            this.lastUserInput = userInput;
            this.lastScenarioCode = scenarioCode;
            this.lastLanguage = language;
            this.lastTemplateText = templateText;
            return slots;
        }
    }

    private static final class FakeSlotValueExtractorWithSchema implements ClientSlotValueExtractor {
        private final Map<String, String> slots;
        private Object lastUserInput;
        private String lastScenarioCode;
        private String lastLanguage;
        private String lastTemplateText;
        private Map<String, Object> lastSchema;

        private FakeSlotValueExtractorWithSchema(Map<String, String> slots) {
            this.slots = slots;
        }

        @Override
        public Map<String, String> extractSlots(
                Object userInput, String scenarioCode, String language, String templateText) {
            this.lastUserInput = userInput;
            this.lastScenarioCode = scenarioCode;
            this.lastLanguage = language;
            this.lastTemplateText = templateText;
            return slots;
        }

        @Override
        public Map<String, String> extractSlotsWithSchema(
                Object userInput, String scenarioCode, String language, String templateText,
                Map<String, Object> dataSchema) {
            this.lastUserInput = userInput;
            this.lastScenarioCode = scenarioCode;
            this.lastLanguage = language;
            this.lastTemplateText = templateText;
            this.lastSchema = dataSchema;
            return slots;
        }
    }

    private static final class FailingExtractSlotsWithSchema implements ClientSlotValueExtractor {
        private final RuntimeException exception;

        private FailingExtractSlotsWithSchema(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public Map<String, String> extractSlots(
                Object userInput, String scenarioCode, String language, String templateText) {
            return Map.of();
        }

        @Override
        public Map<String, String> extractSlotsWithSchema(
                Object userInput, String scenarioCode, String language, String templateText,
                Map<String, Object> dataSchema) {
            throw exception;
        }
    }

    // --- MetadataContent pipeline tests (generateFromTemplateUriWithMetadata / FromText) ---

    @Test
    void generateFromTemplateUriWithMetadataReturnsMetadataContentOnSuccess() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractor slotValueExtractor = new FakeSlotValueExtractor(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent result = orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy-saving");

        assertEquals("Task-T/v1/energy-saving", result.templateUri());
        assertEquals("Site: Site A", result.promptText());
        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, result.extensionUri());
    }

    @Test
    void generateFromTemplateUriWithMetadataScenarioRecognizerIsNotInvoked() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractor(Map.of("site", "Site A")));

        orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy-saving");

        assertEquals(0, recognizer.invocationCount);
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsOnNullTemplateUri() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        CountingFailingTemplateLoader templateLoader = new CountingFailingTemplateLoader();
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractor(Map.of()));

        NullPointerException ex = assertThrows(
                NullPointerException.class, () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", null));
        assertFalse(A2ATError.class.isInstance(ex), "null template URI must stay outside the A2ATError tree");
        assertEquals(0, recognizer.invocationCount);
        assertEquals(0, templateLoader.loadCount);
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsOnBlankOrMalformedTemplateUri() {
        for (String invalidUri : new String[] {"", "   ", "../etc/passwd", "has space", "id;drop"}) {
            RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
            CountingFailingTemplateLoader templateLoader = new CountingFailingTemplateLoader();
            DefaultClientPromptGenerationOrchestrator orchestrator =
                    newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractor(Map.of()));

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", invalidUri));
            assertFalse(A2ATError.class.isInstance(ex), "invalid template URI must stay outside the A2ATError tree");
            assertTrue(
                    ex.getMessage().contains("invalid_template_uri"),
                    "the failure must carry the invalid_template_uri detail but was: " + ex.getMessage());
            assertEquals(0, recognizer.invocationCount);
            assertEquals(0, templateLoader.loadCount);
        }
    }

    @Test
    void generateFromTemplateUriWithMetadataPropagatesResourceNotFoundException() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                (scenarioCode, language) -> {
                    throw new ResourceNotFoundException("Prompt resource file does not exist.", scenarioCode);
                },
                new FakeSlotValueExtractor(Map.of("site", "Site A")));

PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy_saving"));
        assertEquals("template_not_found", ex.getCode());
    }

    @Test
    void generateFromTemplateUriWithMetadataPropagatesRenderException() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}\nMissing: {missing_slot}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")));

PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy_saving"));
        assertEquals("render_failed", ex.getCode());
    }

    @Test
    void generateFromTemplateUriWithMetadataPropagatesLlmRuntimeError() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}"),
                (userInput, scenarioCode, language, templateText) -> {
                    throw new LLMRuntimeError("LLM invocation failed.");
                });

PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy_saving"));
        assertEquals("llm_invocation_failed", ex.getCode());
    }

    // --- MetadataContent pipeline tests (generateFromDataWithSchema) ---

    @Test
    void generateFromDataWithSchemaReturnsMetadataContentOnSuccess() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent result = orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of("site", "string"), "Task-T/v1/energy-saving");

        assertEquals("Task-T/v1/energy-saving", result.templateUri());
        assertEquals("Site: Site A", result.promptText());
        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, result.extensionUri());
    }

    @Test
    void generateFromDataWithSchemaPassesSchemaToExtractor() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        Map<String, Object> schema = Map.of("site", "string", "count", "number");
        orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), schema, "Task-T/v1/energy-saving");

        assertEquals(schema, slotValueExtractor.lastSchema);
        assertEquals("Task-T/v1/energy-saving", slotValueExtractor.lastScenarioCode);
        assertEquals("Site: {site}", slotValueExtractor.lastTemplateText);
    }

    @Test
    void generateFromDataWithSchemaNullSchemaHandledGracefully() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent result = orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), null, "Task-T/v1/energy-saving");

        assertEquals("Site: Site A", result.promptText());
        assertEquals(null, slotValueExtractor.lastSchema);
    }

    @Test
    void generateFromDataWithSchemaEmptySchemaHandledSameAsNull() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent result = orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "Task-T/v1/energy-saving");

        assertEquals("Site: Site A", result.promptText());
        assertEquals(Map.of(), slotValueExtractor.lastSchema);
    }

    @Test
    void generateFromDataWithSchemaThrowsOnInvalidTemplateUri() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        CountingFailingTemplateLoader templateLoader = new CountingFailingTemplateLoader();
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractorWithSchema(Map.of()));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), Map.of(), "has space"));
        assertFalse(A2ATError.class.isInstance(ex), "invalid template URI must stay outside the A2ATError tree");
        assertTrue(
                ex.getMessage().contains("invalid_template_uri"),
                "the failure must carry the invalid_template_uri detail but was: " + ex.getMessage());
        assertEquals(0, recognizer.invocationCount);
        assertEquals(0, templateLoader.loadCount);
    }

    @Test
    void generateFromDataWithSchemaPropagatesResourceNotFoundException() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                (scenarioCode, language) -> {
                    throw new ResourceNotFoundException("Prompt resource file does not exist.", scenarioCode);
                },
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A")));

PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), Map.of(), "Task-T/v1/energy_saving"));
        assertEquals("template_not_found", ex.getCode());
    }

    @Test
    void generateFromDataWithSchemaPropagatesRenderException() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}\nMissing: {missing_slot}"),
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A")));

PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), Map.of(), "Task-T/v1/energy_saving"));
        assertEquals("render_failed", ex.getCode());
    }

    @Test
    void generateFromDataWithSchemaPropagatesLlmRuntimeError() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}"),
                new FailingExtractSlotsWithSchema(new LLMRuntimeError("LLM invocation failed.")));

PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), Map.of(), "Task-T/v1/energy_saving"));
        assertEquals("llm_invocation_failed", ex.getCode());
    }

    @Test
    void generateFromDataWithSchemaPropagatesResourceNotFoundExceptionFromExtractor() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}"),
                new FailingExtractSlotsWithSchema(
                        new ResourceNotFoundException("Slot schema file does not exist.", "energy-saving")));

PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), Map.of(), "Task-T/v1/energy_saving"));
        assertEquals("slot_schema_not_found", ex.getCode());
    }

    // --- Public entry point tests ---

    @Test
    void eachMetadataEntryPointReturnsCorrectMetadataContent() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent taskText = orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy-saving");
        MetadataContent taskData = orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "Task-T/v1/energy-saving");
        MetadataContent authText = orchestrator.generateAuthPromptFromText("Grant access.", "Authorization-T/v1/database_read");
        MetadataContent authData = orchestrator.generateAuthPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "Authorization-T/v1/database_read");
        MetadataContent notifText = orchestrator.generateNotificationPromptFromText("Report finished.", "Task-T/v1/energy-saving");
        MetadataContent notifData = orchestrator.generateNotificationPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "Task-T/v1/energy-saving");

        assertTrue(taskText.promptText().contains("Site A"));
        assertTrue(taskData.promptText().contains("Site A"));
        assertTrue(authText.promptText().contains("Site A"));
        assertTrue(authData.promptText().contains("Site A"));
        assertTrue(notifText.promptText().contains("Site A"));
        assertTrue(notifData.promptText().contains("Site A"));
    }

    @Test
    void eachMetadataEntryPointUsesCorrectExtensionUri() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(), templateLoader, new FakeSlotValueExtractor(Map.of("site", "Site A")));

        MetadataContent taskText = orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy-saving");
        MetadataContent taskData = orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "Task-T/v1/energy-saving");
        MetadataContent authText = orchestrator.generateAuthPromptFromText("Grant access.", "Authorization-T/v1/database_read");
        MetadataContent authData = orchestrator.generateAuthPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "Authorization-T/v1/database_read");
        MetadataContent notifText = orchestrator.generateNotificationPromptFromText("Report finished.", "Task-T/v1/energy-saving");
        MetadataContent notifData = orchestrator.generateNotificationPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "Task-T/v1/energy-saving");

        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, taskText.extensionUri());
        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, taskData.extensionUri());
        assertEquals(ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI, authText.extensionUri());
        assertEquals(ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI, authData.extensionUri());
        assertEquals(ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI, notifText.extensionUri());
        assertEquals(ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI, notifData.extensionUri());
    }

    @Test
    void authorizationMetadataEntryPointsPassAuthorizationTypeAsTemplateIdentifier() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Access: {scope}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("scope", "read"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent nlResult = orchestrator.generateAuthPromptFromText("Grant read access.", "Authorization-T/v1/database_read");

        assertEquals("Authorization-T/v1/database_read", nlResult.templateUri());
        assertEquals("Access: read", nlResult.promptText());
        assertEquals("Authorization-T/v1/database_read", templateLoader.lastScenarioCode);
        assertEquals("Authorization-T/v1/database_read", slotValueExtractor.lastScenarioCode);
        assertEquals("Grant read access.", slotValueExtractor.lastUserInput);

        MetadataContent jsonResult = orchestrator.generateAuthPromptFromDataWithSchema(
                Map.of("scope", "read"), Map.of("scope", "string"), "Authorization-T/v1/database_read");

        assertEquals("Authorization-T/v1/database_read", jsonResult.templateUri());
        assertEquals("Authorization-T/v1/database_read", templateLoader.lastScenarioCode);
        assertEquals(Map.of("scope", "read"), slotValueExtractor.lastUserInput);
        assertEquals(Map.of("scope", "string"), slotValueExtractor.lastSchema);
    }

    // --- slot validation tests ---

    @Test
    void validateRequiredSlotsThrowsSlotValidationErrorWhenRequiredSlotsMissing() {
ClientSlotSchemaLoader schemaLoader = (scenarioCode, language) -> new PromptSlotSchema(
                scenarioCode, List.of(new PromptSlotDefinition("site", true, "string", null, null, null, null, "Site name")));
            DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US", "", "", new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of()),
                new TaskPromptRenderer(),
                schemaLoader);

        PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy_saving"));
        assertEquals("slot_validation_error", ex.getCode());
        List<FailedParameter> failed = ex.failedParameters();
        assertFalse(failed.isEmpty());
        assertEquals("site", failed.get(0).parameterName());
        assertEquals("missing_required", failed.get(0).reason());
    }

    @Test
    void validateRequiredSlotsThrowsSlotValidationErrorWhenMultipleRequiredSlotsMissing() {
        ClientSlotSchemaLoader schemaLoader = (scenarioCode, language) -> new PromptSlotSchema(scenarioCode,
                List.of(
                        new PromptSlotDefinition("site", true, "string", null, null, null, null, "Site name"),
                        new PromptSlotDefinition("target", true, "string", null, null, null, null, "Target")));
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US", "", "", new FakeTemplateLoader("Site: {site}\nTarget: {target}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                schemaLoader);

        PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy_saving"));
        assertEquals("slot_validation_error", ex.getCode());
        assertEquals(1, ex.failedParameters().size());
        assertEquals("target", ex.failedParameters().get(0).parameterName());
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsSlotSchemaNotFoundWhenSchemaLoadingFails() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US", "", "", new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                (scenarioCode, language) -> {
                    throw new A2ATError("Schema file is corrupt.");
                });

        PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy_saving"));
        assertEquals("slot_schema_not_found", ex.getCode());
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsSlotSchemaNotFoundFromExtractor() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US", "", "", new FakeTemplateLoader("Site: {site}"),
                (userInput, scenarioCode, language, templateText) -> {
                    throw new ResourceNotFoundException("Slot schema file does not exist.", scenarioCode);
                },
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy_saving"));
        assertEquals("slot_schema_not_found", ex.getCode());
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsPromptResourceLoadError() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US", "", "", (scenarioCode, language) -> {
                    throw new A2ATError("Template file is corrupt.");
                },
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy_saving"));
        assertEquals("prompt_resource_load_error", ex.getCode());
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsLlmInvocationFailedFromA2ATError() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US", "", "", new FakeTemplateLoader("Site: {site}"),
                (userInput, scenarioCode, language, templateText) -> {
                    throw new A2ATError("Unparseable LLM response.");
                },
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationException ex = assertThrows(PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "Task-T/v1/energy_saving"));
        assertEquals("llm_invocation_failed", ex.getCode());
    }
}
