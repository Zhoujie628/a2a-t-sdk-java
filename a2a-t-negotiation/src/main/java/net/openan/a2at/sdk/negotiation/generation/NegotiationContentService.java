package net.openan.a2at.sdk.negotiation.generation;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Shared service for the negotiation content layer consumed by both the client and the server facade.
 *
 * <p>Each facade method delegates to exactly one service method, so the negotiation content-layer surface is defined
 * once instead of being copy-pasted per facade. The service itself is a thin typing over the
 * {@link NegotiationGenerationOrchestrator} pipeline and adds no behavior.
 *
 * @since 2026-08
 */
public final class NegotiationContentService {

    private final NegotiationGenerationOrchestrator orchestrator;

    /**
     * Creates one service over the given negotiation generation orchestrator.
     *
     * @param orchestrator negotiation generation orchestrator carrying the actual pipelines
     */
    public NegotiationContentService(@NonNull NegotiationGenerationOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "Negotiation orchestrator must not be null.");
    }

    /**
     * Assembles the default negotiation generation orchestrator from the unified SDK config.
     *
     * <p>The wiring is shared by the client and the server builder: the message language and the local template root
     * come from the prompt runtime config, the retry attempt limit comes from the LLM config, and the LLM client is
     * passed by the caller and may be null when the provider is {@code local_rule}.
     *
     * @param config unified SDK config
     * @param llmClient LLM client for the LLM-backed steps; null keeps those steps unavailable
     * @return assembled negotiation generation orchestrator
     */
    public static NegotiationGenerationOrchestrator buildOrchestrator(
            @NonNull A2ATConfig config, @Nullable LLMClient llmClient) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(config.prompt().language())
                .localRootDir(config.prompt().localRootDir())
                .llmClient(llmClient)
                .maxAttempts(config.llm().maxAttempts())
                .build();
    }

    /**
     * Resolves the relative prompt resource local root directory against the `.env` file location.
     *
     * <p>The returned config carries the local root as an absolute normalized path; an absolute configured root is
     * only normalized. Both facades resolve their config through this method before assembling their builders.
     *
     * @param config unified SDK config as loaded from the `.env` file
     * @param envPath resolved `.env` file path the config was loaded from
     * @return config with the local root resolved to an absolute normalized path
     */
    public static A2ATConfig resolvePromptResourceLocalRootDir(
            @NonNull A2ATConfig config, @NonNull Path envPath) {
        String localRootDir = config.prompt().localRootDir();
        Path localRootPath = Path.of(localRootDir);
        Path resolvedLocalRootPath = localRootPath.isAbsolute()
                ? localRootPath.normalize()
                : envPath.getParent().resolve(localRootPath).toAbsolutePath().normalize();
        return new A2ATConfig(
                new PromptRuntimeConfig(
                        config.prompt().language(), config.prompt().sourceType(), resolvedLocalRootPath.toString()),
                config.llm(),
                config.negotiation(),
                config.promptCompliance());
    }

    /**
     * Generates a propose-phase negotiation message from typed data, deterministically without any LLM call.
     *
     * @param data typed propose input carrying the negotiation context and the typed content
     * @param templateUri template URI whose phase segment must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase or type contradicts the method or the content type
     * @throws NegotiationGenerationException with the code {@code template_not_found} or
     *     {@code negotiation_slot_missing} when loading or rendering the template fails
     */
    public MetadataContent generateProposeFromData(
            @NonNull NegotiationProposeData data, @NonNull TemplateUri templateUri) {
        return orchestrator.generateProposeFromData(data, templateUri);
    }

    /**
     * Generates an accept-phase negotiation message from typed data, deterministically without any LLM call.
     *
     * @param data typed terminal input whose content conclusion must be {@code Accept}
     * @param templateUri template URI whose phase segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase or type contradicts the method or the content, or
     *     the content conclusion is not {@code Accept}
     * @throws NegotiationGenerationException with the code {@code template_not_found} or
     *     {@code negotiation_slot_missing} when loading or rendering the template fails
     */
    public MetadataContent generateAcceptFromData(
            @NonNull NegotiationEndingData data, @NonNull TemplateUri templateUri) {
        return orchestrator.generateAcceptFromData(data, templateUri);
    }

    /**
     * Generates a reject-phase negotiation message from typed data, deterministically without any LLM call.
     *
     * @param data typed terminal input whose content conclusion must be {@code Reject}
     * @param templateUri template URI whose phase segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase or type contradicts the method or the content, or
     *     the content conclusion is not {@code Reject}
     * @throws NegotiationGenerationException with the code {@code template_not_found} or
     *     {@code negotiation_slot_missing} when loading or rendering the template fails
     */
    public MetadataContent generateRejectFromData(
            @NonNull NegotiationEndingData data, @NonNull TemplateUri templateUri) {
        return orchestrator.generateRejectFromData(data, templateUri);
    }

    /**
     * Generates a propose-phase negotiation message from free text through one LLM content-extraction step.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context injected into the rendered message without any LLM involvement
     * @param templateUri template URI whose phase segment must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI contradicts the method
     * @throws NegotiationGenerationException with the code {@code template_not_found},
     *     {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} when loading or
     *     extracting fails, {@code negotiation_slot_missing} when the extracted content misses a required field, or
     *     {@code negotiation_invalid_input} when the text is blank or the extracted content contradicts the phase
     */
    public MetadataContent generateProposeFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return orchestrator.generateProposeFromText(text, context, templateUri);
    }

    /**
     * Generates an accept-phase negotiation message from free text through one LLM content-extraction step.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context injected into the rendered message without any LLM involvement
     * @param templateUri template URI whose phase segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI contradicts the method
     * @throws NegotiationGenerationException with the code {@code template_not_found},
     *     {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} when loading or
     *     extracting fails, {@code negotiation_slot_missing} when the extracted content misses a required field, or
     *     {@code negotiation_invalid_input} when the text is blank or the extracted conclusion is not {@code Accept}
     */
    public MetadataContent generateAcceptFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return orchestrator.generateAcceptFromText(text, context, templateUri);
    }

    /**
     * Generates a reject-phase negotiation message from free text through one LLM content-extraction step.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context injected into the rendered message without any LLM involvement
     * @param templateUri template URI whose phase segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI contradicts the method
     * @throws NegotiationGenerationException with the code {@code template_not_found},
     *     {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} when loading or
     *     extracting fails, {@code negotiation_slot_missing} when the extracted content misses a required field, or
     *     {@code negotiation_invalid_input} when the text is blank or the extracted conclusion is not {@code Reject}
     */
    public MetadataContent generateRejectFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return orchestrator.generateRejectFromText(text, context, templateUri);
    }

    /**
     * Lists every negotiation template available for the configured language; never throws.
     *
     * @return loadable negotiation templates of the configured language, in a fixed type and phase order; empty when
     *     none can be loaded
     */
    public List<PromptTemplate> getNegotiationPrompts() {
        return orchestrator.getNegotiationPrompts();
    }

    /**
     * Loads one negotiation template by its URI; never throws.
     *
     * @param templateUri template URI such as {@code Negotiation-T/target-negotiation/propose/v1}
     * @return the addressed template, or an empty optional when the URI does not address a negotiation template or no
     *     template exists for it in the configured language
     * @throws NullPointerException if the template URI is null
     */
    public Optional<PromptTemplate> getNegotiationPrompt(@NonNull TemplateUri templateUri) {
        return orchestrator.getNegotiationPrompt(templateUri);
    }

    /**
     * Validates a propose-phase negotiation message and extracts its parameters.
     *
     * @param prompt rendered negotiation message text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI whose phase segment must be {@code propose}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the prompt, the schema or the template URI is null
     * @throws IllegalArgumentException if the prompt is blank, or the template URI contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation_invalid_input},
     *     {@code negotiation_rule_violation}, {@code negotiation_semantic_rejected},
     *     {@code negotiation_llm_infrastructure_error} or {@code template_not_found} when the validation pipeline
     *     fails
     */
    public FilledParamData validateAndFillingProposeData(
            String prompt, @NonNull Map<String, Object> schema, @NonNull TemplateUri templateUri) {
        return orchestrator.validateAndFillingProposeData(prompt, schema, templateUri);
    }

    /**
     * Validates an accept-phase negotiation message and extracts its parameters.
     *
     * @param prompt rendered negotiation message text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI whose phase segment must be {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the prompt, the schema or the template URI is null
     * @throws IllegalArgumentException if the prompt is blank, or the template URI contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation_invalid_input},
     *     {@code negotiation_rule_violation}, {@code negotiation_semantic_rejected},
     *     {@code negotiation_llm_infrastructure_error} or {@code template_not_found} when the validation pipeline
     *     fails
     */
    public FilledParamData validateAndFillingAcceptData(
            String prompt, @NonNull Map<String, Object> schema, @NonNull TemplateUri templateUri) {
        return orchestrator.validateAndFillingAcceptData(prompt, schema, templateUri);
    }

    /**
     * Validates a reject-phase negotiation message and extracts its parameters.
     *
     * @param prompt rendered negotiation message text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI whose phase segment must be {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the prompt, the schema or the template URI is null
     * @throws IllegalArgumentException if the prompt is blank, or the template URI contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation_invalid_input},
     *     {@code negotiation_rule_violation}, {@code negotiation_semantic_rejected},
     *     {@code negotiation_llm_infrastructure_error} or {@code template_not_found} when the validation pipeline
     *     fails
     */
    public FilledParamData validateAndFillingRejectData(
            String prompt, @NonNull Map<String, Object> schema, @NonNull TemplateUri templateUri) {
        return orchestrator.validateAndFillingRejectData(prompt, schema, templateUri);
    }
}
