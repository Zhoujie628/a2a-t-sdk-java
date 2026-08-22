package net.openan.a2at.sdk.negotiation.validation;

import java.util.List;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.NegotiationContext;

/**
 * Outcome of the rule-level compliance check of a rendered negotiation message.
 *
 * <p>The checker only validates the negotiation context section, so this result carries exactly four components: the
 * overall pass flag, the flag telling whether the text is a negotiation message at all, the structured rule errors and
 * the parsed negotiation context. Nothing else is inferred or checked here.
 *
 * @param passed {@code true} only when the text is a negotiation message whose negotiation context section satisfies
 *     every context rule
 * @param isNegotiation {@code true} when the text contains the negotiation context section and is therefore a
 *     negotiation message
 * @param errors structured rule errors of the negotiation context section; empty when the text is not a negotiation
 *     message or when every rule passes
 * @param context parsed negotiation context; {@code null} unless the text is a negotiation message whose context is
 *     fully valid
 * @since 2026-08
 */
public record NegotiationRuleCheckResult(
        boolean passed, boolean isNegotiation, List<SlotValidationError> errors, NegotiationContext context) {

    /**
     * Normalizes the error list.
     *
     * @throws NullPointerException if the errors list is null
     */
    public NegotiationRuleCheckResult {
        errors = List.copyOf(errors);
    }
}
