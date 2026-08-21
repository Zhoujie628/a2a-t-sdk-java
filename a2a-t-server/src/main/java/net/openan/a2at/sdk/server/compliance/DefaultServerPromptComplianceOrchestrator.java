package net.openan.a2at.sdk.server.compliance;

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

    private final ServerPromptMetadataExtractor metadataExtractor;

    private final ServerPromptSemanticValidator semanticValidator;

    /**
     * Creates a compliance orchestrator.
     *
     * @param metadataExtractor prompt metadata extractor
     * @param semanticValidator semantic validator
     */
    public DefaultServerPromptComplianceOrchestrator(
            ServerPromptMetadataExtractor metadataExtractor, ServerPromptSemanticValidator semanticValidator) {
        this.metadataExtractor = metadataExtractor;
        this.semanticValidator = semanticValidator;
    }

    @Override
    public PromptComplianceResult checkTaskPrompt(String processedPromptText) {
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
