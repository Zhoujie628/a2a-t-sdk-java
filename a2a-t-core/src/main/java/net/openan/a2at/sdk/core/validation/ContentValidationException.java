package net.openan.a2at.sdk.core.validation;

import java.util.List;
import net.openan.a2at.sdk.core.exception.SdkException;
import net.openan.a2at.sdk.core.model.SlotValidationError;

/**
 * Failure raised by the content validation pipeline.
 *
 * <p>Carries a machine-readable error code and optionally structured per-slot validation error details so callers can
 * inspect which slot failed, under which error code, and why, without parsing exception messages.
 *
 * @since 2026-08
 */
public class ContentValidationException extends SdkException {

    private final String code;

    private final List<SlotValidationError> errors;

    /**
     * Creates a content validation failure with an error code and one message.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     */
    public ContentValidationException(String code, String message) {
        this(code, message, List.of());
    }

    /**
     * Creates a content validation failure with an error code, one message and slot details.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     */
    public ContentValidationException(String code, String message, List<SlotValidationError> errors) {
        this(code, message, errors, null);
    }

    /**
     * Creates a content validation failure with an error code, one message and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param cause root cause
     */
    public ContentValidationException(String code, String message, Throwable cause) {
        this(code, message, List.of(), cause);
    }

    /**
     * Creates a content validation failure with an error code, one message, slot details and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     * @param cause root cause
     */
    public ContentValidationException(String code, String message, List<SlotValidationError> errors, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Returns the machine-readable error code for this failure.
     *
     * @return error code
     */
    public String code() {
        return code;
    }

    /**
     * Returns the structured per-slot validation error details.
     *
     * @return immutable list of slot validation errors, never null
     */
    public List<SlotValidationError> errors() {
        return errors;
    }
}