package net.openan.a2at.sdk.core.validation;

import java.util.List;
import java.util.Map;

import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.model.SlotValidationError;

/**
 * Failure raised by the content validation pipeline.
 *
 * <p>Carries the machine-readable error code inherited from {@link A2ATError} and optionally structured per-slot
 * validation error details so callers can inspect which slot failed, under which error code, and why, without parsing
 * exception messages.
 *
 * @since 2026-08
 */
public class ContentValidationException extends A2ATError {

    private final List<SlotValidationError> errors;

    private final Map<String, Object> params;

    /**
     * Creates a content validation failure with an error code and one message.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     */
    public ContentValidationException(String code, String message) {
        this(code, message, List.of(), Map.of(), null);
    }

    /**
     * Creates a content validation failure with an error code, one message and slot details.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     */
    public ContentValidationException(String code, String message, List<SlotValidationError> errors) {
        this(code, message, errors, Map.of(), null);
    }

    /**
     * Creates a content validation failure with an error code, one message and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param cause root cause
     */
    public ContentValidationException(String code, String message, Throwable cause) {
        this(code, message, List.of(), Map.of(), cause);
    }

    public ContentValidationException(String code, String message, List<SlotValidationError> errors, Throwable cause) {
        this(code, message, errors, Map.of(), cause);
    }

    /**
     * Creates a content validation failure with an error code, one message, slot details and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     * @param cause root cause
     */
    public ContentValidationException(
            String code, String message, List<SlotValidationError> errors, Map<String, Object> params, Throwable cause) {
        super(code, message, cause);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.params = params == null ? Map.of() : Map.copyOf(params);
    }

    /**
     * Returns the structured per-slot validation error details.
     *
     * @return immutable list of slot validation errors, never null
     */
    public List<SlotValidationError> errors() {
        return errors;
    }

    public Map<String, Object> params() {
        return params;
    }
}
