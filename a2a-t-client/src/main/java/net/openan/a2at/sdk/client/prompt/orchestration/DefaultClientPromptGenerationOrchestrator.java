package net.openan.a2at.sdk.client.prompt.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.client.model.PromptGenerationFailure;
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
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.taskrendering.api.TaskPromptRenderer;
import net.openan.a2at.sdk.prompt.taskrendering.exception.TaskPromptRenderException;

/**
 * Minimal runnable client prompt generation orchestrator.
 *
 * @since 2026-06
 */
public final class DefaultClientPromptGenerationOrchestrator implements ClientPromptGenerationOrchestrator {

    private static final Pattern TEMPLATE_IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*");

    private final ClientScenarioRecognizer scenarioRecognizer;

    private final List<ScenarioDefinition> scenarios;

    private final String language;

    private final String systemPrompt;

    private final String userPrompt;

    private final ClientTemplateLoader templateLoader;

    private final ClientSlotValueExtractor slotValueExtractor;

    private final ClientSlotSchemaLoader slotSchemaLoader;

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
     * @param slotSchemaLoader slot schema loader
     */
    public DefaultClientPromptGenerationOrchestrator(
            ClientScenarioRecognizer scenarioRecognizer,
            List<ScenarioDefinition> scenarios,
            String language,
            String systemPrompt,
            String userPrompt,
            ClientTemplateLoader templateLoader,
            ClientSlotValueExtractor slotValueExtractor,
            TaskPromptRenderer renderer,
            ClientSlotSchemaLoader slotSchemaLoader) {
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
        requireValidTemplateIdentifier(templateIdentifier);
        final String templateText;
        try {
            templateText = templateLoader.loadTemplate(templateIdentifier, language);
        } catch (ResourceNotFoundException e) {
            throw new PromptGenerationException("template_not_found", e.getMessage(), e);
        } catch (A2ATError e) {
            throw new PromptGenerationException("prompt_resource_load_error", e.getMessage(), e);
        }
        final Map<String, String> slots;
        try {
            slots = slotValueExtractor.extractSlots(userInput, templateIdentifier, language, templateText);
        } catch (ResourceNotFoundException e) {
            throw new PromptGenerationException("slot_schema_not_found", e.getMessage(), e);
        } catch (A2ATError e) {
            throw new PromptGenerationException("llm_invocation_failed", e.getMessage(), e);
        }
        validateRequiredSlots(slots, templateIdentifier);
        final String renderedPrompt;
        try {
            renderedPrompt = renderer.render(templateText, slots);
        } catch (TaskPromptRenderException e) {
            throw new PromptGenerationException("render_failed", e.getMessage(), e);
        }
        return new MetadataContent(templateIdentifier, renderedPrompt, extensionUri);
    }

    private MetadataContent generateFromDataWithSchema(
            Map<String, Object> data,
            Map<String, Object> schema,
            String templateIdentifier,
            String extensionUri) {
        requireValidTemplateIdentifier(templateIdentifier);
        final String templateText;
        try {
            templateText = templateLoader.loadTemplate(templateIdentifier, language);
        } catch (ResourceNotFoundException e) {
            throw new PromptGenerationException("template_not_found", e.getMessage(), e);
        } catch (A2ATError e) {
            throw new PromptGenerationException("prompt_resource_load_error", e.getMessage(), e);
        }
        final Map<String, String> slots;
        try {
            slots = slotValueExtractor.extractSlotsWithSchema(
                    data, templateIdentifier, language, templateText, schema);
        } catch (ResourceNotFoundException e) {
            throw new PromptGenerationException("slot_schema_not_found", e.getMessage(), e);
        } catch (A2ATError e) {
            throw new PromptGenerationException("llm_invocation_failed", e.getMessage(), e);
        }
        validateRequiredSlots(slots, templateIdentifier);
        final String renderedPrompt;
        try {
            renderedPrompt = renderer.render(templateText, slots);
        } catch (TaskPromptRenderException e) {
            throw new PromptGenerationException("render_failed", e.getMessage(), e);
        }
        return new MetadataContent(templateIdentifier, renderedPrompt, extensionUri);
    }

    /**
     * Enforces the caller contract of the template identifier: a null identifier is a {@link NullPointerException},
     * a blank or malformed identifier is an {@link IllegalArgumentException}. Both stay outside the {@link A2ATError}
     * tree because they are programming errors of the caller.
     */
    private static void requireValidTemplateIdentifier(String templateIdentifier) {
        Objects.requireNonNull(templateIdentifier, "templateIdentifier");
        if (templateIdentifier.isBlank() || !TEMPLATE_IDENTIFIER_PATTERN.matcher(templateIdentifier).matches()) {
            throw new IllegalArgumentException(
                    "Template URI is blank or contains invalid characters (invalid_template_uri): "
                            + templateIdentifier);
        }
    }

    private void validateRequiredSlots(Map<String, String> slots, String templateIdentifier) {
        final PromptSlotSchema schema;
        try {
            schema = slotSchemaLoader.loadSlotSchema(templateIdentifier, language);
        } catch (A2ATError e) {
            throw new PromptGenerationException("slot_schema_not_found", e.getMessage(), e);
        }
        if (schema == null) {
            throw new PromptGenerationException(
                    "slot_schema_not_found", "Slot schema not found for template: " + templateIdentifier);
        }
        List<PromptSlotDefinition> defs = schema.slotDefinitions();
        if (defs == null) {
            return;
        }
        if (slots == null) {
            slots = Map.of();
        }
        List<FailedParameter> failed = new ArrayList<>();
        for (PromptSlotDefinition def : defs) {
            if (def == null) {
                continue;
            }
            if (def.required()) {
                String name = def.name();
                if (name == null) {
                    continue;
                }
                String value = slots.get(name);
                if (value == null || value.trim().isEmpty()) {
                    failed.add(new FailedParameter(name, "missing_required"));
                }
            }
        }
        if (!failed.isEmpty()) {
            throw new PromptGenerationException("slot_validation_error",
                    "Required slots are missing or empty: " + failed.stream()
                            .map(FailedParameter::parameterName).collect(Collectors.joining(", ")),
                    failed);
        }
    }

    String lastNormalizedInput() {
        return lastNormalizedInput;
    }
}