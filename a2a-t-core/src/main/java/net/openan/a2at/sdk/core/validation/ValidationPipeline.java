package net.openan.a2at.sdk.core.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the content validation pipeline: input validation, rule-level gate, retryable semantic validation and
 * deterministic parameter merging.
 *
 * <p>Parameter merging writes the context parameters first and the semantically extracted parameters second; on a key
 * conflict the context parameter wins and a warning is logged, so the LLM output can never override the rule-level
 * parsed values.
 *
 * @param <T> template addressing type the validation is performed against, such as {@link TemplateUri}
 * @since 2026-08
 */
public final class ValidationPipeline<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationPipeline.class);

    private final RuleChecker ruleChecker;

    private final SemanticValidator<T> semanticValidator;

    private final int maxAttempts;

    /**
     * Creates a validation pipeline.
     *
     * @param ruleChecker rule-level checker used as the entry gate
     * @param semanticValidator LLM-backed semantic validator producing the semantic verdict and extracted parameters
     * @param maxAttempts maximum number of retry attempts for the semantic validation step
     * @throws NullPointerException if the rule checker or the semantic validator is null
     */
    public ValidationPipeline(
            @NonNull RuleChecker ruleChecker, @NonNull SemanticValidator<T> semanticValidator, int maxAttempts) {
        this.ruleChecker = Objects.requireNonNull(ruleChecker, "ruleChecker");
        this.semanticValidator = Objects.requireNonNull(semanticValidator, "semanticValidator");
        this.maxAttempts = maxAttempts;
    }

    /**
     * Validates one content prompt and extracts its filled parameters through the full pipeline.
     *
     * @param prompt content prompt text
     * @param schema caller-provided parameter JSON schema
     * @param reference template addressing value the content is validated against
     * @return filled parameter data carrying the merged parameters
     * @throws NullPointerException if the prompt, schema or reference is null
     * @throws IllegalArgumentException if the prompt is blank
     * @throws ContentValidationException if the validation fails at any stage
     */
    public @NonNull FilledParamData validate(
            @NonNull String prompt, @NonNull Map<String, Object> schema, @NonNull T reference) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(prompt, "prompt");
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be blank.");
        }
        Objects.requireNonNull(reference, "reference");

        Map<String, Object> contextParams = ruleChecker.check(prompt);

        ValidationResult semanticResult;
        try {
            semanticResult = RetryUtil.withRetry(
                    maxAttempts,
                    "semantic_validation",
                    () -> semanticValidator.validate(prompt, schema, reference));
        } catch (ResourceNotFoundException exception) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_PROMPT_RESOURCE_NOT_FOUND, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR,
                    exception.getMessage(),
                    List.of(new SlotValidationError(
                            "_llm", A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR, exception.getMessage())),
                    exception);
        }

        if (!semanticResult.verdict()) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_SEMANTIC_REJECTED,
                    "Semantic validation rejected the content.",
                    semanticResult.errors());
        }

        Map<String, Object> merged = mergeParams(contextParams, semanticResult.params());
        FilledParamData filledParamData = new FilledParamData(merged);
        LOGGER.atInfo().log("content_validation_completed param_keys={}", filledParamData.data().keySet());
        return filledParamData;
    }

    private static Map<String, Object> mergeParams(Map<String, Object> contextParams, Map<String, Object> semanticParams) {
        Map<String, Object> merged = new LinkedHashMap<>(contextParams);
        for (Map.Entry<String, Object> entry : semanticParams.entrySet()) {
            String key = entry.getKey();
            if (merged.containsKey(key)) {
                LOGGER.atWarn().log("content_param_merge_conflict key={} resolution=context_param_wins", key);
                continue;
            }
            merged.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(merged);
    }
}