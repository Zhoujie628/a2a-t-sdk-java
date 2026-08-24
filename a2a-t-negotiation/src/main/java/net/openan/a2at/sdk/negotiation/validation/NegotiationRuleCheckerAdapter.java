package net.openan.a2at.sdk.negotiation.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.RuleChecker;

/**
 * Adapter that bridges {@link NegotiationComplianceChecker} to the {@link RuleChecker} contract for one validation
 * call.
 *
 * <p>The adapter holds a {@link NegotiationComplianceChecker} and the negotiation context carried alongside the
 * message in the A2A-T metadata, delegating the {@link RuleChecker#check(String)} call to the context-aware
 * {@link NegotiationComplianceChecker#check(NegotiationContext)} method. A null context is reported as not being a
 * negotiation message. The resulting {@link NegotiationRuleCheckResult} is converted into either a map of context
 * parameters ({@code id}, {@code round}, {@code maxRounds}) or a {@link ContentValidationException}.
 *
 * @since 2026-08
 */
public final class NegotiationRuleCheckerAdapter implements RuleChecker {

    private static final String NON_NEGOTIATION_MESSAGE =
            "missing negotiation context; for Task-T compliance use checkTaskPrompt";

    private final NegotiationComplianceChecker checker;

    private final NegotiationContext context;

    public NegotiationRuleCheckerAdapter(NegotiationComplianceChecker checker, NegotiationContext context) {
        this.checker = Objects.requireNonNull(checker, "checker");
        this.context = context;
    }

    @Override
    public Map<String, Object> check(String prompt) {
        if (context == null) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_INVALID_INPUT, NON_NEGOTIATION_MESSAGE, List.of());
        }
        NegotiationRuleCheckResult result = checker.check(context);
        if (!result.passed()) {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_RULE_VIOLATION,
                    "Negotiation context rule validation failed.",
                    result.errors());
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", context.id());
        params.put("round", context.round());
        params.put("maxRounds", context.maxRounds());
        return Collections.unmodifiableMap(params);
    }
}
