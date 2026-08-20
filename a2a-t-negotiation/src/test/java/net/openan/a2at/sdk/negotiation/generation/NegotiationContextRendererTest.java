package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.openan.a2at.sdk.negotiation.content.NegotiationContentException;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import org.junit.jupiter.api.Test;

class NegotiationContextRendererTest {

    private final NegotiationContextRenderer renderer = new NegotiationContextRenderer();

    @Test
    void rendersContextAsMarkdownListWithLanguageNeutralFieldNames() {
        NegotiationContext context = new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 2, 5);

        String rendered = renderer.render(context);

        assertEquals("- id: 3dbc13b5-bd57-4c2b-b503-24e381b6c8d3\n- round: 2\n- maxRounds: 5", rendered);
    }

    @Test
    void rendersContextBuiltByFactoryAndNextRound() {
        NegotiationContext context = NegotiationContext.of("id-1", 1).nextRound();

        assertEquals("- id: id-1\n- round: 2\n- maxRounds: 5", renderer.render(context));
    }

    @Test
    void rejectsNullContext() {
        NegotiationContentException exception =
                assertThrows(NegotiationContentException.class, () -> renderer.render(null));

        assertEquals("context", exception.getField());
    }
}
