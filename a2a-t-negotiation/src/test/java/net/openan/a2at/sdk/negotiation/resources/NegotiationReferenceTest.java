package net.openan.a2at.sdk.negotiation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.negotiation.content.NegotiationContentException;
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
    void parseAcceptsAllSixValidUris() {
        for (NegotiationType type : NegotiationType.values()) {
            NegotiationReference propose =
                    NegotiationReference.parse(proposeUri(type), NegotiationPhase.PROPOSE, "zh-CN");
            assertEquals(type, propose.type());
            assertEquals(NegotiationPhase.PROPOSE, propose.phase());
            assertEquals(proposeUri(type), propose.uri());
            assertEquals("zh-CN", propose.language());

            NegotiationReference accept =
                    NegotiationReference.parse(acceptRejectUri(type), NegotiationPhase.ACCEPT, "en-US");
            assertEquals(type, accept.type());
            assertEquals(NegotiationPhase.ACCEPT, accept.phase());
            assertEquals(acceptRejectUri(type), accept.uri());

            NegotiationReference reject =
                    NegotiationReference.parse(acceptRejectUri(type), NegotiationPhase.REJECT, "en-US");
            assertEquals(type, reject.type());
            assertEquals(NegotiationPhase.REJECT, reject.phase());
            assertEquals(acceptRejectUri(type), reject.uri());
        }
    }

    @Test
    void parseRejectsWrongSegmentCount() {
        assertParseFailure("information-negotiation/propose", NegotiationPhase.PROPOSE, "segments");
        assertParseFailure(
                "Negotiation-T/v1/information-negotiation/propose/extra", NegotiationPhase.PROPOSE, "segments");
        assertParseFailure("foo", NegotiationPhase.PROPOSE, "segments");
    }

    @Test
    void parseRejectsWrongPrefixAndVersion() {
        assertParseFailure("Task-T/v1/information-negotiation/propose", NegotiationPhase.PROPOSE, "Negotiation-T");
        assertParseFailure(
                "negotiation-t/v1/information-negotiation/propose", NegotiationPhase.PROPOSE, "Negotiation-T");
        assertParseFailure("Negotiation-T/v2/information-negotiation/propose", NegotiationPhase.PROPOSE, "v1");
    }

    @Test
    void parseRejectsMissingTypeSegmentSuffixAndUnderscoreVariant() {
        assertParseFailure("Negotiation-T/v1/information/propose", NegotiationPhase.PROPOSE, "-negotiation");
        assertParseFailure(
                "Negotiation-T/v1/information_negotiation/propose", NegotiationPhase.PROPOSE, "-negotiation");
    }

    @Test
    void parseRejectsUnknownType() {
        assertParseFailure("Negotiation-T/v1/unknown-negotiation/propose", NegotiationPhase.PROPOSE, "unknown");
    }

    @Test
    void parseRejectsIllegalPhaseSegment() {
        assertParseFailure("Negotiation-T/v1/information-negotiation/propose-x", NegotiationPhase.PROPOSE, "phase");
        assertParseFailure("Negotiation-T/v1/information-negotiation/accept", NegotiationPhase.PROPOSE, "phase");
    }

    @Test
    void parseRejectsPhaseMismatchAgainstExpectedPhase() {
        NegotiationContentException exception = assertThrows(
                NegotiationContentException.class,
                () -> NegotiationReference.parse(
                        "Negotiation-T/v1/information-negotiation/propose", NegotiationPhase.ACCEPT, "zh-CN"));

        assertEquals("templateUri", exception.getField());
        assertTrue(exception.getMessage().contains("does not match the expected phase"));
        assertTrue(exception.getMessage().contains("propose"));
    }

    @Test
    void parseRejectsBlankUriAndNullExpectedPhase() {
        NegotiationContentException blankException = assertThrows(
                NegotiationContentException.class,
                () -> NegotiationReference.parse("", NegotiationPhase.PROPOSE, "zh-CN"));
        assertEquals("templateUri", blankException.getField());

        NegotiationContentException nullUriException = assertThrows(
                NegotiationContentException.class,
                () -> NegotiationReference.parse(null, NegotiationPhase.PROPOSE, "zh-CN"));
        assertEquals("templateUri", nullUriException.getField());

        NegotiationContentException nullPhaseException = assertThrows(
                NegotiationContentException.class,
                () -> NegotiationReference.parse("Negotiation-T/v1/information-negotiation/propose", null, "zh-CN"));
        assertEquals("phase", nullPhaseException.getField());
    }

    @Test
    void parseFailureMessagesAreDistinguishable() {
        List<String> messages = List.of(
                parseErrorMessage("information-negotiation/propose", NegotiationPhase.PROPOSE),
                parseErrorMessage("Task-T/v1/information-negotiation/propose", NegotiationPhase.PROPOSE),
                parseErrorMessage("Negotiation-T/v2/information-negotiation/propose", NegotiationPhase.PROPOSE),
                parseErrorMessage("Negotiation-T/v1/information/propose", NegotiationPhase.PROPOSE),
                parseErrorMessage("Negotiation-T/v1/unknown-negotiation/propose", NegotiationPhase.PROPOSE),
                parseErrorMessage("Negotiation-T/v1/information-negotiation/propose-x", NegotiationPhase.PROPOSE));

        assertEquals(messages.size(), messages.stream().distinct().count());
    }

    private static String proposeUri(NegotiationType type) {
        return "Negotiation-T/v1/" + type.typeSegment() + "/propose";
    }

    private static String acceptRejectUri(NegotiationType type) {
        return "Negotiation-T/v1/" + type.typeSegment() + "/accept-reject";
    }

    private static void assertParseFailure(String templateUri, NegotiationPhase expectedPhase, String messageFragment) {
        NegotiationContentException exception = assertThrows(
                NegotiationContentException.class,
                () -> NegotiationReference.parse(templateUri, expectedPhase, "zh-CN"));

        assertEquals("templateUri", exception.getField());
        assertTrue(
                exception.getMessage().contains(messageFragment),
                "expected message containing '" + messageFragment + "' but was: " + exception.getMessage());
    }

    private static String parseErrorMessage(String templateUri, NegotiationPhase expectedPhase) {
        return assertThrows(
                        NegotiationContentException.class,
                        () -> NegotiationReference.parse(templateUri, expectedPhase, "zh-CN"))
                .getMessage();
    }
}
