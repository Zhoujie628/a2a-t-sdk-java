package net.openan.a2at.sdk.negotiation.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.resources.PromptTemplate;

/**
 * Generator for information negotiation propose messages.
 *
 * <p>The message carries the requested information items and, when present, a free-form line describing how the missing
 * items relate to each other, appended after the item list.
 *
 * @since 2026-06
 */
public final class InformationProposeGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates an information negotiation propose message.
     *
     * @param context negotiation context of the message
     * @param content information propose content
     * @param template information propose template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered information propose message text
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        InfoProposeContent proposeContent =
                contentOf(content, InfoProposeContent.class, "Information propose generator");
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(vocabulary.get("slot.context"), contextSlotValue(context, vocabulary));
        slots.put(vocabulary.get("slot.info_items"), itemsSlotValue(proposeContent, vocabulary));
        return render(template, slots);
    }

    private String itemsSlotValue(InfoProposeContent content, Vocabulary vocabulary) {
        String items = formatItems(content.items(), vocabulary);
        if (content.relationship() == null || content.relationship().isBlank()) {
            return items;
        }
        String relationshipLine = vocabulary.get("label.relationship") + content.relationship();
        return items.isEmpty() ? relationshipLine : items + "\n" + relationshipLine;
    }
}
