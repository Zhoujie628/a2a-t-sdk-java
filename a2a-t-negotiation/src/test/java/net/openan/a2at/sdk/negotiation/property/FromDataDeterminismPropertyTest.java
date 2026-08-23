package net.openan.a2at.sdk.negotiation.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;
import net.openan.a2at.sdk.negotiation.testdata.ScriptedNegotiationLlmClient;

/**
 * Determinism property layer (design §8.3): the same from-data input produces fully equal output on repeated runs.
 *
 * <p>Each property runs the same typed input through two independently assembled services — proving there is no
 * hidden per-instance state — and asserts record equality of the two {@link MetadataContent} results. The assertion
 * client proves the runs never touch the LLM. Each property runs 1000 samples under a fixed seed.
 *
 * @since 2026-08
 */
class FromDataDeterminismPropertyTest {

    @Property(tries = 1000, seed = "20260831")
    void proposeGenerationIsDeterministic(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("proposeContents") NegotiationProposeContent content) {
        NegotiationProposeData data = new NegotiationProposeData(context, content);
        TemplateUri templateUri = PropertyHarness.templateUri(proposeUriOf(content));
        MetadataContent first = service(language).generateProposeFromData(data, templateUri);
        MetadataContent second = service(language).generateProposeFromData(data, templateUri);
        assertEquals(first, second);
    }

    @Property(tries = 1000, seed = "20260901")
    void acceptGenerationIsDeterministic(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("acceptContents") NegotiationEndingContent content) {
        NegotiationEndingData data = new NegotiationEndingData(context, content);
        TemplateUri templateUri = PropertyHarness.templateUri(acceptRejectUriOf(content));
        MetadataContent first = service(language).generateAcceptFromData(data, templateUri);
        MetadataContent second = service(language).generateAcceptFromData(data, templateUri);
        assertEquals(first, second);
    }

    @Property(tries = 1000, seed = "20260902")
    void rejectGenerationIsDeterministic(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("rejectContents") NegotiationEndingContent content) {
        NegotiationEndingData data = new NegotiationEndingData(context, content);
        TemplateUri templateUri = PropertyHarness.templateUri(acceptRejectUriOf(content));
        MetadataContent first = service(language).generateRejectFromData(data, templateUri);
        MetadataContent second = service(language).generateRejectFromData(data, templateUri);
        assertEquals(first, second);
    }

    @Property(tries = 1000, seed = "20260903")
    void abortGenerationIsDeterministic(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("abortContents") NegotiationAbortContent content) {
        NegotiationAbortData data = new NegotiationAbortData(context, content);
        TemplateUri templateUri = PropertyHarness.templateUri("Negotiation-T/common/abort/v1");
        MetadataContent first = service(language).generateAbortFromData(data, templateUri);
        MetadataContent second = service(language).generateAbortFromData(data, templateUri);
        assertEquals(first, second);
    }

    private static NegotiationContentService service(String language) {
        // The assertion-only client doubles as the zero-LLM-call proof of the from-data family.
        return PropertyHarness.service(language, ScriptedNegotiationLlmClient.assertionOnly());
    }

    private static String proposeUriOf(NegotiationProposeContent content) {
        if (content instanceof InformationProposeContent) {
            return "Negotiation-T/information-negotiation/propose/v1";
        }
        if (content instanceof TargetProposeContent) {
            return "Negotiation-T/target-negotiation/propose/v1";
        }
        return "Negotiation-T/feasibility-negotiation/propose/v1";
    }

    private static String acceptRejectUriOf(NegotiationEndingContent content) {
        if (content instanceof InformationEndingContent) {
            return "Negotiation-T/information-negotiation/accept-reject/v1";
        }
        if (content instanceof TargetEndingContent) {
            return "Negotiation-T/target-negotiation/accept-reject/v1";
        }
        return "Negotiation-T/feasibility-negotiation/accept-reject/v1";
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
    Arbitrary<NegotiationProposeContent> proposeContents() {
        return PropertyArbitraries.proposeContents();
    }

    @Provide
    Arbitrary<NegotiationEndingContent> acceptContents() {
        return PropertyArbitraries.endingContents(NegotiationConclusion.ACCEPT);
    }

    @Provide
    Arbitrary<NegotiationEndingContent> rejectContents() {
        return PropertyArbitraries.endingContents(NegotiationConclusion.REJECT);
    }

    @Provide
    Arbitrary<NegotiationAbortContent> abortContents() {
        return PropertyArbitraries.abortContents();
    }
}
