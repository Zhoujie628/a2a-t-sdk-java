package net.openan.a2at.sdk.server.compliance;

import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.server.metadata.ServerPromptMetadataExtractor;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;
import net.openan.a2at.sdk.server.model.PromptComplianceFailure;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.validation.ServerPromptSemanticValidator;

/**
 * Minimal runnable server-side prompt compliance orchestrator.
 *
 * @since 2026-06
 */
public final class DefaultServerPromptComplianceOrchestrator implements ServerPromptComplianceOrchestrator {

    /** Compliance stage reported when the input gate rejects an oversized prompt before any LLM call. */
    private static final String INPUT_GATE_STAGE = "input_gate";

    private final ServerPromptMetadataExtractor metadataExtractor;

    private final ServerPromptSemanticValidator semanticValidator;

    private final int maxTextChars;

    /**
     * Creates a compliance orchestrator.
     *
     * @param metadataExtractor prompt metadata extractor
     * @param semanticValidator semantic validator
     */
    public DefaultServerPromptComplianceOrchestrator(
            ServerPromptMetadataExtractor metadataExtractor, ServerPromptSemanticValidator semanticValidator) {
        this(metadataExtractor, semanticValidator, InputLimitConfig.DEFAULT_MAX_TEXT_CHARS);
    }

    /**
     * Creates a compliance orchestrator with an explicit free-text input limit.
     *
     * @param metadataExtractor prompt metadata extractor
     * @param semanticValidator semantic validator
     * @param maxTextChars maximum length in characters accepted for the processed prompt text
     */
    public DefaultServerPromptComplianceOrchestrator(
            ServerPromptMetadataExtractor metadataExtractor,
            ServerPromptSemanticValidator semanticValidator,
            int maxTextChars) {
        this.metadataExtractor = metadataExtractor;
        this.semanticValidator = semanticValidator;
        this.maxTextChars = maxTextChars;
    }

    @Override
    public PromptComplianceResult checkTaskPrompt(String processedPromptText) {
        if (InputLimitConfig.isTooLong(processedPromptText, maxTextChars)) {
            return new PromptComplianceResult(
                    false,
                    new PromptComplianceFailure(
                            A2ATErrorCodes.INPUT_TEXT_TOO_LONG,
                            InputLimitConfig.violationMessage(processedPromptText, maxTextChars),
                            INPUT_GATE_STAGE));
        }
        try {
            ProcessedPromptMetadata metadata = metadataExtractor.extract(processedPromptText);
            semanticValidator.validate(processedPromptText, metadata);
            return new PromptComplianceResult(true, null);
        } catch (PromptComplianceCheckException error) {
            return new PromptComplianceResult(
                    false, new PromptComplianceFailure(error.getCode(), error.getMessage(), error.getStage()));
        }
    }
}
