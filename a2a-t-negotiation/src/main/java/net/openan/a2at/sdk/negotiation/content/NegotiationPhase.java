package net.openan.a2at.sdk.negotiation.content;

/**
 * Phase of the negotiation lifecycle an API call operates on.
 *
 * <p>The API-level phase concept has four values, while the template URI layer only distinguishes three: {@code accept}
 * and {@code reject} share the {@code accept-reject} template, differing only in the conclusion value filled into the
 * template slot, and {@code abort} is addressed by the single type-independent common abort template.
 *
 * @since 2026-08
 */
public enum NegotiationPhase {

    /** Phase that proposes or advances a negotiation. */
    PROPOSE("propose"),

    /** Terminal phase that accepts a negotiation. */
    ACCEPT("accept-reject"),

    /** Terminal phase that rejects a negotiation. */
    REJECT("accept-reject"),

    /** Terminal phase that aborts a negotiation; addressed by the type-independent common abort template. */
    ABORT("abort");

    private final String uriSegment;

    NegotiationPhase(String uriSegment) {
        this.uriSegment = uriSegment;
    }

    /**
     * Returns the template URI segment for this phase.
     *
     * @return {@code propose} for the propose phase, {@code accept-reject} for the accept and reject phases and
     *     {@code abort} for the abort phase
     */
    public String uriSegment() {
        return uriSegment;
    }
}
