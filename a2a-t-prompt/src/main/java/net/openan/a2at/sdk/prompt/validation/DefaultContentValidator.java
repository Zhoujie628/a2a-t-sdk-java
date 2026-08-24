package net.openan.a2at.sdk.prompt.validation;

import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import net.openan.a2at.sdk.core.validation.ValidationPipeline;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Default content validator that orchestrates the full validation pipeline with a no-op rule checker and an LLM-backed
 * semantic validator.
 *
 * <p>The template referenced by the {@code templateUri} argument of every {@link #validate} call is loaded through a
 * sourceType-aware template loader before the validation pipeline is entered. A load failure is reported as a
 * {@code TEMPLATE_NOT_FOUND} error, distinct from the {@code VALIDATION_PROMPT_RESOURCE_NOT_FOUND} code that signals
 * missing content_validation prompt resources. The loaded template text flows into the pipeline for prompt injection.
 *
 * <p>The semantic validator is initialised lazily on the first {@link #validate} call so that constructing the
 * validator never loads prompt resources — the facade builders can wire the validator eagerly without requiring the
 * content_validation prompt resources to be present at startup. The content_validation prompt resources are internal
 * LLM instructions loaded from the classpath regardless of the configured prompt source type.
 *
 * @since 2026-08
 */
public final class DefaultContentValidator implements ContentValidator {

    private final String extensionName;
    private final String language;
    private final int maxAttempts;
    private final LLMClient llmClient;
    private final PromptTemplateTextLoader templateLoader;

    private volatile ValidationPipeline pipeline;

    /**
     * Creates a content validator for the given extension name and language.
     *
     * <p>The constructor does not load any prompt resources. The underlying semantic validator and its prompt resources
     * are loaded on the first {@link #validate} call.
     *
     * @param extensionName extension name used for template URI validation
     * @param language language code for prompt resource loading and template loading
     * @param maxAttempts maximum retry attempts for semantic validation
     * @param llmClient LLM client for semantic validation; may be {@code null}, in which case the first
     *     {@link #validate} call fails with {@code ContentValidationException} carrying
     *     {@code VALIDATION_LLM_INFRASTRUCTURE_ERROR}; there is no late injection point
     * @param templateLoader sourceType-aware template text loader for loading the template referenced by the
     *     {@code templateUri} of every {@link #validate} call
     */
    public DefaultContentValidator(
            @NonNull String extensionName,
            @NonNull String language,
            int maxAttempts,
            @Nullable LLMClient llmClient,
            @NonNull PromptTemplateTextLoader templateLoader) {
        this.extensionName = extensionName;
        this.language = language;
        this.maxAttempts = maxAttempts;
        this.llmClient = llmClient;
        this.templateLoader = templateLoader;
    }

    /**
     * Validates one content prompt and extracts its filled parameters.
     *
     * @param prompt content prompt text
     * @param schema caller-provided parameter JSON schema
     * @param templateUri URI of the template the content is validated against, such as
     *     {@code Task-T/network-layer/energy-saving/v1}
     * @return filled parameter data carrying the merged parameters
     * @throws NullPointerException if the prompt, schema or template URI is null
     * @throws IllegalArgumentException if the prompt is blank, the template URI addresses another extension than the
     *     one this validator is configured for, or the template URI version is unsupported
     * @throws ContentValidationException if the validation fails at any stage, including
     *     {@code TEMPLATE_NOT_FOUND} when the template cannot be loaded, or
     *     {@code VALIDATION_PROMPT_RESOURCE_NOT_FOUND} when the content_validation prompt resources of the configured
     *     language are missing on the classpath
     */
    @Override
    public FilledParamData validate(
            @NonNull String prompt, @NonNull Map<String, Object> schema, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");

        if (!extensionName.equals(templateUri.extensionName())) {
            throw new IllegalArgumentException("Template URI extension '" + templateUri.extensionName()
                    + "' does not match expected extension '" + extensionName + "'.");
        }

        if (!TemplateUri.DEFAULT_TEMPLATE_VERSION.equals(templateUri.templateVersion())) {
            throw new IllegalArgumentException("Unsupported template URI version: " + templateUri.templateVersion());
        }

        String templateContent;
        try {
            templateContent = templateLoader.loadTemplate(templateUri.uri(), language);
        } catch (ResourceNotFoundException exception) {
            throw new ContentValidationException(
                    A2ATErrorCodes.TEMPLATE_NOT_FOUND,
                    "Template not found for URI " + templateUri.uri() + " and language " + language + ": "
                            + exception.getMessage(),
                    exception);
        }

        return pipeline().validate(prompt, schema, templateUri, templateContent);
    }

    private ValidationPipeline pipeline() {
        ValidationPipeline p = pipeline;
        if (p == null) {
            synchronized (this) {
                p = pipeline;
                if (p == null) {
                    try {
                        p = new ValidationPipeline(
                                prompt -> Map.of(), new DefaultSemanticValidator(llmClient, language), maxAttempts);
                    } catch (ResourceNotFoundException exception) {
                        throw new ContentValidationException(
                                A2ATErrorCodes.VALIDATION_PROMPT_RESOURCE_NOT_FOUND, exception.getMessage(), exception);
                    }
                    pipeline = p;
                }
            }
        }
        return p;
    }
}
