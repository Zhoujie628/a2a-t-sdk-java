package net.openan.a2at.sdk.negotiation.generation;

import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.core.model.PromptTemplate;

/**
 * Renders one negotiation message from typed content and a loaded template.
 *
 * <p>Implementations assemble the language-specific slot map from the vocabulary, validate their content input and
 * delegate the final rendering step. The registry is the only supported dispatch path; it guarantees the exact runtime
 * type of the content before a generator is invoked.
 *
 * @since 2026-08
 */
interface NegotiationGenerator {

    /**
     * Generates the negotiation message text for one content object.
     *
     * @param context negotiation context of the message
     * @param content typed content of the message; must be the exact content type this generator serves
     * @param template loaded template to render
     * @param vocabulary vocabulary supplying the slot names of the message language
     * @return rendered negotiation message text
     * @throws IllegalArgumentException if the content does not match this generator or violates an input rule
     */
    String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary);
}
