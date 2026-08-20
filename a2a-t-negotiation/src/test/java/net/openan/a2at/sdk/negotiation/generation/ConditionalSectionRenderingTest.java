package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the conditional sections of the propose templates against direct (non-golden) assertions.
 *
 * <p>The feasibility propose template renders exactly one of its two conditional sections, selected by the action; the
 * target propose template renders the intent understanding section only on the first round, the alignment and
 * clarification section only on later rounds, and drops the clarification request section entirely when no
 * clarification items are present.
 */
class ConditionalSectionRenderingTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String FEASIBILITY_PROPOSE_URI = "Negotiation-T/v1/feasibility-negotiation/propose";

    private static final String TARGET_PROPOSE_URI = "Negotiation-T/v1/target-negotiation/propose";

    static Stream<Arguments> languages() {
        return Stream.of(Arguments.of("zh-CN"), Arguments.of("en-US"));
    }

    @ParameterizedTest(name = "evaluation request renders only the evaluate section [{0}]")
    @MethodSource("languages")
    void evaluationRequestRendersOnlyTheContentsToEvaluateSection(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String promptText = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5),
                                new FeasibilityProposeContent(
                                        "Please assess the adjusted rate target.",
                                        NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                        List.of(new NegotiationItem("adjusted target", "rate lowered to 2Mbps")),
                                        null)),
                        FEASIBILITY_PROPOSE_URI)
                .promptText();

        assertTrue(promptText.contains(evaluateTitle(language)));
        assertFalse(promptText.contains(infeasibleTitle(language)));
    }

    @ParameterizedTest(name = "alternative proposal renders only the infeasibility section [{0}]")
    @MethodSource("languages")
    void alternativeProposalRendersOnlyTheInfeasibilitySection(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String promptText = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 2, 5),
                                new FeasibilityProposeContent(
                                        "The rate target is infeasible; a proposal follows.",
                                        NegotiationAction.PROPOSE_ALTERNATIVE_ON_FAILURE,
                                        null,
                                        List.of(new NegotiationItem(
                                                "proposal", "lower the rate guarantee target to 2Mbps")))),
                        FEASIBILITY_PROPOSE_URI)
                .promptText();

        assertTrue(promptText.contains(infeasibleTitle(language)));
        assertFalse(promptText.contains(evaluateTitle(language)));
    }

    @ParameterizedTest(name = "round 1 renders the intent section without the alignment section [{0}]")
    @MethodSource("languages")
    void firstRoundRendersIntentWithoutAlignment(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String promptText = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5),
                                new TargetProposeContent(
                                        "Clarify the intent of the energy-saving task.",
                                        List.of(new NegotiationItem("task intent", "energy-saving optimization")),
                                        null,
                                        null)),
                        TARGET_PROPOSE_URI)
                .promptText();

        assertTrue(promptText.contains(intentTitle(language)));
        assertFalse(promptText.contains(alignmentTitle(language)));
    }

    @ParameterizedTest(name = "later rounds render the alignment section without the intent section [{0}]")
    @MethodSource("languages")
    void laterRoundsRenderAlignmentWithoutIntent(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String promptText = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 3, 5),
                                new TargetProposeContent(
                                        "Clarify the intent of the energy-saving task.",
                                        null,
                                        List.of(new NegotiationItem("task intent", "confirmed as correct")),
                                        null)),
                        TARGET_PROPOSE_URI)
                .promptText();

        assertTrue(promptText.contains(alignmentTitle(language)));
        assertFalse(promptText.contains(intentTitle(language)));
    }

    @ParameterizedTest(name = "empty clarification list drops the clarification section [{0}]")
    @MethodSource("languages")
    void emptyClarificationListDropsTheClarificationSection(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String withoutClarification = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5),
                                new TargetProposeContent(
                                        "Confirm the understood intent of the energy-saving task.",
                                        List.of(new NegotiationItem("task intent", "energy-saving optimization")),
                                        null,
                                        null)),
                        TARGET_PROPOSE_URI)
                .promptText();
        assertFalse(withoutClarification.contains(clarificationTitle(language)));

        String withClarification = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5),
                                new TargetProposeContent(
                                        "Confirm the understood intent of the energy-saving task.",
                                        List.of(new NegotiationItem("task intent", "energy-saving optimization")),
                                        null,
                                        List.of(new NegotiationItem("area", "which site is covered")))),
                        TARGET_PROPOSE_URI)
                .promptText();
        assertTrue(withClarification.contains(clarificationTitle(language)));
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .build();
    }

    private static String evaluateTitle(String language) {
        return "zh-CN".equals(language) ? "## 待评估内容说明" : "## Contents to Evaluate";
    }

    private static String infeasibleTitle(String language) {
        return "zh-CN".equals(language) ? "## 评估不可行时的详情和提案" : "## Infeasibility Details and Proposal";
    }

    private static String intentTitle(String language) {
        return "zh-CN".equals(language) ? "## 意图理解陈述" : "## Intent Understanding";
    }

    private static String alignmentTitle(String language) {
        return "zh-CN".equals(language) ? "## 理解对齐与疑问澄清" : "## Alignment and Clarification";
    }

    private static String clarificationTitle(String language) {
        return "zh-CN".equals(language) ? "## 待澄清内容" : "## Clarification Required";
    }
}
