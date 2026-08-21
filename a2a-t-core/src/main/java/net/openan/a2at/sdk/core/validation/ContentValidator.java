package net.openan.a2at.sdk.core.validation;

import java.util.Map;
import net.openan.a2at.sdk.core.model.FilledParamData;
import org.jspecify.annotations.NonNull;

/**
 * Entry point for validating content and extracting filled parameters.
 *
 * <p>Implementations orchestrate the full validation pipeline: input validation, rule-level gate, semantic validation
 * and parameter merging.
 *
 * @since 2026-08
 */
public interface ContentValidator {

    /**
     * Validates one content prompt and extracts its filled parameters.
     *
     * @param prompt content prompt text
     * @param schema caller-provided parameter JSON schema
     * @param templateUri URI of the template the content is validated against, such as
     *     {@code Task-T/network-layer/energy-saving/v1}
     * @return filled parameter data carrying the merged parameters
     * @throws NullPointerException if the prompt, schema or template URI is null
     * @throws IllegalArgumentException if the prompt is blank
     * @throws ContentValidationException if the validation fails at any stage
     */
    FilledParamData validate(
            @NonNull String prompt, @NonNull Map<String, Object> schema, @NonNull TemplateUri templateUri);
}
