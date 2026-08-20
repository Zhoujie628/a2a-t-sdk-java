package net.openan.a2at.sdk.negotiation.content;

/**
 * Terminal outcome of a negotiation as expressed in negotiation result sections.
 *
 * <p>Only {@code ACCEPT} and {@code REJECT} are renderable conclusions; {@code ABORT} exists in the model but no
 * bundled template carries it, so generation methods reject it as a programming error.
 *
 * @since 2026-06
 */
public enum NegotiationConclusion {

    /** The negotiation parties reached an agreement. */
    ACCEPT("Accept"),

    /** The negotiation parties did not reach an agreement. */
    REJECT("Reject"),

    /** The negotiation was abandoned outside the template outcome model. */
    ABORT("Abort");

    private final String literal;

    NegotiationConclusion(String literal) {
        this.literal = literal;
    }

    /**
     * Returns the literal text filled into negotiation result slots.
     *
     * @return literal conclusion text such as {@code Accept}
     */
    public String literal() {
        return literal;
    }
}
