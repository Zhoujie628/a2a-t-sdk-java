package net.openan.a2at.sdk.negotiation.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.RuleChecker;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Adapter that bridges {@link NegotiationComplianceChecker} to the {@link RuleChecker} contract.
 *
 * <p>The adapter holds a {@link NegotiationComplianceChecker} and a {@link Vocabulary}, delegating the
 * {@link RuleChecker#check(String)} call to the vocabulary-aware {@link NegotiationComplianceChecker#check(String,
 * Vocabulary)} method. The resulting {@link NegotiationRuleCheckResult} is converted into either a map of context
 * parameters ({@code id}, {@code round}, {@code maxRounds}) or a {@link ContentValidationException}.
 *
 * @since 2026-08
 */
public final class NegotiationRuleCheckerAdapter implements RuleChecker {

    private static final String NON_NEGOTIATION_MESSAGE =
            "missing negotiation context section; for Task-T compliance use checkTaskPrompt";

    private final NegotiationComplianceChecker checker;

    private final Vocabulary vocabulary;

    public NegotiationRuleCheckerAdapter(NegotiationComplianceChecker checker, Vocabulary vocabulary) {
        this.checker = Objects.requireNonNull(checker, "checker");
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
    }

    @Override
    public Map<String, Object> check(String prompt) {
        NegotiationRuleCheckResult result = checker.check(prompt, vocabulary);
        if (!result.isNegotiation()) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_INVALID_INPUT, NON_NEGOTIATION_MESSAGE, List.of());
        }
        if (!result.passed()) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_RULE_VIOLATION,
                    "Negotiation context rule validation failed.",
                    result.errors());
        }
        NegotiationContext ctx = result.context();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", ctx.id());
        params.put("round", ctx.round());
        params.put("maxRounds", ctx.maxRounds());
        return Collections.unmodifiableMap(params);
    }
}