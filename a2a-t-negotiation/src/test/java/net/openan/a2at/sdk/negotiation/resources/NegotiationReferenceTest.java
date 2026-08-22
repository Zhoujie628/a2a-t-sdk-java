package net.openan.a2at.sdk.negotiation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.openan.a2at.sdk.core.validation.StandardTemplates;
import net.openan.a2at.sdk.core.validation.TemplateUri;
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
                StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri(),
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "en-US").uri());
        assertEquals(
                StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT.uri(),
                new NegotiationReference(NegotiationType.TARGET, NegotiationPhase.ACCEPT, "zh-CN").uri());
        assertEquals(
                StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT.uri(),
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
                        "Negotiation-T/information-negotiation/propose/v1/extra", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse("foo", NegotiationPhase.PROPOSE, "zh-CN").isEmpty());
    }

    @Test
    void tryParseRejectsWrongPrefixAndVersion() {
        assertTrue(NegotiationReference.tryParse(
                        "Task-T/information-negotiation/propose/v1", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "negotiation-t/information-negotiation/propose/v1", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/information-negotiation/propose/v2", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsMissingTypeSegmentSuffixAndUnderscoreVariant() {
        assertTrue(NegotiationReference.tryParse("Negotiation-T/information/propose/v1", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/information_negotiation/propose/v1", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsUnknownType() {
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/unknown-negotiation/propose/v1", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsIllegalPhaseSegment() {
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/information-negotiation/propose-x/v1", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/information-negotiation/accept/v1", NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsPhaseMismatchAgainstExpectedPhase() {
        Optional<NegotiationReference> parsed = NegotiationReference.tryParse(
                StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri(), NegotiationPhase.ACCEPT, "zh-CN");

        assertTrue(parsed.isEmpty());
    }

    @Test
    void tryParseReturnsEmptyForBlankAndNullUriButThrowsOnNullExpectedPhase() {
        assertTrue(NegotiationReference.tryParse("", NegotiationPhase.PROPOSE, "zh-CN").isEmpty());
        assertTrue(NegotiationReference.tryParse(null, NegotiationPhase.PROPOSE, "zh-CN").isEmpty());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> NegotiationReference.tryParse(StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri(), null, "zh-CN"));
        assertEquals("Expected negotiation phase must not be null.", exception.getMessage());
    }

    @Test
    void fromTemplateUriAcceptsAllSixTypedUris() {
        for (NegotiationType type : NegotiationType.values()) {
            NegotiationReference propose = requirePresent(
                    NegotiationReference.fromTemplateUri(proposeTemplate(type), NegotiationPhase.PROPOSE, "zh-CN"));
            assertEquals(type, propose.type());
            assertEquals(NegotiationPhase.PROPOSE, propose.phase());
            assertEquals(proposeUri(type), propose.uri());
            assertEquals("zh-CN", propose.language());

            NegotiationReference reject = requirePresent(
                    NegotiationReference.fromTemplateUri(acceptRejectTemplate(type), NegotiationPhase.REJECT, "en-US"));
            assertEquals(type, reject.type());
            assertEquals(NegotiationPhase.REJECT, reject.phase());
            assertEquals(acceptRejectUri(type), reject.uri());
        }
    }

    @Test
    void fromTemplateUriRejectsNonNegotiationUris() {
        assertTrue(NegotiationReference.fromTemplateUri(
                        StandardTemplates.ENERGY_SAVING, NegotiationPhase.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("negotiation-t", "v1", "information-negotiation", "propose"),
                        NegotiationPhase.PROPOSE,
                        "zh-CN")
                .isEmpty());
    }

    @Test
    void fromTemplateUriRejectsWrongPathSegmentCount() {
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "v1", "information-negotiation"),
                        NegotiationPhase.PROPOSE,
                        "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "v1", "information-negotiation", "propose", "extra"),
                        NegotiationPhase.PROPOSE,
                        "zh-CN")
                .isEmpty());
    }

    @Test
    void fromTemplateUriRejectsWrongVersionTypeAndPhaseSegments() {
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "v2", "information-negotiation", "propose"),
                        NegotiationPhase.PROPOSE,
                        "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "v1", "information", "propose"),
                        NegotiationPhase.PROPOSE,
                        "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "v1", "information_negotiation", "propose"),
                        NegotiationPhase.PROPOSE,
                        "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "v1", "unknown-negotiation", "propose"),
                        NegotiationPhase.PROPOSE,
                        "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "v1", "information-negotiation", "propose-x"),
                        NegotiationPhase.PROPOSE,
                        "zh-CN")
                .isEmpty());
    }

    @Test
    void fromTemplateUriRejectsPhaseMismatchAndThrowsOnNullArguments() {
        assertTrue(NegotiationReference.fromTemplateUri(
                        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE, NegotiationPhase.ACCEPT, "zh-CN")
                .isEmpty());

        NullPointerException uriFailure = assertThrows(
                NullPointerException.class,
                () -> NegotiationReference.fromTemplateUri(null, NegotiationPhase.PROPOSE, "zh-CN"));
        assertEquals("Template URI must not be null.", uriFailure.getMessage());

        NullPointerException phaseFailure = assertThrows(
                NullPointerException.class,
                () -> NegotiationReference.fromTemplateUri(
                        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE, null, "zh-CN"));
        assertEquals("Expected negotiation phase must not be null.", phaseFailure.getMessage());
    }

    private static TemplateUri proposeTemplate(NegotiationType type) {
        return TemplateUri.of("Negotiation-T", "v1", type.typeSegment(), "propose");
    }

    private static TemplateUri acceptRejectTemplate(NegotiationType type) {
        return TemplateUri.of("Negotiation-T", "v1", type.typeSegment(), "accept-reject");
    }

    private static String proposeUri(NegotiationType type) {
        return "Negotiation-T/" + type.typeSegment() + "/propose/v1";
    }

    private static String acceptRejectUri(NegotiationType type) {
        return "Negotiation-T/" + type.typeSegment() + "/accept-reject/v1";
    }

    private static NegotiationReference requirePresent(Optional<NegotiationReference> reference) {
        assertTrue(reference.isPresent(), "expected a parsed reference but the result was empty");
        return reference.get();
    }
}
