package net.openan.a2at.sdk.negotiation.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Default rule-level compliance checker for rendered negotiation messages.
 *
 * <p>The text is split on lines starting with {@code ## }; anything before the first section title is discarded. A text
 * is recognised as a negotiation message when one section title equals the vocabulary's negotiation context section
 * title. For a recognised message the checker parses the {@code - id:}, {@code - round:} and {@code - maxRounds:} list
 * lines of that section and enforces that the id is a UUID of 36 characters in 8-4-4-4-12 hexadecimal form, that the
 * round and the round budget are integers of at least 1, and that the round does not exceed the budget. No other rules
 * are applied: the checker does not infer the negotiation type, does not validate conclusion values, does not require
 * ending result sections and does not check conditional-section exclusivity.
 *
 * @since 2026-06
 */
public final class DefaultNegotiationComplianceChecker implements NegotiationComplianceChecker {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final String SECTION_PREFIX = "## ";

    private static final String ID_SLOT = "id";

    private static final String ROUND_SLOT = "round";

    private static final String MAX_ROUNDS_SLOT = "maxRounds";

    /** Creates the default checker. */
    public DefaultNegotiationComplianceChecker() {}

    /**
     * Runs the rule-level compliance check of one rendered message.
     *
     * @param prompt rendered negotiation message text; a null or empty text is reported as not being a negotiation
     *     message
     * @param vocabulary vocabulary supplying the language-specific section titles of the message
     * @return rule check outcome: not a negotiation message when the context section is absent, otherwise the parsed
     *     context with no errors when every context rule holds, otherwise the context rule errors without a context
     * @throws NullPointerException if the vocabulary is null
     */
    @Override
    public NegotiationRuleCheckResult check(String prompt, Vocabulary vocabulary) {
        if (vocabulary == null) {
            throw new NullPointerException("vocabulary");
        }
        List<String> contextBody = contextSectionBody(prompt, vocabulary.get("section.context"));
        if (contextBody == null) {
            return new NegotiationRuleCheckResult(false, false, List.of(), null);
        }

        String id = stringValue(contextBody, "- id:");
        String roundText = stringValue(contextBody, "- round:");
        String maxRoundsText = stringValue(contextBody, "- maxRounds:");
        Integer round = integerValue(roundText);
        Integer maxRounds = integerValue(maxRoundsText);

        List<SlotValidationError> errors = new ArrayList<>();
        collectIdErrors(id, errors);
        collectRangeErrors(ROUND_SLOT, round, roundText, errors);
        collectRangeErrors(MAX_ROUNDS_SLOT, maxRounds, maxRoundsText, errors);
        if (round != null && maxRounds != null && round >= 1 && maxRounds >= 1 && round > maxRounds) {
            errors.add(new SlotValidationError(
                    ROUND_SLOT,
                    "out_of_range",
                    "Negotiation context round " + round + " must not exceed maxRounds " + maxRounds + "."));
        }

        if (errors.isEmpty()) {
            NegotiationContext context = new NegotiationContext(id, round, maxRounds);
            return new NegotiationRuleCheckResult(true, true, List.of(), context);
        }
        return new NegotiationRuleCheckResult(false, true, List.copyOf(errors), null);
    }

    private static void collectIdErrors(String id, List<SlotValidationError> errors) {
        if (id == null) {
            errors.add(new SlotValidationError(
                    ID_SLOT, "missing_field", "Negotiation context section is missing the id line."));
            return;
        }
        if (!UUID_PATTERN.matcher(id).matches()) {
            errors.add(new SlotValidationError(
                    ID_SLOT,
                    "invalid_uuid",
                    "Negotiation context id must be a 36-character UUID in 8-4-4-4-12 hexadecimal form but was '" + id
                            + "'."));
        }
    }

    private static void collectRangeErrors(
            String slotName, Integer value, String valueText, List<SlotValidationError> errors) {
        if (value == null) {
            if (valueText == null) {
                errors.add(new SlotValidationError(
                        slotName,
                        "missing_field",
                        "Negotiation context section is missing the " + slotName + " line."));
            } else {
                errors.add(new SlotValidationError(
                        slotName,
                        "invalid_value",
                        "Negotiation context " + slotName + " must be an integer but was '" + valueText + "'."));
            }
            return;
        }
        if (value < 1) {
            errors.add(new SlotValidationError(
                    slotName,
                    "out_of_range",
                    "Negotiation context " + slotName + " must be a positive integer but was " + value + "."));
        }
    }

    /**
     * Returns the body lines of the negotiation context section, or null when the text has no such section.
     *
     * <p>Content before the first section title is discarded entirely, whether or not a context section follows.
     */
    private static List<String> contextSectionBody(String prompt, String contextTitle) {
        if (prompt == null || prompt.isEmpty()) {
            return null;
        }
        boolean inContextSection = false;
        List<String> bodyLines = new ArrayList<>();
        for (String rawLine : prompt.split("\n", -1)) {
            String line = rawLine.strip();
            if (line.startsWith(SECTION_PREFIX)) {
                String title = line.substring(SECTION_PREFIX.length()).strip();
                if (title.equals(contextTitle)) {
                    if (inContextSection) {
                        break;
                    }
                    inContextSection = true;
                    continue;
                }
                if (inContextSection) {
                    break;
                }
                continue;
            }
            if (inContextSection) {
                bodyLines.add(line);
            }
        }
        return inContextSection ? bodyLines : null;
    }

    /**
     * Returns the stripped value of the first list line carrying the given prefix, or null when no such line exists.
     */
    private static String stringValue(List<String> bodyLines, String linePrefix) {
        for (String line : bodyLines) {
            if (line.startsWith(linePrefix)) {
                return line.substring(linePrefix.length()).strip();
            }
        }
        return null;
    }

    private static Integer integerValue(String valueText) {
        if (valueText == null) {
            return null;
        }
        try {
            return Integer.valueOf(valueText);
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
