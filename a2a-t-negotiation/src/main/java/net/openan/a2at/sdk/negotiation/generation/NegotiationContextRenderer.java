package net.openan.a2at.sdk.negotiation.generation;

import java.util.Objects;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;

/**
 * Assembles the negotiation context into the single slot value used by context sections.
 *
 * <p>The context travels in one template slot as a markdown list whose field names are language-neutral.
 */
final class NegotiationContextRenderer {

    /**
     * Renders one negotiation context as a markdown list.
     *
     * @param context negotiation context to render
     * @return three lines of the form {@code - id: ...}, {@code - round: ...}, {@code - maxRounds: ...} joined by
     *     single newlines
     * @throws NullPointerException if the context is null
     */
    public String render(NegotiationContext context) {
        Objects.requireNonNull(context, "Negotiation context must not be null.");
        return "- id: " + context.id() + "\n- round: " + context.round() + "\n- maxRounds: " + context.maxRounds();
    }
}
