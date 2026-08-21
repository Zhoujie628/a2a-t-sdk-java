package net.openan.a2at.sdk.negotiation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationReferenceTest {

    @Test
    void typeSegmentUsesHyphenatedNames() {
        assertEquals("information-negotiation", NegotiationType.INFORMATION.typeSegment());
        assertEquals("target-negotiation", NegotiationType.TARGET.typeSegment());
        assertEquals("feasibility-negotiation", NegotiationType.FEASIBILITY.typeSegment());
    }

    @Test
    void uriComposesPrefixVersionTypeSegmentAndPhaseSegment() {
        assertEquals(
                "Negotiation-T/v1/information-negotiation/propose",
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "en-US").uri());
        assertEquals(
                "Negotiation-T/v1/target-negotiation/accept-reject",
                new NegotiationReference(NegotiationType.TARGET, NegotiationPhase.ACCEPT, "zh-CN").uri());
        assertEquals(
                "Negotiation-T/v1/feasibility-negotiation/accept-reject",
                new NegotiationReference(NegotiationType.FEASIBILITY, NegotiationPhase.REJECT, "zh-CN").uri());
    }

    @Test
    void tryParseAcceptsAllSixValidUris() {
        for (NegotiationType type : NegotiationType.values()) {
            NegotiationReference propose = requirePresent(
                    NegotiationReference.tryParse(proposeUri(type), NegotiationPhase.PROPOSE, "zh-CN"));
            assertEquals(type, propose.type());
            assertEquals(NegotiationPhase.PROPOSE, propose.phase());
            assertEquals(proposeUri(type), propose.uri());
            assertEquals("zh-CN", propose.language());

            NegotiationReference accept = requirePresent(
                    NegotiationReference.tryParse(acceptRejectUri(type), NegotiationPhase.ACCEPT, "en-US"));
            assertEquals(type, accept.type());
            assertEquals(NegotiationPhase.ACCEPT, accept.phase());
            assertEquals(acceptRejectUri(type), accept.uri());

            NegotiationReference reject = requirePresent(
                    NegotiationReference.tryParse(acceptRejectUri(type), NegotiationPhase.REJECT, "en-US"));
            assertEquals(type, reject.type());
            assertEquals(NegotiationPhase.REJECT, reject.phase());
            assertEquals(acceptRejectUri(type), reject.uri());
        }
    }

    @Test
    void tryParseRejectsWrongSegmentCount() {
        assertTrue(NegotiationReference.tryParse("information-negotiation/propose", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/v1/information-negotiation/propose/extra", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse("foo", NegotiationPhase.PROPOSE, "zh-CN").isEmpty());
    }

    @Test
    void tryParseRejectsWrongPrefixAndVersion() {
        assertTrue(NegotiationReference.tryParse("Task-T/v1/information-negotiation/propose", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "negotiation-t/v1/information-negotiation/propose", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse("Negotiation-T/v2/information-negotiation/propose", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsMissingTypeSegmentSuffixAndUnderscoreVariant() {
        assertTrue(NegotiationReference.tryParse("Negotiation-T/v1/information/propose", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/v1/information_negotiation/propose", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsUnknownType() {
        assertTrue(NegotiationReference.tryParse("Negotiation-T/v1/unknown-negotiation/propose", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsIllegalPhaseSegment() {
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/v1/information-negotiation/propose-x", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse("Negotiation-T/v1/information-negotiation/accept", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsPhaseMismatchAgainstExpectedPhase() {
        Optional<NegotiationReference> parsed = NegotiationReference.tryParse(
                "Negotiation-T/v1/information-negotiation/propose", NegotiationPhase.ACCEPT, "zh-CN");

        assertTrue(parsed.isEmpty());
    }

    @Test
    void tryParseReturnsEmptyForBlankAndNullUriButThrowsOnNullExpectedPhase() {
        assertTrue(NegotiationReference.tryParse("", NegotiationPhase.PROPOSE, "zh-CN").isEmpty());
        assertTrue(NegotiationReference.tryParse(null, NegotiationPhase.PROPOSE, "zh-CN").isEmpty());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> NegotiationReference.tryParse("Negotiation-T/v1/information-negotiation/propose", null, "zh-CN"));
        assertEquals("Expected negotiation phase must not be null.", exception.getMessage());
    }

    private static String proposeUri(NegotiationType type) {
        return "Negotiation-T/v1/" + type.typeSegment() + "/propose";
    }

    private static String acceptRejectUri(NegotiationType type) {
        return "Negotiation-T/v1/" + type.typeSegment() + "/accept-reject";
    }

    private static NegotiationReference requirePresent(Optional<NegotiationReference> reference) {
        assertTrue(reference.isPresent(), "expected a parsed reference but the result was empty");
        return reference.get();
    }
}
