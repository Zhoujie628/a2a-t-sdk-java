package net.openan.a2at.sdk.negotiation.content;

/**
 * Raised when negotiation content or negotiation content input violates a structural rule.
 *
 * <p>This is a programming-error exception: it signals invalid input data such as a blank context id or a malformed
 * template URI, not a processing failure. It intentionally does not extend the SDK processing-error root type.
 *
 * @since 2026-06
 */
public class NegotiationContentException extends RuntimeException {

    /**
     * Path of the offending input field, such as {@code context.id} or {@code templateUri}; null when not applicable.
     */
    private final String field;

    /**
     * Creates a content exception without a field path.
     *
     * @param message failure message
     */
    public NegotiationContentException(String message) {
        this(message, null);
    }

    /**
     * Creates a content exception pointing at one offending input field.
     *
     * @param message failure message
     * @param field path of the offending input field, or null when no single field applies
     */
    public NegotiationContentException(String message, String field) {
        super(message);
        this.field = field;
    }

    /**
     * Returns the path of the offending input field.
     *
     * @return field path such as {@code templateUri}, or null when no single field applies
     */
    public String getField() {
        return field;
    }
}
