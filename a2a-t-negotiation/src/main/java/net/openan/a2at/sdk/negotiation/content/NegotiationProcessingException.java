package net.openan.a2at.sdk.negotiation.content;

import net.openan.a2at.sdk.core.exception.A2ATError;

/**
 * Base failure type of the generation branch, raised when a negotiation generation step fails at runtime.
 *
 * <p>Each failure carries a machine-readable error code from {@code A2ATErrorCodes} — inherited from the
 * {@link A2ATError} root — so callers can branch on the failure class without parsing messages. The
 * parameter-extraction branch ({@code NegotiationParamExtractionException}) is deliberately not a subtype; catch
 * {@link A2ATError} for full negotiation failure coverage.
 *
 * @since 2026-08
 */
public class NegotiationProcessingException extends A2ATError {

    /**
     * Creates a negotiation processing failure with one error code.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     */
    public NegotiationProcessingException(String code, String message) {
        super(code, message);
    }

    /**
     * Creates a negotiation processing failure with one error code and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param cause root cause
     */
    public NegotiationProcessingException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
