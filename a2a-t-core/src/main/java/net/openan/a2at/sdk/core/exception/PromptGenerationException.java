package net.openan.a2at.sdk.core.exception;

import java.util.List;

/**
 * Unified exception type for MetadataContent pipeline failures.
 *
 * <p>The machine-readable error code is carried by the root {@link A2ATError} and is never null.
 *
 * @since 2026-08
 */
public final class PromptGenerationException extends A2ATError {

    private final List<FailedParameter> failedParameters;

    /**
     * Creates an exception with a stable error code and a human-readable message.
     *
     * @param code stable error code
     * @param message human-readable failure description
     * @throws NullPointerException if {@code code} is null
     */
    public PromptGenerationException(String code, String message) {
        super(code, message);
        this.failedParameters = List.of();
    }

    /**
     * Creates an exception with a stable error code, a human-readable message, and
     * a list of failed parameters.
     *
     * @param code stable error code
     * @param message human-readable failure description
     * @param failedParameters list of failed parameters
     * @throws NullPointerException if {@code code} is null
     */
    public PromptGenerationException(String code, String message, List<FailedParameter> failedParameters) {
        super(code, message);
        this.failedParameters = failedParameters == null ? List.of() : List.copyOf(failedParameters);
    }

    /**
     * Creates an exception with a stable error code, a human-readable message, and
     * a root cause.
     *
     * @param code stable error code
     * @param message human-readable failure description
     * @param cause root cause
     * @throws NullPointerException if {@code code} is null
     */
    public PromptGenerationException(String code, String message, Throwable cause) {
        super(code, message, cause);
        this.failedParameters = List.of();
    }

    /**
     * Returns the list of failed parameters, or an empty list when not applicable.
     *
     * @return failed parameters
     */
    public List<FailedParameter> failedParameters() {
        return failedParameters;
    }
}
