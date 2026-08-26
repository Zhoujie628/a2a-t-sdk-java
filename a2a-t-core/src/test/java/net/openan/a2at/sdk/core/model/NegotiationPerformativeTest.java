package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NegotiationPerformativeTest {

    @Test
    void valuesPinTheFourWireConstantsInDeclarationOrder() {
        List<NegotiationPerformative> constants = List.of(NegotiationPerformative.values());

        assertEquals(4, constants.size());
        assertEquals(
                List.of(
                        NegotiationPerformative.PROPOSE,
                        NegotiationPerformative.ACCEPT,
                        NegotiationPerformative.REJECT,
                        NegotiationPerformative.ABORT),
                constants);
    }

    @Test
    void namesArePinnedToUpperCaseWireValues() {
        assertEquals("PROPOSE", NegotiationPerformative.PROPOSE.name());
        assertEquals("ACCEPT", NegotiationPerformative.ACCEPT.name());
        assertEquals("REJECT", NegotiationPerformative.REJECT.name());
        assertEquals("ABORT", NegotiationPerformative.ABORT.name());
    }

    @Test
    void tryParseAcceptsTheExactWireValues() {
        assertEquals(Optional.of(NegotiationPerformative.PROPOSE), NegotiationPerformative.tryParse("PROPOSE"));
        assertEquals(Optional.of(NegotiationPerformative.ACCEPT), NegotiationPerformative.tryParse("ACCEPT"));
        assertEquals(Optional.of(NegotiationPerformative.REJECT), NegotiationPerformative.tryParse("REJECT"));
        assertEquals(Optional.of(NegotiationPerformative.ABORT), NegotiationPerformative.tryParse("ABORT"));
    }

    @Test
    void tryParseRejectsLowerCaseVariants() {
        assertTrue(NegotiationPerformative.tryParse("propose").isEmpty());
        assertTrue(NegotiationPerformative.tryParse("accept").isEmpty());
        assertTrue(NegotiationPerformative.tryParse("reject").isEmpty());
        assertTrue(NegotiationPerformative.tryParse("abort").isEmpty());
    }

    @Test
    void tryParseRejectsNullAndUnknownValues() {
        assertTrue(NegotiationPerformative.tryParse(null).isEmpty());
        assertTrue(NegotiationPerformative.tryParse("").isEmpty());
        assertTrue(NegotiationPerformative.tryParse("Propose").isEmpty());
        assertTrue(NegotiationPerformative.tryParse("PROPOSE ").isEmpty());
        assertTrue(NegotiationPerformative.tryParse("COUNTER").isEmpty());
        assertTrue(NegotiationPerformative.tryParse("PROPOSE, ACCEPT").isEmpty());
        assertFalse(NegotiationPerformative.tryParse("ABORT").isEmpty());
    }
}
