package net.openan.a2at.sdk.negotiation.validation;

import java.util.Map;
import net.openan.a2at.sdk.core.validation.RuleChecker;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Rule-level compliance checker for rendered negotiation messages.
 *
 * <p>The checker is deterministic and never calls an LLM. It splits the text into {@code ## } sections, recognises
 * whether the text is a negotiation message by the presence of the negotiation context section, and validates only the
 * strong constraints of that section: the id must be a UUID in 8-4-4-4-12 hexadecimal form, the round and the round
 * budget must be positive integers, and the round must not exceed the budget. Type inference, conclusion values,
 * ending-section presence and conditional-section exclusivity are deliberately not checked here; they belong to the
 * semantic validation step.
 *
 * @since 2026-06
 */
public interface NegotiationComplianceChecker extends RuleChecker {

    /**
     * Runs the rule-level compliance check of one rendered message.
     *
     * @param prompt rendered negotiation message text; a null or empty text is reported as not being a negotiation
     *     message
     * @param vocabulary vocabulary supplying the language-specific section titles of the message
     * @return rule check outcome carrying the pass flag, the negotiation recognition flag, the structured errors and
     *     the parsed context
     * @throws NullPointerException if the vocabulary is null
     */
    NegotiationRuleCheckResult check(String prompt, Vocabulary vocabulary);

    /**
     * {@inheritDoc}
     *
     * <p>This default implementation is not supported; use {@link #check(String, Vocabulary)} instead. The
     * {@link NegotiationRuleCheckerAdapter} bridges this interface to the {@link RuleChecker} contract.
     */
    @Override
    default Map<String, Object> check(String prompt) {
        throw new UnsupportedOperationException("Use check(String, Vocabulary) instead");
    }
}
