package net.openan.a2at.sdk.negotiation.content;

import net.openan.a2at.sdk.core.exception.A2ATError;

/**
 * Base failure type raised when a negotiation processing step fails at runtime.
 *
 * <p>Each failure carries a machine-readable error code from {@code A2ATErrorCodes} so callers can branch on the
 * failure class without parsing messages.
 *
 * @since 2026-06
 */
public class NegotiationProcessingException extends A2ATError {

    /** Machine-readable error code carried by this failure. */
    private final String code;

    /**
     * Creates a negotiation processing failure with one error code.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     */
    public NegotiationProcessingException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Creates a negotiation processing failure with one error code and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param cause root cause
     */
    public NegotiationProcessingException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Returns the machine-readable error code for this failure.
     *
     * @return error code such as {@code negotiation_content_extract_failed}
     */
    public String getCode() {
        return code;
    }
}
