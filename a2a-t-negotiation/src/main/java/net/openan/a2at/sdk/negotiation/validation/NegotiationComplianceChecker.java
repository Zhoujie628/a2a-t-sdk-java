package net.openan.a2at.sdk.negotiation.validation;

import net.openan.a2at.sdk.core.model.NegotiationContext;

/**
 * Rule-level compliance checker for negotiation messages.
 *
 * <p>The checker is deterministic and never calls an LLM. It validates only the strong constraints of the negotiation
 * context carried alongside the message: the id must be a UUID in 8-4-4-4-12 hexadecimal form and the round must not
 * exceed the round budget. The positive-integer shape of the round fields is already guaranteed by the
 * {@link NegotiationContext} constructor. Type inference, conclusion values, ending-section presence and
 * conditional-section exclusivity are deliberately not checked here; they belong to the semantic validation step.
 *
 * @since 2026-08
 */
public interface NegotiationComplianceChecker {

    /**
     * Runs the rule-level compliance check of one negotiation context.
     *
     * @param context negotiation context carried alongside the message in the A2A-T metadata
     * @return rule check outcome carrying the pass flag and the structured errors
     * @throws NullPointerException if the context is null
     */
    NegotiationRuleCheckResult check(NegotiationContext context);
}
