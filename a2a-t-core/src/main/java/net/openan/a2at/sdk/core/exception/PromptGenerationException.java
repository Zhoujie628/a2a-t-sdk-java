package net.openan.a2at.sdk.core.exception;

import java.util.List;
import java.util.Objects;

/**
 * Unified exception type for MetadataContent pipeline failures.
 *
 * @since 2026-08
 */
public final class PromptGenerationException extends SdkException {

    private final String code;

    private final List<FailedParameter> failedParameters;

    /**
     * Creates an exception with a stable error code and a human-readable message.
     *
     * @param code stable error code
     * @param message human-readable failure description
     */
    public PromptGenerationException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.failedParameters = List.of();
    }

    /**
     * Creates an exception with a stable error code, a human-readable message, and
     * a list of failed parameters.
     *
     * @param code stable error code
     * @param message human-readable failure description
     * @param failedParameters list of failed parameters
     */
    public PromptGenerationException(String code, String message, List<FailedParameter> failedParameters) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.failedParameters = failedParameters == null ? List.of() : List.copyOf(failedParameters);
    }

    /**
     * Creates an exception with a stable error code, a human-readable message, and
     * a root cause.
     *
     * @param code stable error code
     * @param message human-readable failure description
     * @param cause root cause
     */
    public PromptGenerationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.failedParameters = List.of();
    }

    /**
     * Returns the stable error code.
     *
     * @return error code
     */
    public String code() {
        return code;
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