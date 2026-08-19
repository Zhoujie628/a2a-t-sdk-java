package net.openan.a2at.sdk.client.prompt.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.extractor.ClientSlotValueExtractor;
import net.openan.a2at.sdk.client.prompt.loader.ClientTemplateLoader;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
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
}
