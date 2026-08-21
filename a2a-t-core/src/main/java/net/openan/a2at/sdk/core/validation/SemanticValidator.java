package net.openan.a2at.sdk.core.validation;

import java.util.Map;

/**
 * LLM-backed semantic validator for content.
 *
 * <p>The validator performs a single structured LLM call that combines semantic validation with parameter extraction.
 *
 * @since 2026-08
 */
public interface SemanticValidator {

    /**
     * Validates one content prompt semantically and extracts its parameters.
     *
     * @param prompt content prompt text
     * @param schema caller-provided parameter JSON schema embedded into the structured-call output contract
     * @param reference template reference the content is validated against
     * @return semantic validation outcome carrying the verdict, the semantic errors and the extracted parameters
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if the semantic validation prompt resources
     *     are missing
     */
    ValidationResult validate(String prompt, Map<String, Object> schema, TemplateReference reference);
}