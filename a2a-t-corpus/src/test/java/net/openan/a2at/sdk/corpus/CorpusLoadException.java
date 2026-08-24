package net.openan.a2at.sdk.corpus;

/**
 * Signals a defect in the negotiation test corpus: an unknown key, a dangling or circular {@code $ref}, a duplicate
 * id, an incomplete expectation block or any other format violation.
 *
 * <p>The loader fails fast — before any case runs — and the message always names the offending corpus file, the
 * offending record id (when already readable) and the JSON path of the defect.
 *
 * @since 2026-08
 */
public final class CorpusLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a corpus load failure.
     *
     * @param message failure description naming the file, the record id and the JSON path
     */
    public CorpusLoadException(String message) {
        super(message);
    }

    /**
     * Creates a corpus load failure with an underlying cause.
     *
     * @param message failure description naming the file, the record id and the JSON path
     * @param cause underlying read or parse failure
     */
    public CorpusLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
