package net.openan.a2at.sdk.client.prompt.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.client.model.PromptGenerationFailure;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.prompt.analysis.impl.PromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.impl.ScenarioRecognizer;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.taskrendering.TaskPromptRenderer;
import net.openan.a2at.sdk.prompt.taskrendering.exception.TaskPromptRenderException;

/**
 * Minimal runnable client prompt generation orchestrator.
 *
 * @since 2026-06
 */
public final class DefaultClientPromptGenerationOrchestrator implements ClientPromptGenerationOrchestrator {

    private final ScenarioRecognizer scenarioRecognizer;

    private final List<ScenarioDefinition> scenarios;

    private final String language;

    private final String systemPrompt;

    private final String userPrompt;

    private final PromptTemplateTextLoader templateLoader;

    private final PromptSlotValueExtractor slotValueExtractor;

    private final PromptSlotSchemaLoader slotSchemaLoader;

    private final TaskPromptRenderer renderer;

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
     * @param slotSchemaLoader slot schema loader
     */
    public DefaultClientPromptGenerationOrchestrator(
            ScenarioRecognizer scenarioRecognizer,
            List<ScenarioDefinition> scenarios,
            String language,
            String systemPrompt,
            String userPrompt,
            PromptTemplateTextLoader templateLoader,
            PromptSlotValueExtractor slotValueExtractor,
            TaskPromptRenderer renderer,
            PromptSlotSchemaLoader slotSchemaLoader) {
        this.scenarioRecognizer = scenarioRecognizer;
        this.scenarios = scenarios;
        this.language = language;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.templateLoader = templateLoader;
        this.slotValueExtractor = slotValueExtractor;
        this.renderer = renderer;
        this.slotSchemaLoader = slotSchemaLoader;
    }

    @Override
    public PromptGenerationResult generateTaskPrompt(Object userInput) {
        String normalizedInput = String.valueOf(userInput);

        final ScenarioRecognitionResult recognition;
        try {
            recognition = scenarioRecognizer.recognize(normalizedInput, scenarios, systemPrompt, userPrompt);
        } catch (ResourceNotFoundException error) {
            return PromptGenerationResult.failure(new PromptGenerationFailure(
                    A2ATErrorCodes.PROMPT_RESOURCE_LOAD_ERROR, error.getMessage(), "generation"));
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
                    new PromptGenerationFailure(A2ATErrorCodes.TEMPLATE_NOT_FOUND, error.getMessage(), "generation"));
        }

        try {
            StructuredSlotExtractionResult extractionResult =
                    slotValueExtractor.extractSlots(userInput, recognition.scenarioCode(), language);
            Map<String, String> slots = extractionResult.slots();
            String renderedPrompt = renderer.render(templateText, slots);
            return PromptGenerationResult.success(renderedPrompt);
        } catch (TaskPromptRenderException error) {
            return PromptGenerationResult.failure(
                    new PromptGenerationFailure(A2ATErrorCodes.RENDER_FAILED, error.getMessage(), "generation"));
        }
    }

    @Override
    public MetadataContent generateTaskPromptFromText(String text, TemplateUri templateUri) {
        return generateFromTemplateUriWithMetadata(text, templateUri, ExtensionUriConstants.TASK_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateTaskPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri) {
        return generateFromDataWithSchema(data, schema, templateUri, ExtensionUriConstants.TASK_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateAuthPromptFromText(String text, TemplateUri templateUri) {
        return generateFromTemplateUriWithMetadata(
                text, templateUri, ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateAuthPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri) {
        return generateFromDataWithSchema(
                data, schema, templateUri, ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateNotificationPromptFromText(String text, TemplateUri templateUri) {
        return generateFromTemplateUriWithMetadata(
                text, templateUri, ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateNotificationPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri) {
        return generateFromDataWithSchema(
                data, schema, templateUri, ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI);
    }

    private MetadataContent generateFromTemplateUriWithMetadata(
            String userInput, TemplateUri templateUri, String extensionUri) {
        Objects.requireNonNull(userInput, "userInput");
        return generateWithMetadata(templateUri, extensionUri, templateIdentifier -> slotValueExtractor
                .extractSlots(userInput, templateIdentifier, language)
                .slots());
    }

    private MetadataContent generateFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri, String extensionUri) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(schema, "schema");
        if (schema.isEmpty()) {
            throw new IllegalArgumentException(
                    "Data schema must not be empty; it describes the meaning of each input field.");
        }
        return generateWithMetadata(templateUri, extensionUri, templateIdentifier -> slotValueExtractor
                .extractSlots(data, templateIdentifier, language, schema)
                .slots());
    }

    private MetadataContent generateWithMetadata(
            TemplateUri templateUri, String extensionUri, Function<String, Map<String, String>> slotExtractor) {
        Objects.requireNonNull(templateUri, "templateUri");
        String templateIdentifier = templateUri.uri();
        final String templateText;
        try {
            templateText = templateLoader.loadTemplate(templateIdentifier, language);
        } catch (ResourceNotFoundException e) {
            throw new PromptGenerationException(A2ATErrorCodes.TEMPLATE_NOT_FOUND, e.getMessage(), e);
        } catch (A2ATError e) {
            throw new PromptGenerationException(A2ATErrorCodes.PROMPT_RESOURCE_LOAD_ERROR, e.getMessage(), e);
        }
        final Map<String, String> slots;
        try {
            slots = slotExtractor.apply(templateIdentifier);
        } catch (ResourceNotFoundException e) {
            throw new PromptGenerationException(A2ATErrorCodes.SLOT_SCHEMA_NOT_FOUND, e.getMessage(), e);
        } catch (A2ATError e) {
            throw new PromptGenerationException(A2ATErrorCodes.LLM_INVOCATION_FAILED, e.getMessage(), e);
        }
        validateRequiredSlots(slots, templateIdentifier);
        final String renderedPrompt;
        try {
            renderedPrompt = renderer.render(templateText, slots);
        } catch (TaskPromptRenderException e) {
            throw new PromptGenerationException(A2ATErrorCodes.RENDER_FAILED, e.getMessage(), e);
        }
        return new MetadataContent(templateIdentifier, renderedPrompt, extensionUri);
    }

    private void validateRequiredSlots(Map<String, String> slots, String templateIdentifier) {
        final PromptSlotSchema schema;
        try {
            schema = slotSchemaLoader.loadSlotSchema(templateIdentifier, language);
        } catch (A2ATError e) {
            throw new PromptGenerationException(A2ATErrorCodes.SLOT_SCHEMA_NOT_FOUND, e.getMessage(), e);
        }
        if (schema == null) {
            throw new PromptGenerationException(
                    A2ATErrorCodes.SLOT_SCHEMA_NOT_FOUND, "Slot schema not found for template: " + templateIdentifier);
        }
        List<PromptSlotDefinition> defs = schema.slotDefinitions();
        if (defs == null) {
            return;
        }
        Map<String, String> effectiveSlots = slots;
        if (effectiveSlots == null) {
            effectiveSlots = Map.of();
        }
        List<SlotValidationError> failed = new ArrayList<>();
        for (PromptSlotDefinition def : defs) {
            if (def == null) {
                continue;
            }
            if (def.required()) {
                String name = def.name();
                if (name == null) {
                    continue;
                }
                String value = effectiveSlots.get(name);
                if (value == null || value.trim().isEmpty()) {
                    failed.add(new SlotValidationError(name, "missing_required", "Required slot is missing or empty"));
                }
            }
        }
        if (!failed.isEmpty()) {
            throw new PromptGenerationException(
                    A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                    "Required slots are missing or empty: "
                            + failed.stream().map(SlotValidationError::slotName).collect(Collectors.joining(", ")),
                    failed);
        }
    }
}
