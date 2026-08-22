package net.openan.a2at.sdk.negotiation.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.validation.StandardTemplates;
import net.openan.a2at.sdk.core.validation.TemplateUri;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.negotiation.content.InfoEndingContent;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import org.junit.jupiter.api.Test;

/**
 * Walks the information negotiation scenario: a missing-information task is rejected as not negotiable, the requesting
 * agent asks for the missing items, the answering agent validates the request, delivers the information and accepts,
 * and the requester validates the terminal answer — all within one shared round.
 */
class InformationNegotiationScenarioTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final TemplateUri INFORMATION_ACCEPT_URI = StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;

    @Test
    void informationNegotiationReachesTheTerminalStateWithinOneRound() {
        ScriptedLlmClient requesterLlm =
                new ScriptedLlmClient(SemanticResponses.acceptance("information", "{\"region\":\"松山湖\"}"));
        ScriptedLlmClient responderLlm =
                new ScriptedLlmClient(SemanticResponses.acceptance("information", "{\"region\":\"松山湖\"}"));
        NegotiationGenerationOrchestrator requester = orchestrator(requesterLlm);
        NegotiationGenerationOrchestrator responder = orchestrator(responderLlm);

        NegotiationParamExtractionException notNegotiable = assertThrows(
                NegotiationParamExtractionException.class,
                () -> responder.validateAndFillingProposeData(
                        "A task prompt describing an energy-saving optimization without any negotiation section.",
                        parameterSchema(),
                        INFORMATION_PROPOSE_URI));
        assertEquals(A2ATErrorCodes.NEGOTIATION_INVALID_INPUT, notNegotiable.getCode());
        assertEquals(
                0,
                responderLlm.callCount(),
                "a text without a negotiation context section must fail before any LLM call");

        NegotiationContext round = new NegotiationContext(SESSION_ID, 1, 5);
        MetadataContent request = requester.generateProposeFromData(
                new NegotiationProposeData(
                        round,
                        new InfoProposeContent(
                                List.of(
                                        new NegotiationItem("节能区域信息", "如松山湖"),
                                        new NegotiationItem("节能速率保障目标", "如20Mbps")),
                                null)),
                INFORMATION_PROPOSE_URI);
        assertTrue(request.promptText().contains("## 所需信息项"));
        assertTrue(request.promptText().contains("1. 节能区域信息：如松山湖"));
        assertTrue(request.promptText().contains("- round: 1"));

        FilledParamData requestParameters = responder.validateAndFillingProposeData(
                request.promptText(), parameterSchema(), INFORMATION_PROPOSE_URI);
        assertEquals(1, responderLlm.callCount());
        assertEquals(SESSION_ID, requestParameters.data().get("id"));
        assertEquals(1, requestParameters.data().get("round"));
        assertEquals(5, requestParameters.data().get("maxRounds"));
        assertEquals("松山湖", requestParameters.data().get("region"));

        MetadataContent answer = responder.generateAcceptFromData(
                new NegotiationEndingData(
                        round,
                        new InfoEndingContent(
                                NegotiationConclusion.ACCEPT,
                                List.of(
                                        new NegotiationItem("节能区域信息", "松山湖"),
                                        new NegotiationItem("节能速率保障目标", "20Mbps")))),
                INFORMATION_ACCEPT_URI);
        assertTrue(answer.promptText().contains("## 信息协商结果\nAccept"));
        assertTrue(answer.promptText().contains("## 信息协商结果内容"));
        assertTrue(answer.promptText().contains("1. 节能区域信息：松山湖"));
        assertTrue(answer.promptText().contains("- round: 1"), "the terminal answer shares the round of the request");

        FilledParamData answerParameters =
                requester.validateAndFillingAcceptData(answer.promptText(), parameterSchema(), INFORMATION_ACCEPT_URI);
        assertEquals(1, requesterLlm.callCount());
        assertEquals(SESSION_ID, answerParameters.data().get("id"));
        assertEquals(1, answerParameters.data().get("round"));
        assertEquals("松山湖", answerParameters.data().get("region"));
    }

    private static NegotiationGenerationOrchestrator orchestrator(ScriptedLlmClient llm) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();
    }

    private static Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of("region", Map.of("type", "string")));
    }
}
