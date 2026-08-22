package net.openan.a2at.sdk.negotiation.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default rule-level compliance checker for negotiation messages.
 *
 * <p>The checker validates the negotiation context carried alongside the message in the A2A-T metadata: the id must be
 * a UUID of 36 characters in 8-4-4-4-12 hexadecimal form and the round must not exceed the round budget. The
 * positive-integer shape of the round fields is already guaranteed by the {@link NegotiationContext} constructor. No
 * other rules are applied: the checker does not infer the negotiation type, does not validate conclusion values, does
 * not require ending result sections and does not check conditional-section exclusivity.
 *
 * @since 2026-08
 */
public final class DefaultNegotiationComplianceChecker implements NegotiationComplianceChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNegotiationComplianceChecker.class);

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final String ID_SLOT = "id";

    private static final String ROUND_SLOT = "round";

    /** Creates the default checker. */
    public DefaultNegotiationComplianceChecker() {}

    /**
     * Runs the rule-level compliance check of one negotiation context.
     *
     * @param context negotiation context carried alongside the message in the A2A-T metadata
     * @return rule check outcome with no errors when every context rule holds, otherwise the context rule errors
     * @throws NullPointerException if the context is null
     */
    @Override
    public NegotiationRuleCheckResult check(NegotiationContext context) {
        Objects.requireNonNull(context, "context");
        List<SlotValidationError> errors = new ArrayList<>();
        collectIdErrors(context.id(), errors);
        if (context.round() > context.maxRounds()) {
            errors.add(new SlotValidationError(
                    ROUND_SLOT,
                    "out_of_range",
                    "Negotiation context round " + context.round() + " must not exceed maxRounds "
                            + context.maxRounds() + "."));
        }
        return logResult(new NegotiationRuleCheckResult(errors.isEmpty(), List.copyOf(errors)));
    }

    private static void collectIdErrors(String id, List<SlotValidationError> errors) {
        if (!UUID_PATTERN.matcher(id).matches()) {
            errors.add(new SlotValidationError(
                    ID_SLOT,
                    "invalid_uuid",
                    "Negotiation context id must be a 36-character UUID in 8-4-4-4-12 hexadecimal form but was '" + id
                            + "'."));
        }
    }

    /**
     * Emits the rule-check completion event of one check outcome.
     *
     * @param result rule check outcome to log
     * @return the unchanged outcome
     */
    private static NegotiationRuleCheckResult logResult(NegotiationRuleCheckResult result) {
        if (result.errors().isEmpty()) {
            LOGGER.atDebug().log("negotiation_rule_checks_completed passed={} error_count=0", result.passed());
        } else {
            LOGGER.atWarn()
                    .log(
                            "negotiation_rule_checks_completed passed={} error_count={}",
                            result.passed(),
                            result.errors().size());
        }
        return result;
    }
}
