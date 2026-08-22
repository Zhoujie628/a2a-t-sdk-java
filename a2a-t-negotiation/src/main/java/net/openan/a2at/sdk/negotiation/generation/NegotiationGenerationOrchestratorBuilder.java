package net.openan.a2at.sdk.negotiation.generation;

import java.nio.file.Path;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.resources.DefaultNegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.validation.DefaultNegotiationComplianceChecker;
import net.openan.a2at.sdk.negotiation.validation.DefaultNegotiationSemanticValidator;
import net.openan.a2at.sdk.negotiation.validation.NegotiationComplianceChecker;
import net.openan.a2at.sdk.negotiation.validation.NegotiationSemanticValidator;
import net.openan.a2at.sdk.negotiation.validation.NegotiationValidationException;
import net.openan.a2at.sdk.negotiation.validation.ParamExtractor;
import org.slf4j.Logger;

/**
 * Fluent builder assembling one {@link NegotiationGenerationOrchestrator}.
 *
 * <p>The language is required. The LLM client is optional: without one, the from-data generation still works, while
 * the LLM steps of the from-text generation and the validation pipeline fail with the
 * {@code negotiation_llm_infrastructure_error} code. Every collaborator has a default implementation wired from the
 * language and the optional local resource root (templates and negotiation vocabulary), and each of them can be
 * overridden for testing or customization.
 *
 * @since 2026-08
 */
public final class NegotiationGenerationOrchestratorBuilder {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private String language;

    private String localRootDir;

    private LLMClient llmClient;

    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    private NegotiationTemplateLoader templateLoader;

    private NegotiationContentExtractor contentExtractor;

    private NegotiationComplianceChecker complianceChecker;

    private NegotiationSemanticValidator semanticValidator;

    private Logger logger;

    private NegotiationGenerationOrchestratorBuilder() {}

    /**
     * Creates one new builder instance.
     *
     * @return empty orchestrator builder
     */
    public static NegotiationGenerationOrchestratorBuilder builder() {
        return new NegotiationGenerationOrchestratorBuilder();
    }

    /**
     * Configures the language of the generated and validated messages.
     *
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @return current builder
     */
    public NegotiationGenerationOrchestratorBuilder language(String language) {
        this.language = language;
        return this;
    }

    /**
     * Configures the optional local prompt resource root containing the {@code templates/} and
     * {@code negotiation-vocabulary/} trees.
     *
     * @param localRootDir local prompt resource root; null or blank disables local template and vocabulary overrides
     * @return current builder
     */
    public NegotiationGenerationOrchestratorBuilder localRootDir(String localRootDir) {
        this.localRootDir = localRootDir;
        return this;
    }

    /**
     * Configures the LLM client used by the LLM steps of the pipelines.
     *
     * @param llmClient LLM client; null keeps the LLM steps unavailable while the deterministic steps keep working
     * @return current builder
     */
    public NegotiationGenerationOrchestratorBuilder llmClient(LLMClient llmClient) {
        this.llmClient = llmClient;
        return this;
    }

    /**
     * Configures how often one LLM step is attempted before its failure is surfaced.
     *
     * @param maxAttempts maximum number of attempts per LLM step, at least 1
     * @return current builder
     */
    public NegotiationGenerationOrchestratorBuilder maxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
    }

    /**
     * Overrides the negotiation template loader.
     *
     * @param templateLoader loader resolving template references to templates
     * @return current builder
     */
    public NegotiationGenerationOrchestratorBuilder templateLoader(NegotiationTemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
        return this;
    }

    /**
     * Overrides the content extractor used by the from-text generation.
     *
     * @param contentExtractor extractor turning free text into typed negotiation content
     * @return current builder
     */
    public NegotiationGenerationOrchestratorBuilder contentExtractor(NegotiationContentExtractor contentExtractor) {
        this.contentExtractor = contentExtractor;
        return this;
    }

    /**
     * Overrides the rule-level compliance checker used by the validation pipeline.
     *
     * @param complianceChecker deterministic rule gate of the validation pipeline
     * @return current builder
     */
    public NegotiationGenerationOrchestratorBuilder complianceChecker(NegotiationComplianceChecker complianceChecker) {
        this.complianceChecker = complianceChecker;
        return this;
    }

    /**
     * Overrides the LLM-backed semantic validator used by the validation pipeline.
     *
     * @param semanticValidator semantic validator producing the verdict and the extracted parameters
     * @return current builder
     */
    public NegotiationGenerationOrchestratorBuilder semanticValidator(NegotiationSemanticValidator semanticValidator) {
        this.semanticValidator = semanticValidator;
        return this;
    }

    /**
     * Overrides the logger of the assembled orchestrator.
     *
     * @param logger logger receiving the pipeline events; null falls back to the default class logger
     * @return current builder
     */
    public NegotiationGenerationOrchestratorBuilder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    /**
     * Assembles the orchestrator from the configured inputs.
     *
     * @return assembled negotiation generation orchestrator
     * @throws IllegalStateException if the language is missing or the attempt limit is below 1
     * @throws IllegalArgumentException if the language has no bundled vocabulary
     */
    public NegotiationGenerationOrchestrator build() {
        if (language == null || language.isBlank()) {
            throw new IllegalStateException("Negotiation language must be configured.");
        }
        if (maxAttempts < 1) {
            throw new IllegalStateException(
                    "Negotiation LLM max attempts must be at least 1 but was " + maxAttempts + ".");
        }
        Path localVocabularyRoot = localRootDir == null || localRootDir.isBlank() ? null : Path.of(localRootDir);
        Vocabulary vocabulary = Vocabulary.forLanguage(language, localVocabularyRoot);
        NegotiationTemplateLoader effectiveTemplateLoader =
                templateLoader != null ? templateLoader : new DefaultNegotiationTemplateLoader(language, localRootDir);
        NegotiationContentExtractor effectiveContentExtractor =
                contentExtractor != null ? contentExtractor : new DefaultNegotiationContentExtractor(llmClient);
        NegotiationComplianceChecker effectiveComplianceChecker =
                complianceChecker != null ? complianceChecker : new DefaultNegotiationComplianceChecker();
        NegotiationSemanticValidator effectiveSemanticValidator =
                semanticValidator != null ? semanticValidator : defaultSemanticValidator();
        ParamExtractor paramExtractor =
                new ParamExtractor(effectiveComplianceChecker, effectiveSemanticValidator, maxAttempts);
        return new NegotiationGenerationOrchestrator(
                language,
                maxAttempts,
                effectiveTemplateLoader,
                effectiveContentExtractor,
                paramExtractor,
                new NegotiationGeneratorRegistry(),
                vocabulary,
                logger);
    }

    /**
     * Builds the default semantic validator, wiring the schema builder of the generation package into the
     * validation-package schema seam.
     *
     * @return default LLM-backed semantic validator, or a validator that fails every call when no LLM client is
     *     configured
     */
    private NegotiationSemanticValidator defaultSemanticValidator() {
        if (llmClient == null) {
            return (prompt, callerSchema, reference) -> {
                throw new NegotiationValidationException(
                        "Semantic validation requires an LLM client but none is configured.");
            };
        }
        return new DefaultNegotiationSemanticValidator(llmClient);
    }
}
