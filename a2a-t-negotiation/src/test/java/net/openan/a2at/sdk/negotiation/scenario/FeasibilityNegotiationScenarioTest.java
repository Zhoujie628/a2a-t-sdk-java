package net.openan.a2at.sdk.negotiation.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs.GoldenCase;
import org.junit.jupiter.api.Test;

/**
 * Walks the feasibility negotiation scenario: the responder's semantic validation rejects an evaluation request that
 * conflicts with an existing constraint (the trigger of the feasibility negotiation), the requester proposes an
 * alternative for the infeasible case, and the responder accepts with a summary that fills the exception slot of the
 * feasibility result confirmation section. The evaluation request and the terminal answer are locked against the golden
 * fixtures.
 */
class FeasibilityNegotiationScenarioTest {

    private static final String FEASIBILITY_PROPOSE_URI = "Negotiation-T/v1/feasibility-negotiation/propose";

    private static final String FEASIBILITY_ACCEPT_URI = "Negotiation-T/v1/feasibility-negotiation/accept-reject";

    @Test
    void feasibilityNegotiationRunsFromSemanticRejectionToATerminalAcceptance() throws IOException {
        String conflictErrors = "[{\"slot_name\":\"section.feasibility_evaluate\",\"code\":\"semantic_inconsistency\","
                + "\"message\":\"The adjusted rate target conflicts with the power supply duration constraint.\"},"
                + "{\"slot_name\":\"section.feasibility\",\"code\":\"semantic_unclear\","
                + "\"message\":\"The request does not state the constraint context of the evaluation.\"}]";
        ScriptedLlmClient requesterLlm =
                new ScriptedLlmClient(SemanticResponses.acceptance("feasibility", "{\"feasibilitySummary\":\"可行\"}"));
        ScriptedLlmClient responderLlm = new ScriptedLlmClient(SemanticResponses.rejection(conflictErrors));
        NegotiationGenerationOrchestrator requester = orchestrator(requesterLlm);
        NegotiationGenerationOrchestrator responder = orchestrator(responderLlm);

        MetadataContent evaluationRequest = GoldenCase.FEASIBILITY_PROPOSE.generate(requester);
        assertEquals(
                readGoldenFixture(GoldenCase.FEASIBILITY_PROPOSE, GoldenInputs.ZH_CN),
                evaluationRequest.promptText(),
                "the evaluation request must match the golden fixture byte for byte");

        NegotiationParamExtractionException rejection = assertThrows(
                NegotiationParamExtractionException.class,
                () -> responder.validateAndFillingProposeData(
                        evaluationRequest.promptText(), parameterSchema(), FEASIBILITY_PROPOSE_URI));
        assertEquals(A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED, rejection.getCode());
        assertEquals(1, responderLlm.callCount(), "a semantic rejection must not be retried");
        List<SlotValidationError> errors = rejection.getErrors();
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(error -> "section.feasibility_evaluate".equals(error.slotName())));
        assertTrue(errors.stream().anyMatch(error -> "section.feasibility".equals(error.slotName())));
        assertTrue(
                errors.stream().allMatch(error -> error.code() != null && error.message() != null),
                "every error detail must carry a code and a message so the caller can react per field");

        MetadataContent alternativeProposal = requester.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(GoldenInputs.SESSION_ID, 2, 5),
                        new FeasibilityProposeContent(
                                "目标与停电约束冲突，替代方案详见<评估不可行时的详情和提案>，请评估",
                                NegotiationAction.PROPOSE_ALTERNATIVE_ON_FAILURE,
                                null,
                                List.of(new NegotiationItem("替代方案", "速率保障目标下调至2Mbps，节能时段缩短为4小时")))),
                FEASIBILITY_PROPOSE_URI);
        assertTrue(
                alternativeProposal.promptText().contains("## 评估不可行时的详情和提案"),
                "the alternative action must render the infeasibility section");
        assertTrue(
                !alternativeProposal.promptText().contains("## 待评估内容说明"),
                "the alternative action must not render the evaluation section");

        MetadataContent acceptance = responder.generateAcceptFromData(
                new NegotiationEndingData(
                        GoldenInputs.defaultContext(),
                        new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, "同意将速率保障目标由5Mbps下调至2Mbps，本次协商确认结束")),
                FEASIBILITY_ACCEPT_URI);
        assertTrue(acceptance.promptText().contains("## 可行性协商结果\nAccept"));
        assertTrue(
                acceptance.promptText().contains("## 可行性评估结果确认\n同意将速率保障目标由5Mbps下调至2Mbps，本次协商确认结束"),
                "the summary must fill the exception slot of the feasibility result confirmation section");

        FilledParamData terminalParameters = requester.validateAndFillingAcceptData(
                acceptance.promptText(), parameterSchema(), FEASIBILITY_ACCEPT_URI);
        assertEquals(1, requesterLlm.callCount());
        assertEquals(GoldenInputs.SESSION_ID, terminalParameters.data().get("id"));
        assertEquals(2, terminalParameters.data().get("round"));
    }

    private static NegotiationGenerationOrchestrator orchestrator(ScriptedLlmClient llm) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();
    }

    private static Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of("feasibilitySummary", Map.of("type", "string")));
    }

    private static String readGoldenFixture(GoldenCase goldenCase, String language) {
        String resourcePath = goldenCase.goldenResourcePath(language);
        InputStream stream = FeasibilityNegotiationScenarioTest.class.getResourceAsStream(resourcePath);
        assertTrue(stream != null, "Golden fixture must exist on the test classpath: " + resourcePath);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new AssertionError("Failed to read golden fixture " + resourcePath, exception);
        }
    }
}
