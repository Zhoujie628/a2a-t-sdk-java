package net.openan.a2at.sdk.server;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.negotiation.content.FilledParamData;
import net.openan.a2at.sdk.negotiation.content.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.resources.PromptTemplate;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import net.openan.a2at.sdk.server.assembly.DefaultA2ATServerBuilder;
import net.openan.a2at.sdk.server.compliance.ServerPromptComplianceOrchestrator;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;

/**
 * High-level server facade for prompt compliance and negotiation APIs. The caller provides the `.env` file path
 * explicitly, typically after copying the repository `env.example`.
 *
 * @since 2026-06
 */
public final class A2ATServer {

    private final ServerPromptComplianceOrchestrator promptComplianceOrchestrator;

    private final RoleBoundNegotiationOrchestrator negotiationOrchestrator;

    private final NegotiationGenerationOrchestrator negotiationGenerationOrchestrator;

    /**
     * Creates a server facade from one user-supplied `.env` path.
     *
     * @param envPath user-supplied `.env` file path
     */
    public A2ATServer(Path envPath) {
        Path resolvedEnvPath = envPath.toAbsolutePath().normalize();
        A2ATConfig config = resolvePromptResourceLocalRootDir(A2ATConfig.load(resolvedEnvPath), resolvedEnvPath);
        DefaultA2ATServerBuilder builder =
                DefaultA2ATServerBuilder.builder().envPath(resolvedEnvPath).config(config);
        this.promptComplianceOrchestrator = builder.buildPromptComplianceOrchestrator();
        this.negotiationOrchestrator = builder.buildNegotiationOrchestrator();
        this.negotiationGenerationOrchestrator = builder.buildNegotiationGenerationOrchestrator();
    }

    /**
     * Checks one processed task prompt for server-side compliance.
     *
     * @param processedPromptText processed task prompt text
     * @return compliance result containing only success state or failure details
     */
    public PromptComplianceResult checkTaskPrompt(String processedPromptText) {
        return promptComplianceOrchestrator.checkTaskPrompt(processedPromptText);
    }

    /**
     * Starts a new negotiation payload for the requested negotiation type.
     *
     * @param type negotiation type to initiate
     * @param contentText human-readable negotiation message
     * @param facts structured facts attached to the payload
     * @return transport payload representing the initial negotiation turn
     */
    public Map<String, Object> startNegotiation(NegotiationType type, String contentText, Map<String, Object> facts) {
        return negotiationOrchestrator.startNegotiation(type, contentText, facts);
    }

    /**
     * Processes a received negotiation message using its transport context payload.
     *
     * @param message received negotiation message
     * @param context transport context payload associated with the message
     * @return normalized payload describing the receive result
     */
    public Map<String, Object> receiveNegotiation(String message, Map<String, Object> context) {
        return negotiationOrchestrator.receiveNegotiation(message, context);
    }

    /**
     * Continues an existing negotiation with a locally stored context snapshot.
     *
     * @param context current negotiation context
     * @param status next status to emit
     * @param contentText continuation message content
     * @return transport payload representing the next negotiation turn
     */
    public Map<String, Object> continueNegotiation(
            NegotiationContext context, NegotiationStatus status, String contentText) {
        return negotiationOrchestrator.continueNegotiation(context, status, contentText);
    }

    /**
     * Generates a propose-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM: the typed content is validated, dispatched to the
     * generator of the negotiation type addressed by the template URI and rendered from that template.
     *
     * @param data typed propose input carrying the negotiation context and the typed content
     * @param templateUri template URI such as {@code Negotiation-T/v1/information-negotiation/propose}; its phase
     *     segment must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationContentException if the data or its context is null,
     *     the template URI is malformed, or its phase or type contradicts the method or the content type
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template exists for the URI in any resource root, or the code
     *     {@code negotiation_slot_missing} when rendering the template fails
     */
    public MetadataContent generateNegotiationProposePromptFromData(NegotiationProposeData data, String templateUri) {
        return negotiationGenerationOrchestrator.generateProposeFromData(data, templateUri);
    }

    /**
     * Generates an accept-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. The content conclusion must be {@code Accept}; a
     * mismatched conclusion is a content error.
     *
     * @param data typed terminal input carrying the negotiation context and the typed ending content
     * @param templateUri template URI such as {@code Negotiation-T/v1/information-negotiation/accept-reject}; its phase
     *     segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationContentException if the data or its context is null,
     *     the template URI is malformed, its phase or type contradicts the method or the content, or the content
     *     conclusion is not {@code Accept}
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template exists for the URI in any resource root, or the code
     *     {@code negotiation_slot_missing} when rendering the template fails
     */
    public MetadataContent generateNegotiationAcceptPromptFromData(NegotiationEndingData data, String templateUri) {
        return negotiationGenerationOrchestrator.generateAcceptFromData(data, templateUri);
    }

    /**
     * Generates a reject-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. The content conclusion must be {@code Reject}; a
     * mismatched conclusion is a content error.
     *
     * @param data typed terminal input carrying the negotiation context and the typed ending content
     * @param templateUri template URI such as {@code Negotiation-T/v1/feasibility-negotiation/accept-reject}; its phase
     *     segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationContentException if the data or its context is null,
     *     the template URI is malformed, its phase or type contradicts the method or the content, or the content
     *     conclusion is not {@code Reject}
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template exists for the URI in any resource root, or the code
     *     {@code negotiation_slot_missing} when rendering the template fails
     */
    public MetadataContent generateNegotiationRejectPromptFromData(NegotiationEndingData data, String templateUri) {
        return negotiationGenerationOrchestrator.generateRejectFromData(data, templateUri);
    }

    /**
     * Generates a propose-phase negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the template URI and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extraction step
     * is retried up to the configured attempt limit on the retryable failure codes
     * {@code negotiation_content_extract_failed} and {@code negotiation_llm_infrastructure_error}.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context injected into the rendered message without any LLM involvement
     * @param templateUri template URI such as {@code Negotiation-T/v1/target-negotiation/propose}; its phase segment
     *     must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationContentException if the context is null or the
     *     template URI is malformed or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} when the
     *     extraction step fails after exhausting its retries, {@code negotiation_slot_missing} when the extracted
     *     content misses a required field, or {@code negotiation_invalid_input} when the text is blank or the extracted
     *     content contradicts the phase
     */
    public MetadataContent generateNegotiationProposePromptFromText(
            String text, net.openan.a2at.sdk.negotiation.content.NegotiationContext context, String templateUri) {
        return negotiationGenerationOrchestrator.generateProposeFromText(text, context, templateUri);
    }

    /**
     * Generates an accept-phase negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the template URI and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extracted
     * conclusion must be {@code Accept}.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context injected into the rendered message without any LLM involvement
     * @param templateUri template URI such as {@code Negotiation-T/v1/information-negotiation/accept-reject}; its phase
     *     segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationContentException if the context is null or the
     *     template URI is malformed or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} when the
     *     extraction step fails after exhausting its retries, {@code negotiation_slot_missing} when the extracted
     *     content misses a required field, or {@code negotiation_invalid_input} when the text is blank or the extracted
     *     conclusion is not {@code Accept}
     */
    public MetadataContent generateNegotiationAcceptPromptFromText(
            String text, net.openan.a2at.sdk.negotiation.content.NegotiationContext context, String templateUri) {
        return negotiationGenerationOrchestrator.generateAcceptFromText(text, context, templateUri);
    }

    /**
     * Generates a reject-phase negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the template URI and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extracted
     * conclusion must be {@code Reject}.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context injected into the rendered message without any LLM involvement
     * @param templateUri template URI such as {@code Negotiation-T/v1/feasibility-negotiation/accept-reject}; its phase
     *     segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationContentException if the context is null or the
     *     template URI is malformed or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} when the
     *     extraction step fails after exhausting its retries, {@code negotiation_slot_missing} when the extracted
     *     content misses a required field, or {@code negotiation_invalid_input} when the text is blank or the extracted
     *     conclusion is not {@code Reject}
     */
    public MetadataContent generateNegotiationRejectPromptFromText(
            String text, net.openan.a2at.sdk.negotiation.content.NegotiationContext context, String templateUri) {
        return negotiationGenerationOrchestrator.generateRejectFromText(text, context, templateUri);
    }

    /**
     * Lists every negotiation template available for the configured language.
     *
     * <p>This query never throws: templates that exist nowhere for the language are skipped and an empty list is
     * returned when no template can be loaded at all.
     *
     * @return loadable negotiation templates of the configured language, in a fixed type and phase order; empty when
     *     none can be loaded
     */
    public List<PromptTemplate> getNegotiationPrompts() {
        return negotiationGenerationOrchestrator.getNegotiationPrompts();
    }

    /**
     * Loads one negotiation template addressed by its URI.
     *
     * <p>This query never throws: a malformed URI or a template that exists nowhere for the configured language returns
     * an empty result and logs an actionable warning.
     *
     * @param templateUri template URI such as {@code Negotiation-T/v1/target-negotiation/propose}
     * @return the addressed template, or an empty result when the URI is malformed or the template does not exist for
     *     the configured language
     */
    public Optional<PromptTemplate> getNegotiationPrompt(String templateUri) {
        return negotiationGenerationOrchestrator.getNegotiationPrompt(templateUri);
    }

    /**
     * Validates a propose-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline checks the template URI format before any LLM call, runs the deterministic rule gate, then
     * performs one semantic validation LLM call (retried on the retryable failure codes) and merges the extracted
     * parameters with the rule-level context parameters; context parameters win on conflict.
     *
     * @param prompt rendered negotiation message text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected negotiation type and phase; its phase segment must be
     *     {@code propose}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationContentException if the schema is null or the template
     *     URI is malformed or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation_invalid_input} when the prompt is not a negotiation message,
     *     {@code negotiation_rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation_semantic_rejected} when the semantic validation rejects the message,
     *     {@code negotiation_llm_infrastructure_error} when the semantic step fails after exhausting its retries, or
     *     {@code template_not_found} when the semantic validation prompt resources are missing
     */
    public FilledParamData validateAndFillingProposeData(
            String prompt, Map<String, Object> schema, String templateUri) {
        return negotiationGenerationOrchestrator.validateAndFillingProposeData(prompt, schema, templateUri);
    }

    /**
     * Validates an accept-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateAndFillingProposeData(String, Map, String)} with the expected phase
     * fixed to accept: the template URI must declare the {@code accept-reject} segment and the message must satisfy the
     * accept-phase semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected negotiation type and phase; its phase segment must be
     *     {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationContentException if the schema is null or the template
     *     URI is malformed or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation_invalid_input} when the prompt is not a negotiation message,
     *     {@code negotiation_rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation_semantic_rejected} when the semantic validation rejects the message,
     *     {@code negotiation_llm_infrastructure_error} when the semantic step fails after exhausting its retries, or
     *     {@code template_not_found} when the semantic validation prompt resources are missing
     */
    public FilledParamData validateAndFillingAcceptData(String prompt, Map<String, Object> schema, String templateUri) {
        return negotiationGenerationOrchestrator.validateAndFillingAcceptData(prompt, schema, templateUri);
    }

    /**
     * Validates a reject-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateAndFillingProposeData(String, Map, String)} with the expected phase
     * fixed to reject: the template URI must declare the {@code accept-reject} segment and the message must satisfy the
     * reject-phase semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected negotiation type and phase; its phase segment must be
     *     {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationContentException if the schema is null or the template
     *     URI is malformed or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation_invalid_input} when the prompt is not a negotiation message,
     *     {@code negotiation_rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation_semantic_rejected} when the semantic validation rejects the message,
     *     {@code negotiation_llm_infrastructure_error} when the semantic step fails after exhausting its retries, or
     *     {@code template_not_found} when the semantic validation prompt resources are missing
     */
    public FilledParamData validateAndFillingRejectData(String prompt, Map<String, Object> schema, String templateUri) {
        return negotiationGenerationOrchestrator.validateAndFillingRejectData(prompt, schema, templateUri);
    }

    private A2ATConfig resolvePromptResourceLocalRootDir(A2ATConfig config, Path envPath) {
        String localRootDir = config.prompt().localRootDir();
        Path localRootPath = Path.of(localRootDir);
        Path resolvedLocalRootPath = localRootPath.isAbsolute()
                ? localRootPath.normalize()
                : envPath.getParent().resolve(localRootPath).toAbsolutePath().normalize();
        return new A2ATConfig(
                new net.openan.a2at.sdk.core.model.PromptRuntimeConfig(
                        config.prompt().language(), config.prompt().sourceType(), resolvedLocalRootPath.toString()),
                config.llm(),
                config.negotiation(),
                config.promptCompliance());
    }
}
