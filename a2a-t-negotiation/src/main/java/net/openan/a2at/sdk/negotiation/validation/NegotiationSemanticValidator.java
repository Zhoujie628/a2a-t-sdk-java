package net.openan.a2at.sdk.negotiation.validation;

import java.util.Map;
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
 * @since 2026-06
 */
public interface NegotiationSemanticValidator {

    /**
     * Validates one rendered negotiation message semantically and extracts its parameters.
     *
     * @param prompt rendered negotiation message text
     * @param callerSchema caller-provided parameter JSON schema embedded into the structured-call output contract
     * @param reference template reference the message is validated against, carrying the declared type, phase and
     *     language
     * @return semantic validation outcome carrying the verdict, the implied negotiation type, the semantic errors and
     *     the extracted parameters
     * @throws NegotiationValidationException if the LLM invocation fails or the response misses a required key or has
     *     the wrong shape
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if the semantic validation prompt resources
     *     of the reference language are missing
     */
    SemanticValidationResult validate(String prompt, Map<String, Object> callerSchema, NegotiationReference reference);
}
