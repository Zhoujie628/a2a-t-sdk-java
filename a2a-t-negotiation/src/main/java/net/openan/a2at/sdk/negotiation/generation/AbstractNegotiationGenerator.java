package net.openan.a2at.sdk.negotiation.generation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.core.model.PromptTemplate;

/**
 * Shared plumbing of the six negotiation generators.
 *
 * <p>Subclasses validate their exact content type, assemble the slot map and hand over to the shared renderers. The
 * slot map is keyed by the language-specific slot names taken from the vocabulary.
 */
abstract class AbstractNegotiationGenerator implements NegotiationGenerator {

    private final NegotiationPromptRenderer promptRenderer = new NegotiationPromptRenderer();

    private final NegotiationItemFormatter itemFormatter = new NegotiationItemFormatter();

    /**
     * Casts the content to the exact type this generator serves.
     *
     * @param <T> expected content type
     * @param content content object validated by the registry
     * @param expectedType exact content class this generator serves
     * @param generatorName human-readable generator name used in the failure message
     * @return content cast to the expected type
     * @throws IllegalArgumentException if the content is null or of another runtime type
     */
    protected static <T extends NegotiationContent> T contentOf(
            NegotiationContent content, Class<T> expectedType, String generatorName) {
        if (content == null || content.getClass() != expectedType) {
            throw new IllegalArgumentException(
                    generatorName + " requires content of type " + expectedType.getSimpleName() + " but received "
                            + (content == null ? "null" : content.getClass().getSimpleName()) + ".");
        }
        return expectedType.cast(content);
    }

    /**
     * Validates that a conclusion is renderable by a typed ending generator, rejecting null and the abort conclusion.
     *
     * @param conclusion conclusion carried by ending content
     * @return the validated conclusion
     * @throws NullPointerException if the conclusion is null
     * @throws IllegalArgumentException if the conclusion is abort; the abort message has no typed conclusion slot and
     *     is generated through the abort phase against the common abort template instead
     */
    protected static NegotiationConclusion renderableConclusion(NegotiationConclusion conclusion) {
        Objects.requireNonNull(
                conclusion, "Negotiation conclusion must not be null; accept and reject are the renderable"
                        + " conclusions of a typed negotiation.");
        if (conclusion == NegotiationConclusion.ABORT) {
            throw new IllegalArgumentException(
                    "Typed negotiation templates carry no Abort conclusion slot; generate the abort message of a"
                            + " terminated negotiation with generateAbortFromData against the common abort template"
                            + " instead.");
        }
        return conclusion;
    }

    /**
     * Validates that a required text field is present.
     *
     * @param value field value
     * @param field field path used in the failure
     * @param description field description used in the failure message
     * @return the validated text
     * @throws IllegalArgumentException if the value is null or blank
     */
    protected static String requiredText(String value, String field, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(description + " must not be blank.");
        }
        return value;
    }

    /**
     * Validates that a required item list is present and non-empty.
     *
     * @param items item list
     * @param field field path used in the failure
     * @param description field description used in the failure message
     * @return the validated item list
     * @throws IllegalArgumentException if the list is null or empty
     */
    protected static List<NegotiationItem> requiredItems(
            List<NegotiationItem> items, String field, String description) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(description + " must contain at least one item.");
        }
        return items;
    }

    /**
     * Formats an item list with the list punctuation of the message language.
     *
     * @param items items to format; null formats as an empty value
     * @param vocabulary vocabulary of the message language
     * @return formatted numbered lines, or an empty string when there is no item
     */
    protected String formatItems(List<NegotiationItem> items, Vocabulary vocabulary) {
        return itemFormatter.format(items, vocabulary.get("punct.list_colon"));
    }

    /**
     * Renders the template with the assembled slots.
     *
     * @param template loaded template to render
     * @param slots slot values keyed by language-specific slot names
     * @return rendered negotiation message text
     */
    protected String render(PromptTemplate template, Map<String, String> slots) {
        return promptRenderer.render(template.content(), slots);
    }
}
