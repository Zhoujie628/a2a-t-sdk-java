package net.openan.a2at.sdk.negotiation.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import org.junit.jupiter.api.Test;

/**
 * Walks the target negotiation scenario across two rounds: the first-round proposal carries the intent understanding
 * and an open clarification request, the responder rejects it, the requester advances to the second round immutably and
 * switches to the alignment section, and the responder finally accepts with the confirmed intent. The responder also
 * extracts a nested time-rate target list from the first-round proposal.
 */
class TargetNegotiationScenarioTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri TARGET_PROPOSE_URI = StandardTemplates.TARGET_NEGOTIATION_PROPOSE;

    private static final TemplateUri TARGET_ACCEPT_URI = StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT;

    @Test
    void targetNegotiationSwitchesConditionalSectionsAcrossRounds() {
        ScriptedLlmClient requesterLlm = new ScriptedLlmClient(
                SemanticResponses.acceptance("target", "{}"),
                SemanticResponses.acceptance("target", "{\"confirmedIntent\":\"08:00-18:00对松山湖站点启用无线节能优化\"}"));
        ScriptedLlmClient responderLlm = new ScriptedLlmClient(SemanticResponses.acceptance(
                "target",
                "{\"timeRateTargets\":[{\"time\":\"08:00-18:00\",\"rate\":\"20Mbps\"},"
                        + "{\"time\":\"00:00-06:00\",\"rate\":\"5Mbps\"}]}"));
        NegotiationGenerationOrchestrator requester = orchestrator(requesterLlm);
        NegotiationGenerationOrchestrator responder = orchestrator(responderLlm);

        NegotiationContext firstRound = new NegotiationContext(SESSION_ID, 1, 5);
        MetadataContent firstProposal = requester.generateProposeFromData(
                new NegotiationProposeData(
                        firstRound,
                        new TargetProposeContent(
                                "对无线节能优化任务的意图理解参见<意图理解陈述>，对节能区域存在疑问，详见<待澄清内容>，请澄清和确认",
                                List.of(new NegotiationItem("任务意图", "08:00-18:00对目标站点启用无线节能优化")),
                                null,
                                List.of(new NegotiationItem("节能区域", "松山湖还是其他站点")))),
                TARGET_PROPOSE_URI);
        assertTrue(
                firstProposal.promptText().contains("## 意图理解陈述"),
                "round 1 must carry the intent understanding section");
        assertTrue(
                !firstProposal.promptText().contains("## 理解对齐与疑问澄清"), "round 1 must not carry the alignment section");
        assertTrue(firstProposal.promptText().contains("## 待澄清内容"));
        assertEquals(1, firstProposal.negotiationContext().round());

        FilledParamData firstRoundParameters = responder.validateProposePromptAndDataFilling(
                firstProposal.promptText(), firstRound, nestedParameterSchema(), TARGET_PROPOSE_URI);
        assertEquals(1, responderLlm.callCount());
        Object timeRateTargets = firstRoundParameters.data().get("timeRateTargets");
        assertTrue(timeRateTargets instanceof List, "the nested parameter list must pass through");
        List<?> targetList = (List<?>) timeRateTargets;
        assertEquals(2, targetList.size());
        assertTrue(targetList.get(0) instanceof Map);
        assertEquals("08:00-18:00", ((Map<?, ?>) targetList.get(0)).get("time"));
        assertEquals("20Mbps", ((Map<?, ?>) targetList.get(0)).get("rate"));

        MetadataContent rejection = responder.generateRejectFromData(
                new NegotiationEndingData(
                        firstRound,
                        new TargetEndingContent(NegotiationConclusion.REJECT, null, "节能区域信息因站点清单不可用而无法完整澄清")),
                TARGET_ACCEPT_URI);
        assertTrue(rejection.promptText().contains("## 目标协商结果\nReject"));
        assertTrue(rejection.promptText().contains("节能区域信息因站点清单不可用而无法完整澄清"));
        assertEquals(1, rejection.negotiationContext().round());

        requester.validateRejectPromptAndDataFilling(
                rejection.promptText(), firstRound, parameterSchema(), TARGET_ACCEPT_URI);
        assertEquals(1, requesterLlm.callCount());

        NegotiationContext secondRound = firstRound.nextRound();
        assertEquals(2, secondRound.round());
        assertEquals(1, firstRound.round(), "nextRound must leave the original context unchanged");

        MetadataContent secondProposal = requester.generateProposeFromData(
                new NegotiationProposeData(
                        secondRound,
                        new TargetProposeContent(
                                "已针对节能区域提供澄清，详见<理解对齐与疑问澄清>，请确认",
                                null,
                                List.of(new NegotiationItem("节能区域", "confirmed（认同）：松山湖")),
                                null)),
                TARGET_PROPOSE_URI);
        assertTrue(secondProposal.promptText().contains("## 理解对齐与疑问澄清"), "round 2 must carry the alignment section");
        assertTrue(
                !secondProposal.promptText().contains("## 意图理解陈述"),
                "round 2 must not carry the intent understanding section");
        assertTrue(
                !secondProposal.promptText().contains("## 待澄清内容"),
                "an empty clarification list must drop the whole section");
        assertEquals(2, secondProposal.negotiationContext().round());

        MetadataContent acceptance = responder.generateAcceptFromData(
                new NegotiationEndingData(
                        secondRound,
                        new TargetEndingContent(
                                NegotiationConclusion.ACCEPT, "最终确认意图：08:00-18:00对松山湖站点启用无线节能优化，速率保障不低于20Mbps", null)),
                TARGET_ACCEPT_URI);
        assertTrue(acceptance.promptText().contains("## 目标协商结果\nAccept"));
        assertTrue(acceptance.promptText().contains("最终确认意图：08:00-18:00对松山湖站点启用无线节能优化"));
        assertEquals(2, acceptance.negotiationContext().round());

        FilledParamData terminalParameters = requester.validateAcceptPromptAndDataFilling(
                acceptance.promptText(), secondRound, parameterSchema(), TARGET_ACCEPT_URI);
        assertEquals(2, requesterLlm.callCount());
        assertEquals(SESSION_ID, terminalParameters.data().get("id"));
        assertEquals(2, terminalParameters.data().get("round"));
        assertEquals("08:00-18:00对松山湖站点启用无线节能优化", terminalParameters.data().get("confirmedIntent"));
        assertEquals(1, firstRound.round(), "the original context must stay unchanged until the end");
    }

    private static NegotiationGenerationOrchestrator orchestrator(ScriptedLlmClient llm) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();
    }

    private static Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of("confirmedIntent", Map.of("type", "string")));
    }

    private static Map<String, Object> nestedParameterSchema() {
        Map<String, Object> targetItem = Map.of(
                "type",
                "object",
                "properties",
                Map.of("time", Map.of("type", "string"), "rate", Map.of("type", "string")));
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of("timeRateTargets", Map.of("type", "array", "items", targetItem)));
    }
}
