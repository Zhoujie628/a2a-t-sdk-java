package net.openan.a2at.sdk.client.prompt.orchestration;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.openan.a2at.sdk.client.model.MetadataContent;
import net.openan.a2at.sdk.client.model.PromptGenerationFailure;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.extractor.ClientSlotValueExtractor;
import net.openan.a2at.sdk.client.prompt.loader.ClientTemplateLoader;
import net.openan.a2at.sdk.client.prompt.recognition.ClientScenarioRecognizer;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.taskrendering.api.TaskPromptRenderer;
import net.openan.a2at.sdk.prompt.taskrendering.exception.TaskPromptRenderException;

/**
 * Minimal runnable client prompt generation orchestrator.
 *
 * @since 2026-06
 */
public final class DefaultClientPromptGenerationOrchestrator implements ClientPromptGenerationOrchestrator {

    private static final Pattern TEMPLATE_IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");

    private final ClientScenarioRecognizer scenarioRecognizer;

    private final List<ScenarioDefinition> scenarios;

    private final String language;

    private final String systemPrompt;

    private final String userPrompt;

    private final ClientTemplateLoader templateLoader;

    private final ClientSlotValueExtractor slotValueExtractor;

    private final TaskPromptRenderer renderer;

    private String lastNormalizedInput;

    /**
     * Creates a client prompt-generation orchestrator with explicit collaborators.
     *
     * @param scenarioRecognizer scenario recognizer
     * @param scenarios supported scenario definitions
     * @param language locale identifier for resource lookup
     * @param systemPrompt system prompt for scenario recognition
     * @param userPrompt user prompt for scenario recognition
     * @param templateLoader template loader
     * @param slotValueExtractor slot value extractor
     * @param renderer task prompt renderer
     */
    public DefaultClientPromptGenerationOrchestrator(
            ClientScenarioRecognizer scenarioRecognizer,
            List<ScenarioDefinition> scenarios,
            String language,
            String systemPrompt,
            String userPrompt,
            ClientTemplateLoader templateLoader,
            ClientSlotValueExtractor slotValueExtractor,
            TaskPromptRenderer renderer) {
        this.scenarioRecognizer = scenarioRecognizer;
        this.scenarios = scenarios;
        this.language = language;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.templateLoader = templateLoader;
        this.slotValueExtractor = slotValueExtractor;
        this.renderer = renderer;
    }

    @Override
    public PromptGenerationResult generateTaskPrompt(Object userInput) {
        String normalizedInput = String.valueOf(userInput);
        this.lastNormalizedInput = normalizedInput;

        final ScenarioRecognitionResult recognition;
        try {
            recognition = scenarioRecognizer.recognize(normalizedInput, scenarios, systemPrompt, userPrompt);
        } catch (ResourceNotFoundException error) {
            return PromptGenerationResult.failure(
                    new PromptGenerationFailure("prompt_resource_load_error", error.getMessage(), "generation"));
        }
        if (!recognition.matched()
                || recognition.scenarioCode() == null
                || recognition.scenarioCode().isBlank()) {
            return PromptGenerationResult.failure(new PromptGenerationFailure(
                    "scenario_not_matched",
                    recognition.errorMessage() == null ? "Scenario recognition failed." : recognition.errorMessage(),
                    "scenario"));
        }

        final String templateText;
        try {
            templateText = templateLoader.loadTemplate(recognition.scenarioCode(), language);
        } catch (ResourceNotFoundException error) {
            return PromptGenerationResult.failure(
                    new PromptGenerationFailure("template_not_found", error.getMessage(), "generation"));
        }

        try {
            Map<String, String> slots =
                    slotValueExtractor.extractSlots(userInput, recognition.scenarioCode(), language, templateText);
            String renderedPrompt = renderer.render(templateText, slots);
            return PromptGenerationResult.success(renderedPrompt);
        } catch (TaskPromptRenderException error) {
            return PromptGenerationResult.failure(
                    new PromptGenerationFailure("render_failed", error.getMessage(), "generation"));
        }
    }

    @Override
    public PromptGenerationResult generateTaskPromptFromNl(String taskInputNl, String promptTemplateUri) {
        return generateFromTemplateUri(taskInputNl, promptTemplateUri);
    }

    @Override
    public PromptGenerationResult generateTaskPromptFromJsonData(
            Map<String, Object> taskInputJsonData, String promptTemplateUri) {
        return generateFromTemplateUri(taskInputJsonData, promptTemplateUri);
    }

    @Override
    public PromptGenerationResult generateAuthorizationPromptFromNl(String inputNl, String authorizationType) {
        return generateFromTemplateUri(inputNl, authorizationType);
    }

    @Override
    public PromptGenerationResult generateAuthorizationPromptFromJsonData(
            Map<String, Object> inputJsonData, String authorizationType) {
        return generateFromTemplateUri(inputJsonData, authorizationType);
    }

    @Override
    public PromptGenerationResult generateNotificationPromptFromNl(String inputNl, String promptTemplateUri) {
        return generateFromTemplateUri(inputNl, promptTemplateUri);
    }

    @Override
    public PromptGenerationResult generateNotificationPromptFromJsonData(
            Map<String, Object> inputJsonData, String promptTemplateUri) {
        return generateFromTemplateUri(inputJsonData, promptTemplateUri);
    }

    private PromptGenerationResult generateFromTemplateUri(Object userInput, String templateIdentifier) {
        if (templateIdentifier == null
                || templateIdentifier.isBlank()
                || !TEMPLATE_IDENTIFIER_PATTERN.matcher(templateIdentifier).matches()) {
            return PromptGenerationResult.failure(new PromptGenerationFailure(
                    "invalid_template_uri",
                    "Template URI is null, blank, or contains invalid characters.",
                    "generation"));
        }

        final String templateText;
        try {
            templateText = templateLoader.loadTemplate(templateIdentifier, language);
        } catch (ResourceNotFoundException error) {
            return PromptGenerationResult.failure(
                    new PromptGenerationFailure("template_not_found", error.getMessage(), "generation"));
        }

        try {
            Map<String, String> slots =
                    slotValueExtractor.extractSlots(userInput, templateIdentifier, language, templateText);
            String renderedPrompt = renderer.render(templateText, slots);
            return PromptGenerationResult.success(renderedPrompt);
        } catch (TaskPromptRenderException error) {
            return PromptGenerationResult.failure(
                    new PromptGenerationFailure("render_failed", error.getMessage(), "generation"));
        }
    }

    @Override
    public MetadataContent generateTaskPromptFromText(String text, String templateUri) {
        return generateFromTemplateUriWithMetadata(text, templateUri, ExtensionUriConstants.TASK_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateTaskPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, String templateUri) {
        return generateFromDataWithSchema(data, schema, templateUri, ExtensionUriConstants.TASK_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateAuthPromptFromText(String text, String authorizationType) {
        return generateFromTemplateUriWithMetadata(
                text, authorizationType, ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateAuthPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, String authorizationType) {
        return generateFromDataWithSchema(
                data, schema, authorizationType, ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateNotificationPromptFromText(String text, String templateUri) {
        return generateFromTemplateUriWithMetadata(
                text, templateUri, ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateNotificationPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, String templateUri) {
        return generateFromDataWithSchema(data, schema, templateUri, ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI);
    }

    private MetadataContent generateFromTemplateUriWithMetadata(
            String userInput, String templateIdentifier, String extensionUri) {
        if (templateIdentifier == null
                || templateIdentifier.isBlank()
                || !TEMPLATE_IDENTIFIER_PATTERN.matcher(templateIdentifier).matches()) {
            throw new IllegalArgumentException("Template URI is null, blank, or contains invalid characters.");
        }
        String templateText = templateLoader.loadTemplate(templateIdentifier, language);
        Map<String, String> slots =
                slotValueExtractor.extractSlots(userInput, templateIdentifier, language, templateText);
        String renderedPrompt = renderer.render(templateText, slots);
        return new MetadataContent(templateIdentifier, renderedPrompt, extensionUri);
    }

    private MetadataContent generateFromDataWithSchema(
            Map<String, Object> data,
            Map<String, Object> schema,
            String templateIdentifier,
            String extensionUri) {
        if (templateIdentifier == null
                || templateIdentifier.isBlank()
                || !TEMPLATE_IDENTIFIER_PATTERN.matcher(templateIdentifier).matches()) {
            throw new IllegalArgumentException("Template URI is null, blank, or contains invalid characters.");
        }
        String templateText = templateLoader.loadTemplate(templateIdentifier, language);
        Map<String, String> slots =
                slotValueExtractor.extractSlotsWithSchema(data, templateIdentifier, language, templateText, schema);
        String renderedPrompt = renderer.render(templateText, slots);
        return new MetadataContent(templateIdentifier, renderedPrompt, extensionUri);
    }

    String lastNormalizedInput() {
        return lastNormalizedInput;
    }
}
