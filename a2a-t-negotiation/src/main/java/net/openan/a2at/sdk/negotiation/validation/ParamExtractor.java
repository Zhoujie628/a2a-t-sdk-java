package net.openan.a2at.sdk.negotiation.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.negotiation.content.FilledParamData;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the validation of a negotiation message and the extraction of its parameters.
 *
 * <p>The extractor runs the rule-level compliance gate first, so a text that is not a negotiation message or whose
 * negotiation context violates a rule fails before any LLM call is made. The LLM-backed semantic validation is
 * intentionally split from the deterministic completion so that the orchestration layer can own the retry loop:
 *
 * <ol>
 *   <li>call {@link #runSemanticValidation(String, Map, NegotiationReference)} inside the retry loop; it re-runs the
 *       rule gate, performs exactly one semantic validation call and reports failures as typed parameter-extraction
 *       failures whose codes decide retryability ({@code negotiation_llm_infrastructure_error} is retryable,
 *       {@code template_not_found} is not),
 *   <li>pass the obtained {@link SemanticValidationResult} to {@link #extract(String, NegotiationReference,
 *       SemanticValidationResult)}, which re-runs the rule gate, rejects a negative semantic verdict and merges the
 *       parameters deterministically.
 * </ol>
 *
 * <p>Parameter merging writes the context parameters (id as a string, round and maxRounds as integers) first and the
 * semantically extracted parameters second; on a key conflict the context parameter wins and a warning is logged, so
 * the LLM output can never override the rule-level parsed values.
 *
 * @since 2026-06
 */
public final class ParamExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParamExtractor.class);

    private static final String NON_NEGOTIATION_MESSAGE =
            "missing negotiation context section; for Task-T compliance use checkTaskPrompt";

    private final NegotiationComplianceChecker complianceChecker;

    private final NegotiationSemanticValidator semanticValidator;

    private final Vocabulary vocabulary;

    /**
     * Creates a parameter extractor.
     *
     * @param complianceChecker rule-level compliance checker used as the entry gate
     * @param semanticValidator LLM-backed semantic validator producing the semantic verdict and extracted parameters
     * @param vocabulary vocabulary of the message language, passed to the compliance checker
     * @throws NullPointerException if any collaborator is null
     */
    public ParamExtractor(
            NegotiationComplianceChecker complianceChecker,
            NegotiationSemanticValidator semanticValidator,
            Vocabulary vocabulary) {
        this.complianceChecker = Objects.requireNonNull(complianceChecker, "complianceChecker");
        this.semanticValidator = Objects.requireNonNull(semanticValidator, "semanticValidator");
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
    }

    /**
     * Runs the retryable LLM step of parameter extraction: the rule gate followed by one semantic validation call.
     *
     * <p>Orchestration layers own the retry loop and should wrap this method: a failure with the code
     * {@code negotiation_llm_infrastructure_error} is retryable, any other failure is not. The internal semantic
     * validation exception never bubbles out of this method.
     *
     * @param prompt rendered negotiation message text
     * @param callerSchema caller-provided parameter JSON schema embedded into the structured-call output contract
     * @param reference template reference the message is validated against
     * @return semantic validation outcome carrying the verdict, the implied type, the semantic errors and the extracted
     *     parameters
     * @throws NegotiationParamExtractionException with the code {@code negotiation_invalid_input} when the text is not
     *     a negotiation message, {@code negotiation_rule_violation} when the negotiation context violates a rule
     *     (before any LLM call), {@code negotiation_llm_infrastructure_error} when the LLM invocation fails or the
     *     response violates the output contract, or {@code template_not_found} when the semantic validation prompt
     *     resources are missing
     */
    public SemanticValidationResult runSemanticValidation(
            String prompt, Map<String, Object> callerSchema, NegotiationReference reference) {
        requireValidRuleContext(prompt);
        try {
            return semanticValidator.validate(prompt, callerSchema, reference);
        } catch (NegotiationValidationException exception) {
            throw new NegotiationParamExtractionException(
                    A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR,
                    "Semantic validation LLM step failed: " + exception.getMessage(),
                    List.of(new SlotValidationError(
                            "_llm", A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR, exception.getMessage())));
        } catch (ResourceNotFoundException exception) {
            throw new NegotiationParamExtractionException(
                    A2ATErrorCodes.TEMPLATE_NOT_FOUND, exception.getMessage(), List.of());
        }
    }

    /**
     * Completes parameter extraction deterministically from an already obtained semantic validation outcome.
     *
     * <p>This method performs no LLM call. It re-runs the rule gate to obtain the negotiation context, rejects a
     * negative semantic verdict with the semantic errors passed through unchanged, and merges the context parameters
     * with the semantically extracted parameters.
     *
     * @param prompt rendered negotiation message text
     * @param reference template reference the message is validated against
     * @param semanticResult semantic validation outcome obtained from {@link #runSemanticValidation(String, Map,
     *     NegotiationReference)}
     * @return filled parameter data carrying the merged parameters
     * @throws NullPointerException if the semantic result is null
     * @throws NegotiationParamExtractionException with the code {@code negotiation_invalid_input} when the text is not
     *     a negotiation message, {@code negotiation_rule_violation} when the negotiation context violates a rule, or
     *     {@code negotiation_semantic_rejected} when the semantic verdict is negative, with the semantic errors passed
     *     through
     */
    public FilledParamData extract(
            String prompt, NegotiationReference reference, SemanticValidationResult semanticResult) {
        Objects.requireNonNull(semanticResult, "semanticResult");
        NegotiationContext context = requireValidRuleContext(prompt);
        if (!semanticResult.verdict()) {
            throw new NegotiationParamExtractionException(
                    A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED,
                    "Semantic validation rejected the negotiation message.",
                    semanticResult.errors());
        }
        return new FilledParamData(mergeParams(context, semanticResult.params()));
    }

    private NegotiationContext requireValidRuleContext(String prompt) {
        NegotiationRuleCheckResult ruleResult = complianceChecker.check(prompt, vocabulary);
        if (!ruleResult.isNegotiation()) {
            throw new NegotiationParamExtractionException(
                    A2ATErrorCodes.NEGOTIATION_INVALID_INPUT, NON_NEGOTIATION_MESSAGE, List.of());
        }
        if (!ruleResult.passed()) {
            throw new NegotiationParamExtractionException(
                    A2ATErrorCodes.NEGOTIATION_RULE_VIOLATION,
                    "Negotiation context rule validation failed.",
                    ruleResult.errors());
        }
        return ruleResult.context();
    }

    private static Map<String, Object> mergeParams(NegotiationContext context, Map<String, Object> semanticParams) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("id", context.id());
        merged.put("round", context.round());
        merged.put("maxRounds", context.maxRounds());
        for (Map.Entry<String, Object> entry : semanticParams.entrySet()) {
            String key = entry.getKey();
            if (merged.containsKey(key)) {
                LOGGER.atWarn().log("negotiation_param_merge_conflict key={} resolution=context_param_wins", key);
                continue;
            }
            merged.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(merged);
    }
}
