package net.openan.a2at.sdk.negotiation.generation;

import java.util.Map;
import net.openan.a2at.sdk.prompt.taskrendering.DropBlankSlotSectionRenderer;
import net.openan.a2at.sdk.prompt.taskrendering.SectionedTemplateRenderer;

/**
 * Renders a negotiation template by filling slot sections and dropping empty ones.
 *
 * <p>A template is split into sections on {@code ## } title lines; any content before the first title, including a
 * leading HTML description comment, is discarded. A section whose first non-empty body line is a slot placeholder line
 * such as {@code {{required_information_items}} (required)} — or the same shape with the full-width required/optional
 * markers used by zh-CN templates — is a slot section: it is rendered as the title followed by the slot value, or
 * dropped entirely when the slot value is null or blank. Every other section is static and passes through with
 * placeholder substitution applied. Rendered sections are joined with a single blank line and the result carries no
 * trailing newline.
 *
 * <p>The rendering itself delegates to the shared {@link DropBlankSlotSectionRenderer} of the prompt kernel, which
 * owns the drop policy of the sectioned template grammar; this class keeps the negotiation-specific null-template
 * contract.
 *
 * @since 2026-06
 */
class NegotiationPromptRenderer implements SectionedTemplateRenderer {

    private final DropBlankSlotSectionRenderer delegate = new DropBlankSlotSectionRenderer();

    /**
     * Renders one template text with the given slot values.
     *
     * @param templateText full template text whose slot sections are filled or dropped
     * @param slots slot values keyed by the language-specific slot name; a null or blank value drops the slot section
     * @return rendered message text with sections joined by one blank line and no trailing newline; empty string when
     *     no section remains
     * @throws NegotiationRenderException if the template text is null
     */
    @Override
    public String render(String templateText, Map<String, String> slots) {
        if (templateText == null) {
            throw new NegotiationRenderException("Negotiation template text must not be null.");
        }
        return delegate.render(templateText, slots);
    }
}
