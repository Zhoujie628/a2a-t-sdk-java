package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
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
                        new InformationProposeContent(List.of(), null)));
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
                        new InformationEndingContent(NegotiationConclusion.ACCEPT, List.of())));
        assertInstanceOf(
                InformationEndingGenerator.class,
                registry.resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPhase.REJECT,
                        new InformationEndingContent(NegotiationConclusion.REJECT, List.of())));
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
        IllegalArgumentException proposeInEnding = assertThrows(
                IllegalArgumentException.class,
                () -> registry.resolve(
                        NegotiationType.INFORMATION, NegotiationPhase.ACCEPT, new InformationProposeContent(List.of(), null)));
        assertTrue(proposeInEnding
                .getMessage()
                .contains("ACCEPT phase requires ending content but received propose content of type"
                        + " InformationProposeContent"));

        IllegalArgumentException endingInPropose = assertThrows(
                IllegalArgumentException.class,
                () -> registry.resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPhase.PROPOSE,
                        new InformationEndingContent(NegotiationConclusion.ACCEPT, List.of())));
        assertTrue(endingInPropose
                .getMessage()
                .contains("PROPOSE phase requires propose content but received ending content of type"
                        + " InformationEndingContent"));
    }

    @Test
    void rejectsContentRuntimeTypeNotMatchingTheNegotiationType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPhase.PROPOSE,
                        new TargetProposeContent("描述", null, null, null)));

        assertTrue(exception
                .getMessage()
                .contains("Negotiation type INFORMATION requires content of type InformationProposeContent but received"
                        + " TargetProposeContent"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.resolve(
                        NegotiationType.TARGET,
                        NegotiationPhase.ACCEPT,
                        new InformationEndingContent(NegotiationConclusion.ACCEPT, List.of())));
    }

    @Test
    void rejectsEndingConclusionNotMatchingThePhase() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPhase.ACCEPT,
                        new InformationEndingContent(NegotiationConclusion.REJECT, List.of())));

        assertTrue(exception
                .getMessage()
                .contains("ACCEPT phase requires conclusion Accept but the content carries Reject"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.resolve(
                        NegotiationType.TARGET,
                        NegotiationPhase.REJECT,
                        new TargetEndingContent(NegotiationConclusion.ACCEPT, "确认的意图", null)));
    }

    @Test
    void rejectsEndingContentWithoutConclusion() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> registry.resolve(
                        NegotiationType.INFORMATION, NegotiationPhase.ACCEPT, new InformationEndingContent(null, List.of())));

        assertTrue(exception.getMessage().contains("conclusion must not be null"));
    }

    @Test
    void rejectsNullArguments() {
        NegotiationContent content = new InformationProposeContent(List.of(), null);

        assertEquals(
                "Negotiation type must not be null.",
                assertThrows(
                                NullPointerException.class,
                                () -> registry.resolve(null, NegotiationPhase.PROPOSE, content))
                        .getMessage());
        assertEquals(
                "Negotiation phase must not be null.",
                assertThrows(
                                NullPointerException.class,
                                () -> registry.resolve(NegotiationType.INFORMATION, null, content))
                        .getMessage());
        assertEquals(
                "Negotiation content must not be null.",
                assertThrows(
                                NullPointerException.class,
                                () -> registry.resolve(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, null))
                        .getMessage());
    }
}
