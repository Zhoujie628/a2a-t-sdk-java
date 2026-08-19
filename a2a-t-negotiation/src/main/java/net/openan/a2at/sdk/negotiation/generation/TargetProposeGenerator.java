package net.openan.a2at.sdk.negotiation.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.resources.PromptTemplate;

/**
 * Generator for target negotiation propose messages.
 *
 * <p>Besides the required target negotiation summary, the message renders round-driven conditional sections: the intent
 * understanding section appears only on the first round, the alignment and clarification section only on later rounds,
 * and the clarification request section only when clarification items are present.
 *
 * @since 2026-06
 */
public final class TargetProposeGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates a target negotiation propose message.
     *
     * @param context negotiation context of the message
     * @param content target propose content
     * @param template target propose template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered target propose message text
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        TargetProposeContent proposeContent =
                contentOf(content, TargetProposeContent.class, "Target propose generator");
        requiredText(
                proposeContent.targetNegotiationDescription(),
                "content.targetNegotiationDescription",
                "Target negotiation description");
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(vocabulary.get("section.context"), contextSlotValue(context, vocabulary));
        slots.put(vocabulary.get("slot.target"), proposeContent.targetNegotiationDescription());
        if (context.round() == 1) {
            slots.put(
                    vocabulary.get("section.target_intent"),
                    formatItems(proposeContent.intentUnderstanding(), vocabulary));
        } else {
            slots.put(
                    vocabulary.get("section.target_alignment"),
                    formatItems(proposeContent.alignmentAndClarification(), vocabulary));
        }
        slots.put(
                vocabulary.get("section.target_clarification"),
                formatItems(proposeContent.requestForClarification(), vocabulary));
        return render(template, slots);
    }
}
