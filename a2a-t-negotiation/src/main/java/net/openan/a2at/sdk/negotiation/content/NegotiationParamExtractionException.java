package net.openan.a2at.sdk.negotiation.content;

import java.util.List;
import net.openan.a2at.sdk.core.exception.A2ATParamExtractionError;
import net.openan.a2at.sdk.core.model.SlotValidationError;

/**
 * Raised when validating a negotiation message and extracting parameters from it fails.
 *
 * @since 2026-06
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
}
