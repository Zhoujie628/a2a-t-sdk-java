package net.openan.a2at.sdk.negotiation.content;

/**
 * Session context carried by every negotiation message.
 *
 * <p>The context identifies one negotiation session, tracks the current round, and bounds how many rounds the session
 * may run. A context is immutable; advancing to the next round produces a new instance.
 *
 * @param id unique identifier of the negotiation session, expected to be a UUID
 * @param round current negotiation round, 1-based
 * @param maxRounds maximum number of rounds before the negotiation must end
 * @since 2026-06
 */
public record NegotiationContext(String id, int round, int maxRounds) {

    /** Default round budget applied when a caller does not specify one. */
    public static final int DEFAULT_MAX_ROUNDS = 5;

    /**
     * Validates the context fields.
     *
     * @throws NegotiationContentException if the id is blank, the round is below 1, or maxRounds is below 1
     */
    public NegotiationContext {
        if (id == null || id.isBlank()) {
            throw new NegotiationContentException("Negotiation context id must not be blank.", "context.id");
        }
        if (round < 1) {
            throw new NegotiationContentException(
                    "Negotiation context round must be a positive integer but was " + round + ".", "context.round");
        }
        if (maxRounds < 1) {
            throw new NegotiationContentException(
                    "Negotiation context maxRounds must be a positive integer but was " + maxRounds + ".",
                    "context.maxRounds");
        }
    }

    /**
     * Creates a context using the default round budget.
     *
     * @param id unique identifier of the negotiation session
     * @param round current negotiation round, 1-based
     * @return new negotiation context with {@link #DEFAULT_MAX_ROUNDS} as the round budget
     */
    public static NegotiationContext of(String id, int round) {
        return new NegotiationContext(id, round, DEFAULT_MAX_ROUNDS);
    }

    /**
     * Returns the context for the next round, leaving this context unchanged.
     *
     * @return new negotiation context whose round is one greater than the current round
     */
    public NegotiationContext nextRound() {
        return new NegotiationContext(id, round + 1, maxRounds);
    }

    /**
     * Reports whether the round budget has been exceeded.
     *
     * <p>A context whose round equals maxRounds is not exhausted yet; only rounds strictly beyond the budget are.
     *
     * @return {@code true} if the current round is greater than maxRounds
     */
    public boolean isExhausted() {
        return round > maxRounds;
    }
}
