package net.openan.a2at.sdk.client.prompt.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.model.MetadataContent;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.extractor.ClientSlotValueExtractor;
import net.openan.a2at.sdk.client.prompt.loader.ClientTemplateLoader;
import net.openan.a2at.sdk.client.prompt.recognition.ClientScenarioRecognizer;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.taskrendering.api.TaskPromptRenderer;
import org.junit.jupiter.api.Test;

class DefaultClientPromptGenerationOrchestratorTest {

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
                new TaskPromptRenderer());

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
                new TaskPromptRenderer());

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
                new TaskPromptRenderer());

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
                new TaskPromptRenderer());

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
                new TaskPromptRenderer());

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("prompt_resource_load_error", result.failure().code());
        assertEquals("generation", result.failure().stage());
    }

    @Test
    void eachTemplateUriEntryPointGeneratesPromptWithoutScenarioRecognition() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractor slotValueExtractor = new FakeSlotValueExtractor(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(recognizer, templateLoader, slotValueExtractor);

        PromptGenerationResult taskNl = orchestrator.generateTaskPromptFromNl("Analyze Site A.", "energy_saving");
        PromptGenerationResult taskJson =
                orchestrator.generateTaskPromptFromJsonData(Map.of("site", "Site A"), "energy_saving");
        PromptGenerationResult authorizationNl =
                orchestrator.generateAuthorizationPromptFromNl("Grant access.", "database_read");
        PromptGenerationResult authorizationJson =
                orchestrator.generateAuthorizationPromptFromJsonData(Map.of("site", "Site A"), "database_read");
        PromptGenerationResult notificationNl =
                orchestrator.generateNotificationPromptFromNl("Report finished.", "energy_saving");
        PromptGenerationResult notificationJson =
                orchestrator.generateNotificationPromptFromJsonData(Map.of("site", "Site A"), "energy_saving");

        assertTrue(taskNl.success());
        assertTrue(taskJson.success());
        assertTrue(authorizationNl.success());
        assertTrue(authorizationJson.success());
        assertTrue(notificationNl.success());
        assertTrue(notificationJson.success());
        assertEquals("Site: Site A", taskNl.promptText());
        assertEquals("Site: Site A", authorizationJson.promptText());
        assertEquals("Site: Site A", notificationNl.promptText());
        assertEquals(0, recognizer.invocationCount);
        assertEquals("en-US", templateLoader.lastLanguage);
        assertEquals("en-US", slotValueExtractor.lastLanguage);
        assertEquals("Site: {site}", slotValueExtractor.lastTemplateText);
    }

    @Test
    void authorizationEntryPointsUseAuthorizationTypeAsTemplateIdentifier() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Access: {scope}");
        FakeSlotValueExtractor slotValueExtractor = new FakeSlotValueExtractor(Map.of("scope", "read"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        PromptGenerationResult nlResult =
                orchestrator.generateAuthorizationPromptFromNl("Grant read access.", "database_read");

        assertTrue(nlResult.success());
        assertEquals("database_read", templateLoader.lastScenarioCode);
        assertEquals("database_read", slotValueExtractor.lastScenarioCode);
        assertEquals("Grant read access.", slotValueExtractor.lastUserInput);

        PromptGenerationResult jsonResult =
                orchestrator.generateAuthorizationPromptFromJsonData(Map.of("scope", "read"), "database_read");

        assertTrue(jsonResult.success());
        assertEquals("database_read", templateLoader.lastScenarioCode);
        assertEquals(Map.of("scope", "read"), slotValueExtractor.lastUserInput);
    }

    @Test
    void templateUriGenerationReturnsInvalidTemplateUriWhenIdentifierIsNullOrBlankOrPatternViolating() {
        for (String invalidUri :
                new String[] {null, "", "   ", "templates/energy_saving", "../etc/passwd", "has space", "id;drop"}) {
            RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
            CountingFailingTemplateLoader templateLoader = new CountingFailingTemplateLoader();
            DefaultClientPromptGenerationOrchestrator orchestrator =
                    newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractor(Map.of()));

            PromptGenerationResult result =
                    orchestrator.generateTaskPromptFromJsonData(Map.of("site", "Site A"), invalidUri);

            assertFalse(result.success());
            assertNotNull(result.failure());
            assertEquals("invalid_template_uri", result.failure().code());
            assertEquals("generation", result.failure().stage());
            assertEquals(0, recognizer.invocationCount);
            assertEquals(0, templateLoader.loadCount);
        }
    }

    @Test
    void templateUriGenerationReturnsTemplateNotFoundWhenTemplateIsMissing() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                (scenarioCode, language) -> {
                    throw new ResourceNotFoundException("Prompt resource file does not exist.", scenarioCode);
                },
                new FakeSlotValueExtractor(Map.of("site", "Site A")));

        PromptGenerationResult result = orchestrator.generateTaskPromptFromNl("Analyze Site A.", "energy_saving");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("template_not_found", result.failure().code());
        assertEquals("generation", result.failure().stage());
    }

    @Test
    void templateUriGenerationReturnsRenderFailedWhenRenderingFails() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}\nMissing: {missing_slot}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")));

        PromptGenerationResult result =
                orchestrator.generateNotificationPromptFromJsonData(Map.of("site", "Site A"), "energy_saving");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("render_failed", result.failure().code());
        assertEquals("generation", result.failure().stage());
    }

    @Test
    void templateUriGenerationProcessesNullAndBlankUserInputWithoutAdditionalValidation() {
        for (String userInput : new String[] {null, "", "   "}) {
            FakeSlotValueExtractor slotValueExtractor = new FakeSlotValueExtractor(Map.of("site", "Site A"));
            DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                    new RecordingScenarioRecognizer(), new FakeTemplateLoader("Site: {site}"), slotValueExtractor);

            PromptGenerationResult result = orchestrator.generateTaskPromptFromNl(userInput, "energy_saving");

            assertTrue(result.success());
            assertEquals("Site: Site A", result.promptText());
            assertEquals(userInput, slotValueExtractor.lastUserInput);
        }
    }

    private static DefaultClientPromptGenerationOrchestrator newTemplateUriOrchestrator(
            ClientScenarioRecognizer recognizer,
            ClientTemplateLoader templateLoader,
            ClientSlotValueExtractor slotValueExtractor) {
        return new DefaultClientPromptGenerationOrchestrator(
                recognizer,
                List.of(new ScenarioDefinition(
                        "energy_saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                templateLoader,
                slotValueExtractor,
                new TaskPromptRenderer());
    }

    private static final class RecordingScenarioRecognizer implements ClientScenarioRecognizer {
        private int invocationCount;

        @Override
        public ScenarioRecognitionResult recognize(
                String normalizedInput, List<ScenarioDefinition> scenarios, String systemPrompt, String userPrompt) {
            this.invocationCount++;
            return new ScenarioRecognitionResult(true, "energy_saving", null);
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

        MetadataContent result = orchestrator.generateTaskPromptFromText("Analyze Site A.", "energy_saving");

        assertEquals("energy_saving", result.templateUri());
        assertEquals("Site: Site A", result.promptText());
        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, result.extensionUri());
    }

    @Test
    void generateFromTemplateUriWithMetadataScenarioRecognizerIsNotInvoked() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractor(Map.of("site", "Site A")));

        orchestrator.generateTaskPromptFromText("Analyze Site A.", "energy_saving");

        assertEquals(0, recognizer.invocationCount);
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsOnInvalidTemplateUri() {
        for (String invalidUri : new String[] {null, "", "   ", "templates/energy_saving", "../etc/passwd", "has space", "id;drop"}) {
            RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
            CountingFailingTemplateLoader templateLoader = new CountingFailingTemplateLoader();
            DefaultClientPromptGenerationOrchestrator orchestrator =
                    newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractor(Map.of()));

            assertThrows(IllegalArgumentException.class,
                    () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", invalidUri));
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

        assertThrows(ResourceNotFoundException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "energy_saving"));
    }

    @Test
    void generateFromTemplateUriWithMetadataPropagatesRenderException() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}\nMissing: {missing_slot}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")));

        assertThrows(net.openan.a2at.sdk.prompt.taskrendering.exception.TaskPromptRenderException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "energy_saving"));
    }

    @Test
    void generateFromTemplateUriWithMetadataPropagatesLlmRuntimeError() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}"),
                (userInput, scenarioCode, language, templateText) -> {
                    throw new LLMRuntimeError("LLM invocation failed.");
                });

        assertThrows(LLMRuntimeError.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", "energy_saving"));
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
                Map.of("site", "Site A"), Map.of("site", "string"), "energy_saving");

        assertEquals("energy_saving", result.templateUri());
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
        orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), schema, "energy_saving");

        assertEquals(schema, slotValueExtractor.lastSchema);
        assertEquals("energy_saving", slotValueExtractor.lastScenarioCode);
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
                Map.of("site", "Site A"), null, "energy_saving");

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
                Map.of("site", "Site A"), Map.of(), "energy_saving");

        assertEquals("Site: Site A", result.promptText());
        assertEquals(Map.of(), slotValueExtractor.lastSchema);
    }

    @Test
    void generateFromDataWithSchemaThrowsOnInvalidTemplateUri() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        CountingFailingTemplateLoader templateLoader = new CountingFailingTemplateLoader();
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractorWithSchema(Map.of()));

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), Map.of(), "has space"));
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

        assertThrows(ResourceNotFoundException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), Map.of(), "energy_saving"));
    }

    @Test
    void generateFromDataWithSchemaPropagatesRenderException() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}\nMissing: {missing_slot}"),
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A")));

        assertThrows(net.openan.a2at.sdk.prompt.taskrendering.exception.TaskPromptRenderException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), Map.of(), "energy_saving"));
    }

    @Test
    void generateFromDataWithSchemaPropagatesLlmRuntimeError() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}"),
                new FailingExtractSlotsWithSchema(new LLMRuntimeError("LLM invocation failed.")));

        assertThrows(LLMRuntimeError.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), Map.of(), "energy_saving"));
    }

    @Test
    void generateFromDataWithSchemaPropagatesResourceNotFoundExceptionFromExtractor() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}"),
                new FailingExtractSlotsWithSchema(
                        new ResourceNotFoundException("Slot schema file does not exist.", "energy_saving")));

        assertThrows(ResourceNotFoundException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(Map.of("site", "Site A"), Map.of(), "energy_saving"));
    }

    // --- Public entry point tests ---

    @Test
    void eachMetadataEntryPointReturnsCorrectMetadataContent() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent taskText = orchestrator.generateTaskPromptFromText("Analyze Site A.", "energy_saving");
        MetadataContent taskData = orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "energy_saving");
        MetadataContent authText = orchestrator.generateAuthPromptFromText("Grant access.", "database_read");
        MetadataContent authData = orchestrator.generateAuthPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "database_read");
        MetadataContent notifText = orchestrator.generateNotificationPromptFromText("Report finished.", "energy_saving");
        MetadataContent notifData = orchestrator.generateNotificationPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "energy_saving");

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

        MetadataContent taskText = orchestrator.generateTaskPromptFromText("Analyze Site A.", "energy_saving");
        MetadataContent taskData = orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "energy_saving");
        MetadataContent authText = orchestrator.generateAuthPromptFromText("Grant access.", "database_read");
        MetadataContent authData = orchestrator.generateAuthPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "database_read");
        MetadataContent notifText = orchestrator.generateNotificationPromptFromText("Report finished.", "energy_saving");
        MetadataContent notifData = orchestrator.generateNotificationPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of(), "energy_saving");

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

        MetadataContent nlResult = orchestrator.generateAuthPromptFromText("Grant read access.", "database_read");

        assertEquals("database_read", nlResult.templateUri());
        assertEquals("Access: read", nlResult.promptText());
        assertEquals("database_read", templateLoader.lastScenarioCode);
        assertEquals("database_read", slotValueExtractor.lastScenarioCode);
        assertEquals("Grant read access.", slotValueExtractor.lastUserInput);

        MetadataContent jsonResult = orchestrator.generateAuthPromptFromDataWithSchema(
                Map.of("scope", "read"), Map.of("scope", "string"), "database_read");

        assertEquals("database_read", jsonResult.templateUri());
        assertEquals("database_read", templateLoader.lastScenarioCode);
        assertEquals(Map.of("scope", "read"), slotValueExtractor.lastUserInput);
        assertEquals(Map.of("scope", "string"), slotValueExtractor.lastSchema);
    }
}
