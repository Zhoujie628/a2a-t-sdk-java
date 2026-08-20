package net.openan.a2at.sdk.negotiation.generation;

import net.openan.a2at.sdk.negotiation.content.NegotiationContentException;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;

/**
 * Assembles the negotiation context into the single slot value used by context sections.
 *
 * <p>The context travels in one template slot as a markdown list whose field names are language-neutral.
 */
public final class NegotiationContextRenderer {

    /**
     * Renders one negotiation context as a markdown list.
     *
     * @param context negotiation context to render
     * @return three lines of the form {@code - id: ...}, {@code - round: ...}, {@code - maxRounds: ...} joined by
     *     single newlines
     * @throws NegotiationContentException if the context is null
     */
    public String render(NegotiationContext context) {
        if (context == null) {
            throw new NegotiationContentException("Negotiation context must not be null.", "context");
        }
        return "- id: " + context.id() + "\n- round: " + context.round() + "\n- maxRounds: " + context.maxRounds();
    }
}
