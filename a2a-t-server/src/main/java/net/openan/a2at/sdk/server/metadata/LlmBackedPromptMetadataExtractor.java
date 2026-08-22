package net.openan.a2at.sdk.server.metadata;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.prompt.analysis.exception.ScenarioRecognitionException;
import net.openan.a2at.sdk.prompt.analysis.impl.PromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.impl.ScenarioRecognizer;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotValidationError;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;

/**
 * Server-side metadata extractor that mirrors the Python flow by resolving scenario and slots from
 * the processed prompt text with LLM-backed analysis steps.
 *
 * @since 2026-06
 */
public final class LlmBackedPromptMetadataExtractor implements ServerPromptMetadataExtractor {

    private final ScenarioRecognizer scenarioRecognizer;

    private final List<ScenarioDefinition> scenarios;

    private final String language;

    private final String systemPrompt;

    private final String userPrompt;

    private final PromptTemplateTextLoader templateLoader;

    private final PromptSlotSchemaLoader slotSchemaLoader;

    private final PromptSlotValueExtractor slotValueExtractor;

    public LlmBackedPromptMetadataExtractor(
            ScenarioRecognizer scenarioRecognizer,
            List<ScenarioDefinition> scenarios,
            String language,
            String systemPrompt,
            String userPrompt,
            PromptTemplateTextLoader templateLoader,
            PromptSlotSchemaLoader slotSchemaLoader,
            PromptSlotValueExtractor slotValueExtractor) {
        this.scenarioRecognizer = scenarioRecognizer;
        this.scenarios = List.copyOf(scenarios);
        this.language = language;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.templateLoader = templateLoader;
        this.slotSchemaLoader = slotSchemaLoader;
        this.slotValueExtractor = slotValueExtractor;
    }

    @Override
    public ProcessedPromptMetadata extract(String processedPromptText) {
        ScenarioRecognitionResult recognitionResult = resolveScenario(processedPromptText);
        String scenarioCode = recognitionResult.scenarioCode();
        String templateText = loadTemplate(scenarioCode);
        PromptSlotSchema slotSchema = slotSchemaLoader.loadSlotSchema(scenarioCode, language);
        StructuredSlotExtractionResult extractionResult =
                slotValueExtractor.extractSlots(processedPromptText, scenarioCode, language);
        validateExtractionResult(extractionResult, slotSchema);
        return new ProcessedPromptMetadata(scenarioCode, language, templateText, Map.copyOf(extractionResult.slots()));
    }

    private ScenarioRecognitionResult resolveScenario(String processedPromptText) {
        try {
            ScenarioRecognitionResult result =
                    scenarioRecognizer.recognize(processedPromptText, scenarios, systemPrompt, userPrompt);
            if (!result.matched() || result.scenarioCode() == null || result.scenarioCode().isBlank()) {
                throw new PromptComplianceCheckException(
                        A2ATErrorCodes.PROCESSED_PROMPT_PARSE_ERROR,
                        result.errorMessage() == null ? "Scenario recognition failed." : result.errorMessage(),
                        "prompt_parse");
            }
            return result;
        } catch (ScenarioRecognitionException error) {
            throw new PromptComplianceCheckException(A2ATErrorCodes.PROCESSED_PROMPT_PARSE_ERROR, error.getMessage(), "prompt_parse");
        }
    }

    private String loadTemplate(String scenarioCode) {
        try {
            return templateLoader.loadTemplate(scenarioCode, language);
        } catch (ResourceNotFoundException error) {
            throw new PromptComplianceCheckException(A2ATErrorCodes.TEMPLATE_NOT_FOUND, error.getMessage(), "generation");
        }
    }

    private static void validateExtractionResult(
            StructuredSlotExtractionResult extractionResult, PromptSlotSchema slotSchema) {
        if (!extractionResult.slotErrors().isEmpty()) {
            throw new PromptComplianceCheckException(
                    A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                    extractionResult.slotErrors().stream()
                            .map(StructuredSlotValidationError::message)
                            .filter(message -> message != null && !message.isBlank())
                            .collect(Collectors.joining("; ")),
                    "slot_validation");
        }

        for (PromptSlotDefinition definition : slotSchema.slotDefinitions()) {
            String value = extractionResult.slots().get(definition.name());
            if (definition.required() && (value == null || value.isBlank())) {
                throw new PromptComplianceCheckException(
                        A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                        "Required slot '" + definition.name() + "' is missing.",
                        "slot_validation");
            }
            if (value == null || value.isBlank()) {
                continue;
            }
            if (definition.allowedValues() != null
                    && !definition.allowedValues().isEmpty()
                    && !definition.allowedValues().contains(value.trim())) {
                throw new PromptComplianceCheckException(
                        A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                        "Slot '" + definition.name() + "' violates allowed values.",
                        "slot_validation");
            }
            if (definition.pattern() != null
                    && !definition.pattern().isBlank()
                    && !value.trim().matches(definition.pattern())) {
                throw new PromptComplianceCheckException(
                        A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                        "Slot '" + definition.name() + "' violates pattern constraint.",
                        "slot_validation");
            }
            validateNumericConstraint(definition, value.trim());
        }
    }

    private static void validateNumericConstraint(PromptSlotDefinition definition, String value) {
        if (!"integer".equalsIgnoreCase(definition.jsonType()) && !"number".equalsIgnoreCase(definition.jsonType())) {
            return;
        }
        try {
            double numericValue = Double.parseDouble(value);
            if (definition.minimum() != null && numericValue < definition.minimum()) {
                throw new PromptComplianceCheckException(
                        A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                        "Slot '" + definition.name() + "' is smaller than minimum.",
                        "slot_validation");
            }
            if (definition.maximum() != null && numericValue > definition.maximum()) {
                throw new PromptComplianceCheckException(
                        A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                        "Slot '" + definition.name() + "' is larger than maximum.",
                        "slot_validation");
            }
        } catch (NumberFormatException error) {
            throw new PromptComplianceCheckException(
                    A2ATErrorCodes.SLOT_VALIDATION_ERROR,
                    "Slot '" + definition.name() + "' is not numeric.",
                    "slot_validation");
        }
    }
}
