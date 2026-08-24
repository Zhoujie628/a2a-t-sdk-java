package net.openan.a2at.sdk.negotiation.validation;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.SemanticValidator;
import net.openan.a2at.sdk.core.validation.ValidationResult;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;

/**
 * LLM-backed semantic validator for negotiation messages.
 *
 * <p>The validator performs a single structured LLM call that combines semantic validation with parameter extraction
 * and then enforces the declared type consistency in code: when the verdict is true, the negotiation type reported for
 * the message must be present and must match the type declared by the template reference. A response that misses one of
 * the four required keys or has the wrong shape is a validation infrastructure failure signalled through the internal
 * {@link NegotiationValidationException}.
 *
 * @since 2026-08
 */
public interface NegotiationSemanticValidator extends SemanticValidator<NegotiationReference> {

    /**
     * Validates one rendered negotiation message semantically and extracts its parameters.
     *
     * @param prompt rendered negotiation message text
     * @param callerSchema caller-provided parameter JSON schema embedded into the structured-call output contract
     * @param reference negotiation reference the message is validated against, carrying the declared type, phase and
     *     language
     * @param templateContent loaded template text used as a reference for structure/completeness checks
     * @return semantic validation outcome carrying the verdict, the implied negotiation type, the semantic errors and
     *     the extracted parameters
     * @throws NegotiationValidationException if the LLM invocation fails or the response misses a required key or has
     *     the wrong shape
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if the semantic validation prompt resources
     *     of the reference language are missing
     */
    SemanticValidationResult validateNegotiation(
            String prompt, Map<String, Object> callerSchema, NegotiationReference reference, String templateContent);

    @Override
    default ValidationResult validate(
            String prompt, Map<String, Object> schema, NegotiationReference reference, String templateContent) {
        try {
            SemanticValidationResult result = validateNegotiation(prompt, schema, reference, templateContent);
            return new ValidationResult(result.verdict(), result.errors(), result.params());
        } catch (NegotiationValidationException exception) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR,
                    "Semantic validation LLM step failed: " + exception.getMessage(),
                    List.of(new SlotValidationError(
                            "_llm", A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR, exception.getMessage())),
                    exception);
        }
    }
}
