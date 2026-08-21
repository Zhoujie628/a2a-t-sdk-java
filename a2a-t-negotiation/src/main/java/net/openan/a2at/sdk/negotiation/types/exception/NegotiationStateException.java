package net.openan.a2at.sdk.negotiation.types.exception;

import net.openan.a2at.sdk.core.exception.A2ATError;

/**
 * Raised when incoming negotiation state skips or contradicts local progress.
 *
 * <p>Carries the default error code {@code sdk_internal_error} from the {@link A2ATError} root.
 *
 * @since 2026-06
 */
public final class NegotiationStateException extends A2ATError {

    /**
     * Creates a negotiation state failure with one message.
     *
     * @param message failure message
     */
    public NegotiationStateException(String message) {
        super(message);
    }
}
