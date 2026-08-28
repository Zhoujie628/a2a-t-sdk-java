package net.openan.a2at.sdk.negotiation.generation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Generator for feasibility negotiation propose messages.
 *
 * <p>The message category selects which conditional section is rendered: requesting a feasibility evaluation renders the
 * contents to evaluate, proposing an alternative after an infeasible outcome renders the infeasibility details and
 * proposal, and a non-blank confirm request marks the derived "assessed as feasible and requesting confirmation" category
 * (action {@code REQUEST_FEASIBILITY_EVALUATION} with both lists empty) that renders only the confirm request. Exactly
 * one of the three sections is always present.
 *
 * @since 2026-08
 */
final class FeasibilityProposeGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates a feasibility negotiation propose message.
     *
     * @param context negotiation context of the message
     * @param content feasibility propose content
     * @param template feasibility propose template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered feasibility propose message text
     * @throws NullPointerException if the action is null
     * @throws IllegalArgumentException if the summary is blank, the action-selected section is empty, or the confirm
     *     request is combined with the wrong action or either conditional section
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        FeasibilityProposeContent proposeContent =
                contentOf(content, FeasibilityProposeContent.class, "Feasibility propose generator");
        requiredText(
                proposeContent.feasibilityNegotiationDescription(),
                "content.feasibilityNegotiationDescription",
                "Feasibility negotiation description");
        NegotiationAction action = proposeContent.action();
        Objects.requireNonNull(
                action,
                "Feasibility negotiation action must not be null; it selects the conditional sections of the message.");
        String confirmRequest = presentText(proposeContent.feasibilityConfirmRequest());
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(vocabulary.get("slot.feasibility"), proposeContent.feasibilityNegotiationDescription());
        if (confirmRequest != null) {
            if (action != NegotiationAction.REQUEST_FEASIBILITY_EVALUATION) {
                throw new IllegalArgumentException(
                        "Feasibility confirm request requires the REQUEST_FEASIBILITY_EVALUATION action but the content"
                                + " carries "
                                + action.name()
                                + ".");
            }
            if (hasItems(proposeContent.contentsToEvaluate()) || hasItems(proposeContent.infeasibilityDetailsAndProposal())) {
                throw new IllegalArgumentException(
                        "Feasibility confirm request must not be combined with the contents to evaluate or"
                                + " infeasibility details and proposal sections; a confirm-request round carries only"
                                + " the summary and the confirm request.");
            }
            slots.put(vocabulary.get("slot.feasibility_confirm_request"), confirmRequest);
        } else if (action == NegotiationAction.REQUEST_FEASIBILITY_EVALUATION) {
            slots.put(
                    vocabulary.get("slot.feasibility_evaluate"),
                    formatItems(
                            requiredItems(
                                    proposeContent.contentsToEvaluate(),
                                    "content.contentsToEvaluate",
                                    "Contents to evaluate of a feasibility evaluation request"),
                            vocabulary));
        } else {
            slots.put(
                    vocabulary.get("slot.feasibility_infeasible"),
                    formatItems(
                            requiredItems(
                                    proposeContent.infeasibilityDetailsAndProposal(),
                                    "content.infeasibilityDetailsAndProposal",
                                    "Infeasibility details and proposal of an alternative proposal"),
                            vocabulary));
        }
        return render(template, slots);
    }

    private static @Nullable String presentText(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean hasItems(@Nullable List<NegotiationItem> items) {
        return items != null && !items.isEmpty();
    }
}
