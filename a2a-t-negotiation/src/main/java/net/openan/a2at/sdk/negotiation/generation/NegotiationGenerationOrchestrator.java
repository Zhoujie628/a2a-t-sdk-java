package net.openan.a2at.sdk.negotiation.generation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.A2ATParamExtractionError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContentException;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.validation.ParamExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the negotiation content layer: deterministic message generation, single-step LLM content extraction and
 * the validation-plus-parameter-extraction pipeline.
 *
 * <p>The orchestrator owns the retry loop of every LLM step. A step failing with one of the retryable codes
 * {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} is re-run up to the
 * configured attempt limit; the exhaustion failure rethrows the original error code. Internal exceptions of the
 * generation and validation pipelines never bubble out raw: render failures become generation failures carrying
 * {@code negotiation_slot_missing}, semantic validation infrastructure failures become parameter-extraction failures
 * carrying {@code negotiation_llm_infrastructure_error}, and resource load misses become the code
 * {@code template_not_found} on both pipelines.
 *
 * <p>Instances are created through {@link NegotiationGenerationOrchestratorBuilder}; the builder wires the default
 * collaborators and allows overriding each of them.
 *
 * @since 2026-06
 */
public final class NegotiationGenerationOrchestrator {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(NegotiationGenerationOrchestrator.class);

    private static final String STEP_CONTENT_EXTRACT = "negotiation_content_extract";

    private static final String LANGUAGE_HINT =
            "set A2AT_LANGUAGE to a language with bundled templates (zh-CN or en-US) or provide the template under"
                    + " the local resource root";

    private final String language;

    private final int maxAttempts;

    private final NegotiationTemplateLoader templateLoader;

    private final NegotiationContentExtractor contentExtractor;

    private final ParamExtractor paramExtractor;

    private final NegotiationGeneratorRegistry generatorRegistry;

    private final Vocabulary vocabulary;

    private final Logger logger;

    NegotiationGenerationOrchestrator(
            String language,
            int maxAttempts,
            NegotiationTemplateLoader templateLoader,
            NegotiationContentExtractor contentExtractor,
            ParamExtractor paramExtractor,
            NegotiationGeneratorRegistry generatorRegistry,
            Vocabulary vocabulary,
            Logger logger) {
        this.language = language;
        this.maxAttempts = maxAttempts;
        this.templateLoader = templateLoader;
        this.contentExtractor = contentExtractor;
        this.paramExtractor = paramExtractor;
        this.generatorRegistry = generatorRegistry;
        this.vocabulary = vocabulary;
        this.logger = logger == null ? DEFAULT_LOGGER : logger;
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
     * @throws NegotiationContentException if the data or its context is null, the template URI is malformed, or its
     *     phase or type contradicts the method or the content type
     * @throws NegotiationGenerationException with the code {@code template_not_found} when no template exists for the
     *     URI in any resource root, or the code {@code negotiation_slot_missing} when rendering the template fails
     */
    public MetadataContent generateProposeFromData(NegotiationProposeData data, String templateUri) {
        if (data == null) {
            throw new NegotiationContentException("Negotiation propose data must not be null.", "data");
        }
        return generateFromData(data.context(), data.content(), templateUri, NegotiationPhase.PROPOSE);
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
     * @throws NegotiationContentException if the data or its context is null, the template URI is malformed, its phase
     *     or type contradicts the method or the content, or the content conclusion is not {@code Accept}
     * @throws NegotiationGenerationException with the code {@code template_not_found} when no template exists for the
     *     URI in any resource root, or the code {@code negotiation_slot_missing} when rendering the template fails
     */
    public MetadataContent generateAcceptFromData(NegotiationEndingData data, String templateUri) {
        if (data == null) {
            throw new NegotiationContentException("Negotiation ending data must not be null.", "data");
        }
        return generateFromData(data.context(), data.content(), templateUri, NegotiationPhase.ACCEPT);
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
     * @throws NegotiationContentException if the data or its context is null, the template URI is malformed, its phase
     *     or type contradicts the method or the content, or the content conclusion is not {@code Reject}
     * @throws NegotiationGenerationException with the code {@code template_not_found} when no template exists for the
     *     URI in any resource root, or the code {@code negotiation_slot_missing} when rendering the template fails
     */
    public MetadataContent generateRejectFromData(NegotiationEndingData data, String templateUri) {
        if (data == null) {
            throw new NegotiationContentException("Negotiation ending data must not be null.", "data");
        }
        return generateFromData(data.context(), data.content(), templateUri, NegotiationPhase.REJECT);
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
     * @throws NegotiationContentException if the context is null or the template URI is malformed or its phase
     *     contradicts the method
     * @throws NegotiationGenerationException with the code {@code template_not_found} when no template or prompt
     *     resource exists for the URI and language, {@code negotiation_content_extract_failed} or
     *     {@code negotiation_llm_infrastructure_error} when the extraction step fails after exhausting its retries,
     *     {@code negotiation_slot_missing} when the extracted content misses a required field, or
     *     {@code negotiation_invalid_input} when the text is blank or the extracted content contradicts the phase
     */
    public MetadataContent generateProposeFromText(String text, NegotiationContext context, String templateUri) {
        return generateFromText(text, context, templateUri, NegotiationPhase.PROPOSE);
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
     * @throws NegotiationContentException if the context is null or the template URI is malformed or its phase
     *     contradicts the method
     * @throws NegotiationGenerationException with the code {@code template_not_found} when no template or prompt
     *     resource exists for the URI and language, {@code negotiation_content_extract_failed} or
     *     {@code negotiation_llm_infrastructure_error} when the extraction step fails after exhausting its retries,
     *     {@code negotiation_slot_missing} when the extracted content misses a required field, or
     *     {@code negotiation_invalid_input} when the text is blank or the extracted conclusion is not {@code Accept}
     */
    public MetadataContent generateAcceptFromText(String text, NegotiationContext context, String templateUri) {
        return generateFromText(text, context, templateUri, NegotiationPhase.ACCEPT);
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
     * @throws NegotiationContentException if the context is null or the template URI is malformed or its phase
     *     contradicts the method
     * @throws NegotiationGenerationException with the code {@code template_not_found} when no template or prompt
     *     resource exists for the URI and language, {@code negotiation_content_extract_failed} or
     *     {@code negotiation_llm_infrastructure_error} when the extraction step fails after exhausting its retries,
     *     {@code negotiation_slot_missing} when the extracted content misses a required field, or
     *     {@code negotiation_invalid_input} when the text is blank or the extracted conclusion is not {@code Reject}
     */
    public MetadataContent generateRejectFromText(String text, NegotiationContext context, String templateUri) {
        return generateFromText(text, context, templateUri, NegotiationPhase.REJECT);
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
        try {
            return templateLoader.loadAll();
        } catch (ResourceNotFoundException exception) {
            logger.atWarn().log("negotiation_template_not_found uri=all language={} hint={}", language, LANGUAGE_HINT);
            return List.of();
        }
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
        Optional<NegotiationReference> reference = parseQueryReference(templateUri);
        if (reference.isEmpty()) {
            logger.atWarn()
                    .log(
                            "negotiation_template_not_found uri={} language={} reason=invalid_template_uri hint={}",
                            templateUri,
                            language,
                            LANGUAGE_HINT);
            return Optional.empty();
        }
        try {
            return Optional.of(templateLoader.load(reference.get()));
        } catch (ResourceNotFoundException exception) {
            logger.atWarn()
                    .log(
                            "negotiation_template_not_found uri={} language={} hint={}",
                            templateUri,
                            language,
                            LANGUAGE_HINT);
            return Optional.empty();
        }
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
     * @throws NegotiationContentException if the schema is null or the template URI is malformed or its phase
     *     contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation_invalid_input} when the prompt is
     *     not a negotiation message, {@code negotiation_rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation_semantic_rejected} when the semantic validation rejects the message,
     *     {@code negotiation_llm_infrastructure_error} when the semantic step fails after exhausting its retries, or
     *     {@code template_not_found} when the semantic validation prompt resources are missing
     */
    public FilledParamData validateAndFillingProposeData(
            String prompt, Map<String, Object> schema, String templateUri) {
        return validateAndFilling(prompt, schema, templateUri, NegotiationPhase.PROPOSE);
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
     * @throws NegotiationContentException if the schema is null or the template URI is malformed or its phase
     *     contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation_invalid_input} when the prompt is
     *     not a negotiation message, {@code negotiation_rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation_semantic_rejected} when the semantic validation rejects the message,
     *     {@code negotiation_llm_infrastructure_error} when the semantic step fails after exhausting its retries, or
     *     {@code template_not_found} when the semantic validation prompt resources are missing
     */
    public FilledParamData validateAndFillingAcceptData(String prompt, Map<String, Object> schema, String templateUri) {
        return validateAndFilling(prompt, schema, templateUri, NegotiationPhase.ACCEPT);
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
     * @throws NegotiationContentException if the schema is null or the template URI is malformed or its phase
     *     contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation_invalid_input} when the prompt is
     *     not a negotiation message, {@code negotiation_rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation_semantic_rejected} when the semantic validation rejects the message,
     *     {@code negotiation_llm_infrastructure_error} when the semantic step fails after exhausting its retries, or
     *     {@code template_not_found} when the semantic validation prompt resources are missing
     */
    public FilledParamData validateAndFillingRejectData(String prompt, Map<String, Object> schema, String templateUri) {
        return validateAndFilling(prompt, schema, templateUri, NegotiationPhase.REJECT);
    }

    private MetadataContent generateFromData(
            NegotiationContext context, NegotiationContent content, String templateUri, NegotiationPhase phase) {
        requireContext(context);
        try {
            NegotiationReference reference = NegotiationReference.parse(templateUri, phase, language);
            PromptTemplate template = loadTemplate(reference);
            String promptText = renderMessage(context, content, reference, template);
            return completeGeneration(reference, promptText, context);
        } catch (NegotiationGenerationException failure) {
            logger.atWarn()
                    .log("negotiation_generation_failed code={} template_uri={}", failure.getCode(), templateUri);
            throw failure;
        }
    }

    private MetadataContent generateFromText(
            String text, NegotiationContext context, String templateUri, NegotiationPhase phase) {
        requireContext(context);
        try {
            NegotiationReference reference = NegotiationReference.parse(templateUri, phase, language);
            PromptTemplate template = loadTemplate(reference);
            NegotiationContent content = extractContent(text, reference);
            String promptText = renderMessage(context, content, reference, template);
            return completeGeneration(reference, promptText, context);
        } catch (NegotiationGenerationException failure) {
            logger.atWarn()
                    .log("negotiation_generation_failed code={} template_uri={}", failure.getCode(), templateUri);
            throw failure;
        }
    }

    private PromptTemplate loadTemplate(NegotiationReference reference) {
        try {
            return templateLoader.load(reference);
        } catch (ResourceNotFoundException exception) {
            throw new NegotiationGenerationException(
                    A2ATErrorCodes.TEMPLATE_NOT_FOUND, exception.getMessage(), exception);
        }
    }

    private NegotiationContent extractContent(String text, NegotiationReference reference) {
        try {
            return withRetry(STEP_CONTENT_EXTRACT, () -> contentExtractor.extract(text, reference));
        } catch (ResourceNotFoundException exception) {
            throw new NegotiationGenerationException(
                    A2ATErrorCodes.TEMPLATE_NOT_FOUND, exception.getMessage(), exception);
        }
    }

    private String renderMessage(
            NegotiationContext context,
            NegotiationContent content,
            NegotiationReference reference,
            PromptTemplate template) {
        NegotiationGenerator generator = generatorRegistry.resolve(reference.type(), reference.phase(), content);
        try {
            return generator.generate(context, content, template, vocabulary);
        } catch (NegotiationRenderException exception) {
            throw new NegotiationGenerationException(
                    A2ATErrorCodes.NEGOTIATION_SLOT_MISSING,
                    "Rendering the negotiation template failed: " + exception.getMessage(),
                    exception);
        }
    }

    private MetadataContent completeGeneration(
            NegotiationReference reference, String promptText, NegotiationContext context) {
        logger.atInfo().log(
                "negotiation_generation_completed uri={} type={} phase={} round={} id={}",
                reference.uri(),
                reference.type(),
                reference.phase(),
                context.round(),
                context.id());
        return new MetadataContent(
                reference.uri(), promptText, ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI);
    }

    private FilledParamData validateAndFilling(
            String prompt, Map<String, Object> schema, String templateUri, NegotiationPhase phase) {
        if (schema == null) {
            throw new NegotiationContentException("Parameter schema must not be null.", "schema");
        }
        NegotiationReference reference = NegotiationReference.parse(templateUri, phase, language);
        try {
            return paramExtractor.extract(prompt, schema, reference);
        } catch (NegotiationParamExtractionException failure) {
            logger.atWarn()
                    .log(
                            "negotiation_param_extraction_failed code={} error_count={}",
                            failure.getCode(),
                            failure.getErrors().size());
            throw failure;
        }
    }

    private Optional<NegotiationReference> parseQueryReference(String templateUri) {
        // The URI layer cannot distinguish accept from reject because both phases share the accept-reject template
        // segment. The query therefore addresses the shared template through the ACCEPT phase; the parsed phase is an
        // addressing artifact only and must never be read as the phase of any message.
        for (NegotiationPhase phase : List.of(NegotiationPhase.PROPOSE, NegotiationPhase.ACCEPT)) {
            try {
                return Optional.of(NegotiationReference.parse(templateUri, phase, language));
            } catch (NegotiationContentException exception) {
                // The next candidate phase may still match the URI segment.
            }
        }
        return Optional.empty();
    }

    /**
     * Runs one LLM step with the retry policy of the negotiation content layer.
     *
     * <p>A failure carrying a retryable code is re-run up to the configured attempt limit; a failure carrying any other
     * code is rethrown immediately. When the attempts are exhausted, the original failure is rethrown with its original
     * error code.
     *
     * @param <T> result type of the step
     * @param step internal diagnostic step name used in the retry logs
     * @param action step implementation performing exactly one LLM call
     * @return step result
     */
    private <T> T withRetry(String step, Supplier<T> action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException failure) {
                String code = retryableCode(failure);
                if (code == null) {
                    throw failure;
                }
                lastFailure = failure;
                if (attempt == maxAttempts) {
                    logger.atWarn()
                            .log(
                                    "negotiation_llm_retry_exhausted step={} max_attempts={} code={}",
                                    step,
                                    maxAttempts,
                                    code);
                    throw failure;
                }
                logger.atWarn()
                        .log(
                                "negotiation_llm_retry step={} attempt={} max_attempts={} code={}",
                                step,
                                attempt,
                                maxAttempts,
                                code);
            }
        }
        throw lastFailure;
    }

    private static String retryableCode(RuntimeException failure) {
        String code = null;
        if (failure instanceof NegotiationGenerationException generation) {
            code = generation.getCode();
        } else if (failure instanceof A2ATParamExtractionError extraction) {
            code = extraction.getCode();
        }
        if (code == null) {
            return null;
        }
        boolean retryable = A2ATErrorCodes.NEGOTIATION_CONTENT_EXTRACT_FAILED.equals(code)
                || A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR.equals(code);
        return retryable ? code : null;
    }

    private static void requireContext(NegotiationContext context) {
        if (context == null) {
            throw new NegotiationContentException("Negotiation context must not be null.", "context");
        }
    }
}
