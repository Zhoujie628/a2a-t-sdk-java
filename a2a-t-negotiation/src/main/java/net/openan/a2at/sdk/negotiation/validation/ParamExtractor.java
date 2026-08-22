package net.openan.a2at.sdk.negotiation.validation;

import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.RuleChecker;
import net.openan.a2at.sdk.core.validation.SemanticValidator;
import net.openan.a2at.sdk.core.validation.ValidationPipeline;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;

/**
 * Orchestrates the validation of a negotiation message and the extraction of its parameters.
 *
 * <p>The extractor delegates to the shared {@link ValidationPipeline} from the core module, which runs the rule-level
 * gate, retryable semantic validation and deterministic parameter merging in one pass. The rule-level gate is a
 * per-call {@link NegotiationRuleCheckerAdapter} bridging the negotiation context carried alongside the message to the
 * core {@link RuleChecker} contract; a {@link SemanticValidator} adapter bridges the negotiation types to the core
 * validation contracts.
 *
 * @since 2026-08
 */
public final class ParamExtractor {

    private final NegotiationComplianceChecker complianceChecker;

    private final SemanticValidator<NegotiationReference> semanticValidator;

    private final int maxAttempts;

    /**
     * Creates a parameter extractor.
     *
     * @param complianceChecker rule-level checker used as the entry gate
     * @param semanticValidator LLM-backed semantic validator producing the semantic verdict and extracted parameters
     * @param maxAttempts maximum number of retry attempts for the semantic validation step
     * @throws NullPointerException if any collaborator is null
     */
    public ParamExtractor(
            NegotiationComplianceChecker complianceChecker,
            SemanticValidator<NegotiationReference> semanticValidator,
            int maxAttempts) {
        this.complianceChecker = Objects.requireNonNull(complianceChecker, "complianceChecker");
        this.semanticValidator = Objects.requireNonNull(semanticValidator, "semanticValidator");
        this.maxAttempts = maxAttempts;
    }

    /**
     * Validates one negotiation message and extracts its parameters through the full pipeline.
     *
     * @param prompt rendered negotiation message text
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param reference template reference the message is validated against
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NegotiationParamExtractionException with the code {@code negotiation_invalid_input},
     *     {@code negotiation_rule_violation}, {@code negotiation_semantic_rejected},
     *     {@code negotiation_llm_infrastructure_error} or {@code template_not_found} when the validation pipeline
     *     fails
     */
    public FilledParamData extract(
            String prompt, NegotiationContext context, Map<String, Object> schema, NegotiationReference reference) {
        ValidationPipeline<NegotiationReference> pipeline = new ValidationPipeline<>(
                new NegotiationRuleCheckerAdapter(complianceChecker, context), semanticValidator, maxAttempts);
        try {
            return pipeline.validate(prompt, schema, reference);
        } catch (ContentValidationException e) {
            throw new NegotiationParamExtractionException(mapCode(e.getCode()), e.getMessage(), e.errors());
        }
    }

    private static String mapCode(String code) {
        switch (code) {
            case A2ATErrorCodes.VALIDATION_INVALID_INPUT:
                return A2ATErrorCodes.NEGOTIATION_INVALID_INPUT;
            case A2ATErrorCodes.VALIDATION_RULE_VIOLATION:
                return A2ATErrorCodes.NEGOTIATION_RULE_VIOLATION;
            case A2ATErrorCodes.VALIDATION_SEMANTIC_REJECTED:
                return A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED;
            case A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR:
                return A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR;
            case A2ATErrorCodes.VALIDATION_PROMPT_RESOURCE_NOT_FOUND:
                return A2ATErrorCodes.TEMPLATE_NOT_FOUND;
            default:
                return code;
        }
    }
}
