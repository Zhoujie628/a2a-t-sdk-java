package net.openan.a2at.sdk.core.model;

import org.jspecify.annotations.Nullable;

/**
 * Session context carried by every negotiation message.
 *
 * <p>The context identifies one negotiation session, tracks the current round, and bounds how many rounds the session
 * may run. A context is immutable; advancing to the next round produces a new instance.
 *
 * <p>The optional {@code performative} plays a dual role. On an <em>input</em> context it is {@code null}: the context
 * acts as a session handle for an incoming message whose intent has not been decided yet. On an <em>output</em> context
 * the generation pipeline stamps the performative of the message it is about to emit, mirroring the nested
 * {@code negotiationContext} structure of the A2A-T Negotiation-T specification.
 *
 * @param id unique identifier of the negotiation session, expected to be a UUID
 * @param round current negotiation round, 1-based
 * @param maxRounds maximum number of rounds before the negotiation must end
 * @param performative communicative intent of the message this context travels with; {@code null} when the context is
 *     only a session handle (input) or the intent is not yet decided
 * @since 2026-08
 */
public record NegotiationContext(
        String id, int round, int maxRounds, @Nullable NegotiationPerformative performative) {

    /** Default round budget applied when a caller does not specify one. */
    public static final int DEFAULT_MAX_ROUNDS = 5;

    /**
     * Convenience constructor for contexts that act only as session handles.
     *
     * <p>Delegates to the canonical constructor with a {@code null} performative: the intent of the message is either
     * not applicable yet (input context) or decided later via {@link #withPerformative(NegotiationPerformative)}.
     *
     * @param id unique identifier of the negotiation session
     * @param round current negotiation round, 1-based
     * @param maxRounds maximum number of rounds before the negotiation must end
     */
    public NegotiationContext(String id, int round, int maxRounds) {
        this(id, round, maxRounds, null);
    }

    /**
     * Validates the context fields.
     *
     * @throws IllegalArgumentException if the id is blank, the round is below 1, or maxRounds is below 1
     */
    public NegotiationContext {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Negotiation context id must not be blank.");
        }
        if (round < 1) {
            throw new IllegalArgumentException(
                    "Negotiation context round must be a positive integer but was " + round + ".");
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException(
                    "Negotiation context maxRounds must be a positive integer but was " + maxRounds + ".");
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
     * <p>The next round's performative is not decided yet, so the returned context carries a {@code null} performative
     * regardless of this context's one.
     *
     * @return new negotiation context whose round is one greater than the current round
     */
    public NegotiationContext nextRound() {
        return new NegotiationContext(id, round + 1, maxRounds);
    }

    /**
     * Returns a copy of this context stamped with the given performative, leaving this context unchanged.
     *
     * @param performative communicative intent to stamp on the copy
     * @return new negotiation context with the same {@code id}, {@code round}, and {@code maxRounds} as this context
     */
    public NegotiationContext withPerformative(NegotiationPerformative performative) {
        return new NegotiationContext(id, round, maxRounds, performative);
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
