package net.openan.a2at.sdk.core.exception;

import java.util.List;
import net.openan.a2at.sdk.core.model.SlotValidationError;

/**
 * Shared failure type raised when validating a prompt and extracting parameters from it fails.
 *
 * <p>This base type is shared across prompt families so that a caller can handle extraction failures uniformly.
 * Subclasses carry a more specific error code when one is available.
 *
 * @since 2026-06
 */
public class A2ATParamExtractionError extends A2ATError {

    /** Error code carried by this failure. */
    private final String code;

    /** Structured per-slot validation error details. */
    private final List<SlotValidationError> errors;

    /**
     * Creates a parameter-extraction failure with the default error code and no slot details.
     *
     * @param message failure message
     */
    public A2ATParamExtractionError(String message) {
        this(A2ATErrorCodes.PARAM_EXTRACTION_FAILED, message, List.of());
    }

    /**
     * Creates a parameter-extraction failure with one specific error code and slot details.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     */
    public A2ATParamExtractionError(String code, String message, List<SlotValidationError> errors) {
        super(message);
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    /**
     * Returns the machine-readable error code for this failure.
     *
     * @return error code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the structured per-slot validation error details.
     *
     * @return immutable list of slot validation errors, never null
     */
    public List<SlotValidationError> getErrors() {
        return errors;
    }
}
