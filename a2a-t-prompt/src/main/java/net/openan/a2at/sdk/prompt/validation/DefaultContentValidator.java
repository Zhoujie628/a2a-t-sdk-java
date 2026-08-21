package net.openan.a2at.sdk.prompt.validation;

import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import net.openan.a2at.sdk.core.validation.TemplateUri;
import net.openan.a2at.sdk.core.validation.ValidationPipeline;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.resources.loader.PromptResourceAccess;

/**
 * Default content validator that orchestrates the full validation pipeline with a no-op rule checker and an LLM-backed
 * semantic validator.
 *
 * <p>The semantic validator is initialised lazily on the first {@link #validate} call so that constructing the
 * validator never loads prompt resources — the facade builders can wire the validator eagerly without requiring the
 * content_validation prompt resources to be present at startup.
 *
 * @since 2026-08
 */
public final class DefaultContentValidator implements ContentValidator {

    private final String extensionName;
    private final String language;
    private final int maxAttempts;
    private final LLMClient llmClient;
    private final PromptResourceAccess promptResourceAccess;

    private volatile ValidationPipeline pipeline;

    /**
     * Creates a content validator for the given extension name and language.
     *
     * <p>The constructor does not load any prompt resources. The underlying semantic validator and its prompt resources
     * are loaded on the first {@link #validate} call.
     *
     * @param extensionName extension name used for template URI validation
     * @param language language code for prompt resource loading
     * @param maxAttempts maximum retry attempts for semantic validation
     * @param llmClient LLM client for semantic validation; may be {@code null} and set later
     * @param promptResourceAccess prompt resource access for loading validation prompts
     */
    public DefaultContentValidator(
            String extensionName,
            String language,
            int maxAttempts,
            LLMClient llmClient,
            PromptResourceAccess promptResourceAccess) {
        this.extensionName = extensionName;
        this.language = language;
        this.maxAttempts = maxAttempts;
        this.llmClient = llmClient;
        this.promptResourceAccess = promptResourceAccess;
    }

    @Override
    public FilledParamData validate(String prompt, Map<String, Object> schema, String templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        if (templateUri.isBlank()) {
            throw new IllegalArgumentException("Template URI must not be blank.");
        }

        String[] parts = templateUri.split("/");
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Template URI format must be {prefix}/v1/{scenario}, got: " + templateUri);
        }

        String prefix = parts[0];
        String version = parts[1];

        if (!extensionName.equals(prefix)) {
            throw new IllegalArgumentException(
                    "Template URI prefix '" + prefix + "' does not match expected extension prefix '" + extensionName
                            + "'.");
        }

        if (!"v1".equals(version)) {
            throw new IllegalArgumentException("Unsupported template URI version: " + version);
        }

        TemplateUri reference = TemplateUri.of(prefix, version, java.util.Arrays.copyOfRange(parts, 2, parts.length));
        return pipeline().validate(prompt, schema, reference);
    }

    private ValidationPipeline pipeline() {
        ValidationPipeline p = pipeline;
        if (p == null) {
            synchronized (this) {
                p = pipeline;
                if (p == null) {
                    p = new ValidationPipeline(
                            prompt -> Map.of(),
                            new DefaultSemanticValidator(llmClient, language, promptResourceAccess),
                            maxAttempts);
                    pipeline = p;
                }
            }
        }
        return p;
    }
}