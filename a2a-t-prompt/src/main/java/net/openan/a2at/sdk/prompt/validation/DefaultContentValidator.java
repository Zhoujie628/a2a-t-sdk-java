package net.openan.a2at.sdk.prompt.validation;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import net.openan.a2at.sdk.core.validation.TemplateReference;
import net.openan.a2at.sdk.core.validation.ValidationPipeline;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.resources.loader.PromptResourceAccess;

/**
 * Default content validator that orchestrates the full validation pipeline with a no-op rule checker and an LLM-backed
 * semantic validator.
 *
 * @since 2026-08
 */
public final class DefaultContentValidator implements ContentValidator {

    private final ValidationPipeline pipeline;
    private final String extensionPrefix;
    private final String language;

    /**
     * Creates a content validator for the given extension prefix and language.
     *
     * @param extensionPrefix extension prefix used for template URI validation
     * @param language language code for prompt resource loading
     * @param maxAttempts maximum retry attempts for semantic validation
     * @param llmClient LLM client for semantic validation; may be {@code null} and set later
     * @param promptResourceAccess prompt resource access for loading validation prompts
     */
    public DefaultContentValidator(
            String extensionPrefix,
            String language,
            int maxAttempts,
            LLMClient llmClient,
            PromptResourceAccess promptResourceAccess) {
        this.extensionPrefix = extensionPrefix;
        this.language = language;
        this.pipeline = new ValidationPipeline(
                prompt -> Map.of(),
                new DefaultSemanticValidator(llmClient, language, promptResourceAccess),
                maxAttempts);
    }

    @Override
    public FilledParamData validate(String prompt, Map<String, Object> schema, String templateUri) {
        if (templateUri == null || templateUri.isBlank()) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_INVALID_INPUT, "Template URI must not be null or blank.");
        }

        String[] parts = templateUri.split("/");
        if (parts.length < 3) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_INVALID_INPUT,
                    "Template URI format must be {prefix}/v1/{scenario}, got: " + templateUri);
        }

        String prefix = parts[0];
        String version = parts[1];
        String scenario = parts[2];

        if (!extensionPrefix.equals(prefix)) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_INVALID_INPUT,
                    "Template URI prefix '" + prefix + "' does not match expected extension prefix '" + extensionPrefix
                            + "'.");
        }

        if (!"v1".equals(version)) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_INVALID_INPUT,
                    "Unsupported template URI version: " + version);
        }

        TemplateReference reference = new SimpleTemplateReference(templateUri, language, extensionPrefix);
        return pipeline.validate(prompt, schema, reference);
    }

    /**
     * Package-private template reference implementation.
     */
    record SimpleTemplateReference(String uri, String language, String extensionPrefix) implements TemplateReference {}
}