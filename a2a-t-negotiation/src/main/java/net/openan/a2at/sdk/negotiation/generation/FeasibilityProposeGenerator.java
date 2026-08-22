package net.openan.a2at.sdk.negotiation.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.core.model.PromptTemplate;

/**
 * Generator for feasibility negotiation propose messages.
 *
 * <p>The action selects which conditional section is rendered: requesting a feasibility evaluation renders the contents
 * to evaluate, while proposing an alternative after an infeasible outcome renders the infeasibility details and
 * proposal. Exactly one of the two sections is always present.
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
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(vocabulary.get("slot.context"), contextSlotValue(context, vocabulary));
        slots.put(vocabulary.get("slot.feasibility"), proposeContent.feasibilityNegotiationDescription());
        if (action == NegotiationAction.REQUEST_FEASIBILITY_EVALUATION) {
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
}
