package net.openan.a2at.sdk.negotiation.content;

import java.util.List;
import net.openan.a2at.sdk.core.exception.A2ATParamExtractionError;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import org.jspecify.annotations.Nullable;

/**
 * Raised when validating a negotiation message and extracting parameters from it fails.
 *
 * @since 2026-08
 */
public class NegotiationParamExtractionException extends A2ATParamExtractionError {

    /**
     * Creates a negotiation parameter-extraction failure with the default error code and no slot details.
     *
     * @param message failure message
     */
    public NegotiationParamExtractionException(String message) {
        super(message);
    }

    /**
     * Creates a negotiation parameter-extraction failure with one specific error code and slot details.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     */
    public NegotiationParamExtractionException(String code, String message, List<SlotValidationError> errors) {
        super(code, message, errors);
    }

    /**
     * Creates a negotiation parameter-extraction failure with one specific error code, slot details and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     * @param cause root cause of the failure
     */
    public NegotiationParamExtractionException(
            String code, String message, List<SlotValidationError> errors, Throwable cause) {
        super(code, message, errors, cause);
    }
}
