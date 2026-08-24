package net.openan.a2at.sdk.corpus;

/**
 * Negotiation context of one corpus record, inline in the corpus JSON.
 *
 * <p>The engine converts this spec into a {@code NegotiationContext}; a JSON {@code null} context (the null-context
 * probes of the validate family) stays a null reference on the loaded case instead of a spec.
 *
 * @param id negotiation session id (a 36-digit UUID in the rule-gated cases)
 * @param round current negotiation round, at least 1
 * @param maxRounds negotiated round limit, at least 1
 * @since 2026-08
 */
public record ContextSpec(String id, int round, int maxRounds) {

    public ContextSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("The context id must not be blank.");
        }
        if (round < 1 || maxRounds < 1) {
            throw new IllegalArgumentException("The context round and maxRounds must be at least 1.");
        }
    }
}
