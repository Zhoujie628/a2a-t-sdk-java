package net.openan.a2at.sdk.negotiation.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;
import net.openan.a2at.sdk.negotiation.testdata.ScriptedNegotiationLlmClient;

/**
 * Happy-path property layer (design §8.3): round-trip over arbitrary legal typed content.
 *
 * <p>Every property generates a negotiation message from typed data with the production wiring, feeds the rendered
 * text into the matching validate API with an accepting scripted semantic verdict, and asserts that the merged
 * parameters carry the three context keys with the original values surviving untouched. Each property runs 1000
 * samples under a fixed seed.
 *
 * @since 2026-08
 */
class FromDataRoundTripPropertyTest {

    private static final String INFORMATION_PROPOSE_URI = "Negotiation-T/information-negotiation/propose/v1";

    private static final String TARGET_PROPOSE_URI = "Negotiation-T/target-negotiation/propose/v1";

    private static final String FEASIBILITY_PROPOSE_URI = "Negotiation-T/feasibility-negotiation/propose/v1";

    private static final String COMMON_ABORT_URI = "Negotiation-T/common/abort/v1";

    private static final Map<String, Object> FLAT_SCHEMA = PropertyHarness.objectSchema(Map.of());

    @Property(tries = 1000, seed = "20260823")
    void informationProposeRoundTripsThroughValidation(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("informationProposeContents") InformationProposeContent content) {
        roundTrip(language, context, new NegotiationProposeData(context, content), INFORMATION_PROPOSE_URI, "information");
    }

    @Property(tries = 1000, seed = "20260824")
    void targetProposeRoundTripsThroughValidation(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("targetProposeContents") TargetProposeContent content) {
        roundTrip(language, context, new NegotiationProposeData(context, content), TARGET_PROPOSE_URI, "target");
    }

    @Property(tries = 1000, seed = "20260825")
    void feasibilityProposeRoundTripsThroughValidation(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("feasibilityProposeContents") FeasibilityProposeContent content) {
        roundTrip(language, context, new NegotiationProposeData(context, content), FEASIBILITY_PROPOSE_URI, "feasibility");
    }

    @Property(tries = 1000, seed = "20260826")
    void abortRoundTripsThroughValidation(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("abortContents") NegotiationAbortContent content) {
        ScriptedNegotiationLlmClient llm =
                PropertyHarness.scripted(PropertyHarness.semanticVerdict(null, Map.of()));
        NegotiationContentService service = PropertyHarness.service(language, llm);
        TemplateUri templateUri = PropertyHarness.templateUri(COMMON_ABORT_URI);
        MetadataContent message =
                service.generateAbortFromData(new NegotiationAbortData(context, content), templateUri);
        assertMessageInvariants(message, templateUri, context);
        FilledParamData filled = service.validateAbortPromptAndDataFilling(
                message.promptText(), context, FLAT_SCHEMA, templateUri);
        assertContextSurvives(filled, context);
        assertEquals(1, llm.callCount());
    }

    private void roundTrip(
            String language,
            NegotiationContext context,
            NegotiationProposeData data,
            String rawUri,
            String negotiationType) {
        ScriptedNegotiationLlmClient llm =
                PropertyHarness.scripted(PropertyHarness.semanticVerdict(negotiationType, Map.of()));
        NegotiationContentService service = PropertyHarness.service(language, llm);
        TemplateUri templateUri = PropertyHarness.templateUri(rawUri);
        MetadataContent message = service.generateProposeFromData(data, templateUri);
        assertMessageInvariants(message, templateUri, context);
        FilledParamData filled =
                service.validateProposePromptAndDataFilling(message.promptText(), context, FLAT_SCHEMA, templateUri);
        assertContextSurvives(filled, context);
        assertEquals(1, llm.callCount());
    }

    private static void assertMessageInvariants(
            MetadataContent message, TemplateUri templateUri, NegotiationContext context) {
        assertEquals(templateUri.uri(), message.templateUri());
        assertEquals(context, message.negotiationContext());
        assertFalse(message.promptText().contains("{{"), "the rendered text must not leak unfilled slots");
        assertTrue(message.promptText() != null && !message.promptText().isBlank());
    }

    private static void assertContextSurvives(FilledParamData filled, NegotiationContext context) {
        assertEquals(context.id(), filled.data().get("id"));
        assertEquals(context.round(), filled.data().get("round"));
        assertEquals(context.maxRounds(), filled.data().get("maxRounds"));
    }

    // ------------------------------------------------------------------ providers

    @Provide
    Arbitrary<String> languages() {
        return PropertyArbitraries.languages();
    }

    @Provide
    Arbitrary<NegotiationContext> contexts() {
        return PropertyArbitraries.contexts();
    }

    @Provide
    Arbitrary<InformationProposeContent> informationProposeContents() {
        return PropertyArbitraries.informationProposeContents();
    }

    @Provide
    Arbitrary<TargetProposeContent> targetProposeContents() {
        return PropertyArbitraries.targetProposeContents();
    }

    @Provide
    Arbitrary<FeasibilityProposeContent> feasibilityProposeContents() {
        return PropertyArbitraries.feasibilityProposeContents();
    }

    @Provide
    Arbitrary<NegotiationAbortContent> abortContents() {
        return PropertyArbitraries.abortContents();
    }
}
