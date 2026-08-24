package net.openan.a2at.sdk.negotiation.testdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.analysis.impl.DefaultStructuredPromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;
import net.openan.a2at.sdk.prompt.resources.loader.PromptResourceAccess;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.taskrendering.TaskPromptRenderer;
import net.openan.a2at.sdk.prompt.taskrendering.exception.TaskPromptRenderException;
import net.openan.a2at.sdk.prompt.validation.DefaultContentValidator;

/**
 * Test-domain assembly of the three closed-loop task APIs (Q21): everything except the LLM client is production
 * wiring, mirroring the two facade builders without touching production code.
 *
 * <ul>
 *   <li>{@code validateTaskPromptAndDataFilling} mirrors {@code DefaultA2ATServerBuilder.buildTaskContentValidator}
 *       exactly: the same {@link DefaultContentValidator} constructor the server builder calls, with the Task-T
 *       extension name, the language, the LLM retry limit, the LLM client and the classpath
 *       {@link PromptResourceAccess};</li>
 *   <li>{@code generateTaskPromptFromText} and {@code generateTaskPromptFromDataWithSchema} mirror the internals of
 *       {@code DefaultClientPromptGenerationOrchestrator}: the same production services the client builder wires
 *       ({@link PromptTemplateTextLoader}, {@link DefaultStructuredPromptSlotValueExtractor} — the exact class the
 *       client's {@code DefaultStructuredClientSlotValueExtractor} delegates to —, {@link PromptSlotSchemaLoader} and
 *       {@link TaskPromptRenderer}), with the same error-code mapping and required-slot validation the client
 *       orchestrator applies.</li>
 * </ul>
 *
 * <p>The negotiation module cannot see the client and server modules (they depend on it, not the other way round), so
 * the orchestrator logic is mirrored here line by line; a renamed facade method fails the {@link NegotiationApi}
 * dispatch compilation, and a behavior change of the mirrored pipeline shows up as a corpus red.
 *
 * @since 2026-08
 */
final class TaskApiAssembler {

    private static final String SLOT_EXTRACTION_PROMPT = "slot_extraction";

    private final String language;

    private final PromptTemplateTextLoader templateLoader;

    private final PromptSlotSchemaLoader slotSchemaLoader;

    private final DefaultStructuredPromptSlotValueExtractor slotValueExtractor;

    private final TaskPromptRenderer renderer;

    private final ContentValidator taskValidator;

    /**
     * Assembles the task API wiring for one language.
     *
     * @param language language of the generated and validated task prompts, such as {@code zh-CN}
     * @param maxAttempts retry limit of the LLM steps, mirroring the builder default of 3
     * @param llmClient scripted LLM client injected at the same seam the facade builders inject their real client
     */
    TaskApiAssembler(String language, int maxAttempts, LLMClient llmClient) {
        this.language = language;
        PromptResourceAccess resources = PromptResourceAccess.create(
                new PromptRuntimeConfig(language, PromptResourceAccess.CLASSPATH_SOURCE_TYPE, null));
        this.templateLoader = resources.templateLoader();
        this.slotSchemaLoader = resources.slotSchemaLoader();
        this.slotValueExtractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                slotSchemaLoader,
                resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "system.md"),
                resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "user.md"));
        this.renderer = new TaskPromptRenderer();
        this.taskValidator = new DefaultContentValidator(
                StandardTemplates.TASK_EXTENSION_NAME, language, maxAttempts, llmClient, resources);
    }

    /**
     * Generates a task prompt with metadata from natural-language input, mirroring
     * {@code A2ATClient.generateTaskPromptFromText}.
     *
     * @param text natural-language task input
     * @param templateUri template URI identifying the target task template
     * @return metadata content carrying the resolved template URI, rendered prompt text and Task-T extension URI
     */
    MetadataContent generateTaskPromptFromText(String text, TemplateUri templateUri) {
        String templateIdentifier = templateUri.uri();
        String templateText = loadTemplate(templateIdentifier);
        Map<String, String> slots = extractSlots(text, templateIdentifier, templateText);
        validateRequiredSlots(slots, templateIdentifier);
        return new MetadataContent(
                templateIdentifier, render(templateText, slots), ExtensionUriConstants.TASK_T_EXTENSION_URI);
    }

    /**
     * Generates a task prompt with metadata from structured input and a data schema, mirroring
     * {@code A2ATClient.generateTaskPromptFromDataWithSchema}.
     *
     * @param data structured task input as a string-to-object map
     * @param schema data schema map describing the meaning of each input field
     * @param templateUri template URI identifying the target task template
     * @return metadata content carrying the resolved template URI, rendered prompt text and Task-T extension URI
     */
    MetadataContent generateTaskPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri) {
        if (schema == null || schema.isEmpty()) {
            throw new IllegalArgumentException(
                    "Data schema must not be empty; it describes the meaning of each input field.");
        }
        String templateIdentifier = templateUri.uri();
        String templateText = loadTemplate(templateIdentifier);
        StructuredSlotExtractionResult result =
                slotValueExtractor.extractSlots(data, templateIdentifier, language, schema);
        Map<String, String> slots = result.slots();
        validateRequiredSlots(slots, templateIdentifier);
        return new MetadataContent(
                templateIdentifier, render(templateText, slots), ExtensionUriConstants.TASK_T_EXTENSION_URI);
    }

    /**
     * Validates a task prompt and extracts its filled parameters, mirroring
     * {@code A2ATServer.validateTaskPromptAndDataFilling}.
     *
     * <p>A schema slot the prompt misses surfaces as a null-valued entry of the returned parameter data — that set of
     * null-valued keys is the missing-parameter set the negotiation loop then fills.
     *
     * @param prompt rendered task prompt text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected task template
     * @return filled parameter data carrying the extracted parameters; null values mark missing parameters
     */
    FilledParamData validateTaskPromptAndDataFilling(String prompt, Map<String, Object> schema, TemplateUri templateUri) {
        return taskValidator.validate(prompt, schema, templateUri);
    }

    // ------------------------------------------------------------------ mirrored client pipeline

    private String loadTemplate(String templateIdentifier) {
        try {
            return templateLoader.loadTemplate(templateIdentifier, language);
        } catch (ResourceNotFoundException error) {
            throw new PromptGenerationException(A2ATErrorCodes.TEMPLATE_NOT_FOUND, error.getMessage(), error);
        } catch (A2ATError error) {
            throw new PromptGenerationException(A2ATErrorCodes.PROMPT_RESOURCE_LOAD_ERROR, error.getMessage(), error);
        }
    }

    private Map<String, String> extractSlots(String userInput, String templateIdentifier, String templateText) {
        try {
            return slotValueExtractor.extractSlots(userInput, templateIdentifier, language).slots();
        } catch (ResourceNotFoundException error) {
            throw new PromptGenerationException(A2ATErrorCodes.SLOT_SCHEMA_NOT_FOUND, error.getMessage(), error);
        } catch (A2ATError error) {
            throw new PromptGenerationException(A2ATErrorCodes.LLM_INVOCATION_FAILED, error.getMessage(), error);
        }
    }

    private String render(String templateText, Map<String, String> slots) {
        try {
            return renderer.render(templateText, slots);
        } catch (TaskPromptRenderException error) {
            throw new PromptGenerationException(A2ATErrorCodes.RENDER_FAILED, error.getMessage(), error);
        }
    }

    private void validateRequiredSlots(Map<String, String> slots, String templateIdentifier) {
        final PromptSlotSchema schema;
        try {
            schema = slotSchemaLoader.loadSlotSchema(templateIdentifier, language);
        } catch (A2ATError error) {
            throw new PromptGenerationException(A2ATErrorCodes.SLOT_SCHEMA_NOT_FOUND, error.getMessage(), error);
        }
        if (schema == null) {
            throw new PromptGenerationException(
                    A2ATErrorCodes.SLOT_SCHEMA_NOT_FOUND, "Slot schema not found for template: " + templateIdentifier);
        }
        List<PromptSlotDefinition> definitions = schema.slotDefinitions();
        if (definitions == null) {
            return;
        }
        List<SlotValidationError> failed = new ArrayList<>();
        for (PromptSlotDefinition definition : definitions) {
            if (definition == null || !definition.required()) {
                continue;
            }
            String name = definition.name();
            if (name == null) {
                continue;
            }
            String value = slots.get(name);
            if (value == null || value.trim().isEmpty()) {
                failed.add(new SlotValidationError(name, "missing_required", "Required slot is missing or empty"));
            }
        }
        if (!failed.isEmpty()) {
            throw new PromptGenerationException(
                    A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                    "Required slots are missing or empty: " + failed.stream()
                            .map(SlotValidationError::slotName).collect(Collectors.joining(", ")),
                    failed);
        }
    }
}
