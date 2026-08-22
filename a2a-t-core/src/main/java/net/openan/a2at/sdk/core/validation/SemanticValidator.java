package net.openan.a2at.sdk.core.validation;

import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * LLM-backed semantic validator for content.
 *
 * <p>The validator performs a single structured LLM call that combines semantic validation with parameter extraction.
 * The reference type is generic so each caller passes its own template addressing type — for example a
 * {@link TemplateUri} for extension content or a richer negotiation reference carrying type and phase.
 *
 * @param <T> template addressing type the validation is performed against
 * @since 2026-08
 */
public interface SemanticValidator<T> {

    /**
     * Validates one content prompt semantically and extracts its parameters.
     *
     * @param prompt content prompt text
     * @param schema caller-provided parameter JSON schema embedded into the structured-call output contract
     * @param reference template addressing value the content is validated against
     * @return semantic validation outcome carrying the verdict, the semantic errors and the extracted parameters
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if the semantic validation prompt resources
     *     are missing
     */
    @NonNull ValidationResult validate(
            @NonNull String prompt, @NonNull Map<String, Object> schema, @NonNull T reference);
}
