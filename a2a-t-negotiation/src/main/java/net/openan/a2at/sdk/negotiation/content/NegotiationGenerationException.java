package net.openan.a2at.sdk.negotiation.content;

/**
 * Raised when generating a negotiation message fails at runtime.
 *
 * @since 2026-06
 */
public class NegotiationGenerationException extends NegotiationProcessingException {

    /**
     * Creates a negotiation generation failure with one error code.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     */
    public NegotiationGenerationException(String code, String message) {
        super(code, message);
    }

    /**
     * Creates a negotiation generation failure with one error code and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param cause root cause
     */
    public NegotiationGenerationException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
