package net.openan.a2at.sdk.negotiation.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import org.junit.jupiter.api.Test;

/**
 * Walks the round-budget exhaustion scenario: the last allowed round is not yet exhausted, advancing past it marks the
 * context exhausted, the agent closes the negotiation with a reject message at the last allowed round and the peer
 * still validates it — while a message of a round beyond the budget fails the peer's rule gate, so no further round can
 * ever be exchanged.
 */
class MaxRoundsExhaustionScenarioTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String TARGET_ACCEPT_URI = "Negotiation-T/v1/target-negotiation/accept-reject";

    @Test
    void exhaustedRoundBudgetEndsWithARejectMessageThePeerStillAccepts() {
        ScriptedLlmClient peerLlm = new ScriptedLlmClient(SemanticResponses.acceptance("target", "{}"));
        NegotiationGenerationOrchestrator agent = orchestrator(null);
        NegotiationGenerationOrchestrator peer = orchestrator(peerLlm);

        NegotiationContext lastRound = new NegotiationContext(SESSION_ID, 5, 5);
        assertFalse(lastRound.isExhausted(), "round == maxRounds is the last usable round, not an exhausted one");
        NegotiationContext beyondBudget = lastRound.nextRound();
        assertEquals(6, beyondBudget.round());
        assertTrue(beyondBudget.isExhausted(), "round > maxRounds marks the context exhausted");
        assertEquals(5, lastRound.round(), "nextRound must leave the original context unchanged");

        MetadataContent terminal = agent.generateRejectFromData(
                new NegotiationEndingData(
                        lastRound,
                        new TargetEndingContent(NegotiationConclusion.REJECT, null, "节能目标无法在轮次预算内澄清，本次协商结束")),
                TARGET_ACCEPT_URI);
        assertTrue(terminal.promptText().contains("## 目标协商结果\nReject"));
        assertTrue(!terminal.promptText().contains("Abort"), "no abort conclusion exists in the template set");
        assertTrue(terminal.promptText().contains("- round: 5"));

        FilledParamData terminalParameters =
                peer.validateAndFillingRejectData(terminal.promptText(), parameterSchema(), TARGET_ACCEPT_URI);
        assertEquals(1, peerLlm.callCount());
        assertEquals(SESSION_ID, terminalParameters.data().get("id"));
        assertEquals(5, terminalParameters.data().get("round"));
        assertEquals(5, terminalParameters.data().get("maxRounds"));

        MetadataContent beyondBudgetMessage = agent.generateRejectFromData(
                new NegotiationEndingData(
                        beyondBudget,
                        new TargetEndingContent(
                                NegotiationConclusion.REJECT, null, "This message exceeds the round budget.")),
                TARGET_ACCEPT_URI);
        NegotiationParamExtractionException ruleFailure = assertThrows(
                NegotiationParamExtractionException.class,
                () -> peer.validateAndFillingRejectData(
                        beyondBudgetMessage.promptText(), parameterSchema(), TARGET_ACCEPT_URI));
        assertEquals(A2ATErrorCodes.NEGOTIATION_RULE_VIOLATION, ruleFailure.getCode());
        assertTrue(
                ruleFailure.getErrors().stream().anyMatch(error -> "round".equals(error.slotName())),
                "the beyond-budget round must be reported on the round slot");
        assertEquals(1, peerLlm.callCount(), "the rule gate must fail before any semantic LLM call");

        assertTrue(
                agent.getNegotiationPrompt("Negotiation-T/v1/target-negotiation/abort")
                        .isEmpty(),
                "no template of an abort phase exists anywhere");
    }

    private static NegotiationGenerationOrchestrator orchestrator(ScriptedLlmClient llm) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();
    }

    private static Map<String, Object> parameterSchema() {
        return Map.of("type", "object");
    }
}
