package net.openan.a2at.sdk.core.exception;

/**
 * Common root exception type for all A2A-T processing failures.
 *
 * <p>Callers can catch this single root type to handle any processing failure raised by the SDK across generation and
 * parameter-extraction flows.
 *
 * @since 2026-06
 */
public class A2ATError extends RuntimeException {

    /**
     * Creates an A2A-T processing failure with one message.
     *
     * @param message failure message
     */
    public A2ATError(String message) {
        super(message);
    }

    /**
     * Creates an A2A-T processing failure with one message and root cause.
     *
     * @param message failure message
     * @param cause root cause
     */
    public A2ATError(String message, Throwable cause) {
        super(message, cause);
    }
}
