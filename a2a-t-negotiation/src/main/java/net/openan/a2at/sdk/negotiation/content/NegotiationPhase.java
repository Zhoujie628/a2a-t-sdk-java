package net.openan.a2at.sdk.negotiation.content;

/**
 * Phase of the negotiation lifecycle an API call operates on.
 *
 * <p>The API-level phase concept has three values, while the template URI layer only distinguishes two: {@code accept}
 * and {@code reject} share the {@code accept-reject} template, differing only in the conclusion value filled into the
 * template slot.
 *
 * @since 2026-06
 */
public enum NegotiationPhase {

    /** Phase that proposes or advances a negotiation. */
    PROPOSE("propose"),

    /** Terminal phase that accepts a negotiation. */
    ACCEPT("accept-reject"),

    /** Terminal phase that rejects a negotiation. */
    REJECT("accept-reject");

    private final String uriSegment;

    NegotiationPhase(String uriSegment) {
        this.uriSegment = uriSegment;
    }

    /**
     * Returns the template URI segment for this phase.
     *
     * @return {@code propose} for the propose phase, {@code accept-reject} for both terminal phases
     */
    public String uriSegment() {
        return uriSegment;
    }
}
