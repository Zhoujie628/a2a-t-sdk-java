package net.openan.a2at.sdk.core.validation;

import java.util.function.Supplier;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight retry utility for the content validation pipeline.
 *
 * <p>Only retries on {@link ContentValidationException} failures with the code
 * {@link A2ATErrorCodes#VALIDATION_LLM_INFRASTRUCTURE_ERROR}. All other failure codes and unknown runtime
 * exceptions are rethrown immediately.
 *
 * @since 2026-08
 */
public final class RetryUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryUtil.class);

    private RetryUtil() {}

    /**
     * Executes one action with retry on LLM infrastructure errors.
     *
     * @param <T> result type
     * @param maxAttempts maximum number of attempts, must be at least 1
     * @param stepName name of the step for logging purposes
     * @param action the action to execute
     * @return the result of the action
     * @throws ContentValidationException if the action fails on every attempt with a retryable error, or if it fails
     *     with a non-retryable error
     */
    public static <T> T withRetry(int maxAttempts, String stepName, Supplier<T> action) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (ContentValidationException exception) {
                if (!A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR.equals(exception.getCode())) {
                    throw exception;
                }
                if (attempt == maxAttempts) {
                    LOGGER.atWarn().log(
                            "{}_retry_exhausted attempts={} last_error={}", stepName, attempt, exception.getMessage());
                    throw exception;
                }
                LOGGER.atWarn().log(
                        "{}_retry_attempt attempt={}/{} error={}",
                        stepName,
                        attempt,
                        maxAttempts,
                        exception.getMessage());
            } catch (RuntimeException exception) {
                throw exception;
            }
        }
        throw new IllegalStateException("unreachable");
    }
}