package net.openan.a2at.sdk.negotiation.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import org.junit.jupiter.api.Test;

/**
 * Walks the bilateral interop loop between two independently assembled negotiation pipelines: one side generates from
 * data and from text (the text variant must render exactly the data variant of the same extracted content), the other
 * side validates the received message, and the roles are then reversed for the terminal answer. Both sides answer with
 * equal metadata content and equal filled parameters for the same inputs.
 */
class BilateralInteropScenarioTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final TemplateUri INFORMATION_ACCEPT_URI = StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;

    private static final String EXTRACTION_RESPONSE =
            "{\"items\":[{\"name\":\"节能区域信息\",\"value\":\"松山湖\"}],\"relationship\":null}";

    @Test
    void bothSidesGenerateAndValidateEachOthersMessages() {
        ScriptedLlmClient initiatingSideLlm = new ScriptedLlmClient(
                EXTRACTION_RESPONSE, SemanticResponses.acceptance("information", "{\"region\":\"松山湖\"}"));
        ScriptedLlmClient respondingSideLlm =
                new ScriptedLlmClient(SemanticResponses.acceptance("information", "{\"region\":\"松山湖\"}"));
        NegotiationGenerationOrchestrator initiatingSide = orchestrator(initiatingSideLlm);
        NegotiationGenerationOrchestrator respondingSide = orchestrator(respondingSideLlm);
        NegotiationContext round = new NegotiationContext(SESSION_ID, 1, 5);

        MetadataContent fromData = initiatingSide.generateProposeFromData(
                new NegotiationProposeData(
                        round, new InformationProposeContent(List.of(new NegotiationItem("节能区域信息", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);
        MetadataContent fromText =
                initiatingSide.generateProposeFromText("请提供节能区域信息：松山湖。", round, INFORMATION_PROPOSE_URI);
        assertEquals(
                fromData.promptText(),
                fromText.promptText(),
                "the from-text variant must render exactly the from-data variant of the same extracted content");
        assertEquals(fromData.templateUri(), fromText.templateUri());
        assertEquals(1, initiatingSideLlm.callCount(), "the from-text generation performs exactly one extraction call");

        FilledParamData respondingSideParameters = respondingSide.validateProposePromptAndDataFilling(
                fromData.promptText(), round, parameterSchema(), INFORMATION_PROPOSE_URI);
        FilledParamData initiatingSideParameters = initiatingSide.validateProposePromptAndDataFilling(
                fromData.promptText(), round, parameterSchema(), INFORMATION_PROPOSE_URI);
        assertEquals(
                respondingSideParameters,
                initiatingSideParameters,
                "both sides must extract the same parameters from the same message");
        assertEquals(SESSION_ID, respondingSideParameters.data().get("id"));
        assertEquals("松山湖", respondingSideParameters.data().get("region"));

        NegotiationEndingData answer = new NegotiationEndingData(
                round,
                new InformationEndingContent(NegotiationConclusion.ACCEPT, List.of(new NegotiationItem("节能区域信息", "松山湖"))));
        MetadataContent respondingSideAnswer = respondingSide.generateAcceptFromData(answer, INFORMATION_ACCEPT_URI);
        MetadataContent initiatingSideAnswer = initiatingSide.generateAcceptFromData(answer, INFORMATION_ACCEPT_URI);
        assertEquals(
                respondingSideAnswer,
                initiatingSideAnswer,
                "both sides must generate the identical message for the same typed input");
        assertEquals(respondingSideAnswer.buildMetadataContent(), initiatingSideAnswer.buildMetadataContent());
        assertTrue(respondingSideAnswer.promptText().contains("## 信息协商结果\nAccept"));

        FilledParamData validatedAnswer = initiatingSide.validateAcceptPromptAndDataFilling(
                respondingSideAnswer.promptText(), round, parameterSchema(), INFORMATION_ACCEPT_URI);
        assertEquals(SESSION_ID, validatedAnswer.data().get("id"));
        assertEquals(1, validatedAnswer.data().get("round"));
        assertEquals("松山湖", validatedAnswer.data().get("region"));
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
