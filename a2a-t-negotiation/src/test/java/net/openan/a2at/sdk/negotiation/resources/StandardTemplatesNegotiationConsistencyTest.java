package net.openan.a2at.sdk.negotiation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.junit.jupiter.api.Test;

/**
 * Locks the two sources of negotiation template URI spelling together: the constants in {@link StandardTemplates}
 * (a2a-t-core, cannot depend on this module) and the compositional logic in {@link NegotiationReference} backed by
 * the {@link NegotiationType}/{@link NegotiationPhase} enums. Any drift turns this test red.
 */
class StandardTemplatesNegotiationConsistencyTest {

    private static final List<NegotiationPhase> TYPED_PHASES =
            List.of(NegotiationPhase.PROPOSE, NegotiationPhase.ACCEPT, NegotiationPhase.REJECT);

    @Test
    void negotiationConstantsMatchReferenceComposition() {
        for (NegotiationType type : NegotiationType.values()) {
            for (NegotiationPhase phase : TYPED_PHASES) {
                NegotiationReference reference = new NegotiationReference(type, phase, "en-US");
                TemplateUri expected = findConstant(reference.uri());
                assertEquals(
                        reference.uri(),
                        expected.uri(),
                        "StandardTemplates has no constant matching the composed URI of " + type + "/" + phase);
            }
        }
        NegotiationReference abort = new NegotiationReference(null, NegotiationPhase.ABORT, "en-US");
        TemplateUri abortConstant = findConstant(abort.uri());
        assertEquals(
                abort.uri(),
                abortConstant.uri(),
                "StandardTemplates has no constant matching the composed URI of the common abort template");
    }

    @Test
    void negotiationGroupCoversExactlyTheComposedUris() {
        List<String> composed =
                StandardTemplates.NEGOTIATION.stream().map(TemplateUri::uri).sorted().toList();
        List<String> expected = new java.util.ArrayList<>();
        // Accept and reject share the accept-reject template, so the group carries one URI per type for them.
        for (NegotiationType type : NegotiationType.values()) {
            for (NegotiationPhase phase : List.of(NegotiationPhase.PROPOSE, NegotiationPhase.ACCEPT)) {
                expected.add(new NegotiationReference(type, phase, "en-US").uri());
            }
        }
        expected.add(new NegotiationReference(null, NegotiationPhase.ABORT, "en-US").uri());
        assertEquals(expected.stream().sorted().toList(), composed);
    }

    private static TemplateUri findConstant(String uri) {
        return StandardTemplates.NEGOTIATION.stream()
                .filter(template -> template.uri().equals(uri))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No StandardTemplates constant for URI " + uri));
    }
}
