package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InfoEndingContent;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContentException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import org.junit.jupiter.api.Test;

class NegotiationGeneratorRegistryTest {

    private final NegotiationGeneratorRegistry registry = new NegotiationGeneratorRegistry();

    @Test
    void dispatchesEveryTypeAndPhaseCombination() {
        assertInstanceOf(
                InformationProposeGenerator.class,
                registry.resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPhase.PROPOSE,
                        new InfoProposeContent(List.of(), null)));
        assertInstanceOf(
                TargetProposeGenerator.class,
                registry.resolve(
                        NegotiationType.TARGET,
                        NegotiationPhase.PROPOSE,
                        new TargetProposeContent("描述", null, null, null)));
        assertInstanceOf(
                FeasibilityProposeGenerator.class,
                registry.resolve(
                        NegotiationType.FEASIBILITY,
                        NegotiationPhase.PROPOSE,
                        new FeasibilityProposeContent(
                                "描述",
                                NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                List.of(new NegotiationItem("名称", "值")),
                                null)));
        assertInstanceOf(
                InformationEndingGenerator.class,
                registry.resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPhase.ACCEPT,
                        new InfoEndingContent(NegotiationConclusion.ACCEPT, List.of())));
        assertInstanceOf(
                InformationEndingGenerator.class,
                registry.resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPhase.REJECT,
                        new InfoEndingContent(NegotiationConclusion.REJECT, List.of())));
        assertInstanceOf(
                TargetEndingGenerator.class,
                registry.resolve(
                        NegotiationType.TARGET,
                        NegotiationPhase.ACCEPT,
                        new TargetEndingContent(NegotiationConclusion.ACCEPT, "确认的意图", null)));
        assertInstanceOf(
                TargetEndingGenerator.class,
                registry.resolve(
                        NegotiationType.TARGET,
                        NegotiationPhase.REJECT,
                        new TargetEndingContent(NegotiationConclusion.REJECT, null, "失败原因")));
        assertInstanceOf(
                FeasibilityEndingGenerator.class,
                registry.resolve(
                        NegotiationType.FEASIBILITY,
                        NegotiationPhase.ACCEPT,
                        new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, "评估结论")));
        assertInstanceOf(
                FeasibilityEndingGenerator.class,
                registry.resolve(
                        NegotiationType.FEASIBILITY,
                        NegotiationPhase.REJECT,
                        new FeasibilityEndingContent(NegotiationConclusion.REJECT, "评估结论")));
    }

    @Test
    void rejectsProposeContentInTerminalPhaseAndEndingContentInProposePhase() {
        NegotiationContentException proposeInEnding = assertThrows(
                NegotiationContentException.class,
                () -> registry.resolve(
                        NegotiationType.INFORMATION, NegotiationPhase.ACCEPT, new InfoProposeContent(List.of(), null)));
        assertEquals("content", proposeInEnding.getField());
        assertTrue(proposeInEnding
                .getMessage()
                .contains("ACCEPT phase requires ending content but received propose content of type"
                        + " InfoProposeContent"));

        NegotiationContentException endingInPropose = assertThrows(
                NegotiationContentException.class,
                () -> registry.resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPhase.PROPOSE,
                        new InfoEndingContent(NegotiationConclusion.ACCEPT, List.of())));
        assertEquals("content", endingInPropose.getField());
        assertTrue(endingInPropose
                .getMessage()
                .contains("PROPOSE phase requires propose content but received ending content of type"
                        + " InfoEndingContent"));
    }

    @Test
    void rejectsContentRuntimeTypeNotMatchingTheNegotiationType() {
        NegotiationContentException exception = assertThrows(
                NegotiationContentException.class,
                () -> registry.resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPhase.PROPOSE,
                        new TargetProposeContent("描述", null, null, null)));

        assertEquals("content", exception.getField());
        assertTrue(exception
                .getMessage()
                .contains("Negotiation type INFORMATION requires content of type InfoProposeContent but received"
                        + " TargetProposeContent"));

        assertThrows(
                NegotiationContentException.class,
                () -> registry.resolve(
                        NegotiationType.TARGET,
                        NegotiationPhase.ACCEPT,
                        new InfoEndingContent(NegotiationConclusion.ACCEPT, List.of())));
    }

    @Test
    void rejectsEndingConclusionNotMatchingThePhase() {
        NegotiationContentException exception = assertThrows(
                NegotiationContentException.class,
                () -> registry.resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPhase.ACCEPT,
                        new InfoEndingContent(NegotiationConclusion.REJECT, List.of())));

        assertEquals("content.conclusion", exception.getField());
        assertTrue(exception
                .getMessage()
                .contains("ACCEPT phase requires conclusion Accept but the content carries Reject"));

        assertThrows(
                NegotiationContentException.class,
                () -> registry.resolve(
                        NegotiationType.TARGET,
                        NegotiationPhase.REJECT,
                        new TargetEndingContent(NegotiationConclusion.ACCEPT, "确认的意图", null)));
    }

    @Test
    void rejectsEndingContentWithoutConclusion() {
        NegotiationContentException exception = assertThrows(
                NegotiationContentException.class,
                () -> registry.resolve(
                        NegotiationType.INFORMATION, NegotiationPhase.ACCEPT, new InfoEndingContent(null, List.of())));

        assertEquals("content.conclusion", exception.getField());
    }

    @Test
    void rejectsNullArguments() {
        NegotiationContent content = new InfoProposeContent(List.of(), null);

        assertEquals(
                "type",
                assertThrows(
                                NegotiationContentException.class,
                                () -> registry.resolve(null, NegotiationPhase.PROPOSE, content))
                        .getField());
        assertEquals(
                "phase",
                assertThrows(
                                NegotiationContentException.class,
                                () -> registry.resolve(NegotiationType.INFORMATION, null, content))
                        .getField());
        assertEquals(
                "content",
                assertThrows(
                                NegotiationContentException.class,
                                () -> registry.resolve(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, null))
                        .getField());
    }
}
