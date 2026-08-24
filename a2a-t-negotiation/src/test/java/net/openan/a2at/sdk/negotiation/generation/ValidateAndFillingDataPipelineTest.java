package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Integration tests of the three validate-and-filling pipelines of the negotiation content layer.
 *
 * <p>Every test drives the real orchestrator with the built-in zh-CN templates and prompt resources; only the LLM
 * boundary is scripted, so each test exercises the full chain from the rendered message through the rule gate, the
 * merged semantic validation schema and the deterministic parameter merge.
 */
class ValidateAndFillingDataPipelineTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final NegotiationContext CONTEXT = new NegotiationContext(SESSION_ID, 2, 5);

    private static final NegotiationContext TARGET_CONTEXT = new NegotiationContext(SESSION_ID, 1, 5);

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final TemplateUri INFORMATION_ACCEPT_REJECT_URI =
            StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;

    private static final TemplateUri TARGET_PROPOSE_URI = StandardTemplates.TARGET_NEGOTIATION_PROPOSE;

    private static final Map<String, Object> ACCESS_PORT_SCHEMA = Map.of(
            "type", "object",
            "properties",
            Map.of(
                    "accessPort", Map.of("type", "string"),
                    "bizScenario", Map.of("type", "string")),
            "required",
            List.of("accessPort", "bizScenario"));

    @Test
    void legalProposeMessageRunsTheFullChainWithASingleLlmCall() {
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"accessPort\":\"P533-珠江旧城-PTN3900-23-TPA1EG24-1\","
                        + "\"bizScenario\":\"专线质差\"}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationProposeMessage(orchestrator);

        FilledParamData filled =
                orchestrator.validateProposePromptAndDataFilling(message, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_PROPOSE_URI);

        assertEquals(1, llm.calls);
        assertEquals(SESSION_ID, filled.data().get("id"));
        assertEquals(2, filled.data().get("round"));
        assertEquals(5, filled.data().get("maxRounds"));
        assertEquals("P533-珠江旧城-PTN3900-23-TPA1EG24-1", filled.data().get("accessPort"));
        assertEquals("专线质差", filled.data().get("bizScenario"));
        assertEquals(5, filled.data().size());
        assertInstanceOf(String.class, filled.data().get("id"), "context id must be a string");
        assertInstanceOf(Integer.class, filled.data().get("round"), "context round must be a number");
        assertInstanceOf(Integer.class, filled.data().get("maxRounds"), "context maxRounds must be a number");
    }

    @Test
    void legalAcceptMessageRunsTheFullChainWithASingleLlmCall() {
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"accessPort\":\"P533-珠江旧城-PTN3900-23-TPA1EG24-1\"}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationEndingMessage(orchestrator, NegotiationConclusion.ACCEPT);

        FilledParamData filled =
                orchestrator.validateAcceptPromptAndDataFilling(message, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_ACCEPT_REJECT_URI);

        assertEquals(1, llm.calls);
        assertEquals(SESSION_ID, filled.data().get("id"));
        assertEquals(2, filled.data().get("round"));
        assertEquals(5, filled.data().get("maxRounds"));
        assertEquals("P533-珠江旧城-PTN3900-23-TPA1EG24-1", filled.data().get("accessPort"));
        assertEquals(4, filled.data().size());
    }

    @Test
    void legalRejectMessageRunsTheFullChainWithASingleLlmCall() {
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"unavailable_item\":\"接入端口名称\"}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationEndingMessage(orchestrator, NegotiationConclusion.REJECT);

        FilledParamData filled =
                orchestrator.validateRejectPromptAndDataFilling(message, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_ACCEPT_REJECT_URI);

        assertEquals(1, llm.calls);
        assertEquals(SESSION_ID, filled.data().get("id"));
        assertEquals("接入端口名称", filled.data().get("unavailable_item"));
        assertEquals(4, filled.data().size());
    }

    // The rule-violation table of this suite (non-uuid id, round above maxRounds, 0 LLM calls) is absorbed by the
    // VAL-RULE batch of the test corpus (negotiation-cases/validate/rule-gate.json): every row there asserts the same
    // negotiation_rule_violation code, the exact slot error pairs and the zero-LLM guarantee, plus the boundary and
    // combined-error rows the hand-written table never carried (design §6 Q8).

    @Test
    void semanticRejectionIsNotRetriedAndPassesTheErrorsThrough() {
        List<SlotValidationError> semanticErrors = List.of(
                new SlotValidationError(
                        "section.info_conclusion",
                        "invalid_conclusion",
                        "Conclusion Abort is not one of Accept or Reject."),
                new SlotValidationError(
                        "section.info_items", "missing_section", "Required information items are missing."));
        ScriptedLlmClient llm = new ScriptedLlmClient("{\"semantic_verdict\":false,\"negotiation_type\":null,"
                + "\"errors\":[{\"slot_name\":\"section.info_conclusion\",\"code\":\"invalid_conclusion\","
                + "\"message\":\"Conclusion Abort is not one of Accept or Reject.\"},"
                + "{\"slot_name\":\"section.info_items\",\"code\":\"missing_section\","
                + "\"message\":\"Required information items are missing.\"}],\"params\":{}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationProposeMessage(orchestrator);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(message, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED, exception.getCode());
        assertEquals(semanticErrors, exception.getErrors());
        assertEquals(1, llm.calls, "a negative verdict is a decision, not a failure, and must not be retried");
    }

    @ParameterizedTest
    @MethodSource("taskTMessages")
    void taskTMessagesAreRejectedAsNonNegotiationInputWithALanguageNeutralMessage(
            String caseName, String taskTMessage) {
        ScriptedLlmClient llm = new ScriptedLlmClient(
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(taskTMessage, null, ACCESS_PORT_SCHEMA, INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.NEGOTIATION_INVALID_INPUT, exception.getCode(), caseName);
        assertTrue(exception.getMessage().contains("checkTaskPrompt"), caseName);
        assertFalse(exception.getMessage().contains("协商上下文"), caseName);
        assertFalse(exception.getMessage().contains("Negotiation Context"), caseName);
        assertEquals(List.of(), exception.getErrors(), caseName);
        assertEquals(0, llm.calls, caseName);
    }

    static List<Object[]> taskTMessages() {
        return List.of(
                new Object[] {
                    "chinese task prompt",
                    "## 任务类型(Task Type)\n传输专线业务投诉诊断\n\n## 任务对象(Task Object)\n接入端口名称：P533-珠江旧城"
                            + "-PTN3900-23-TPA1EG24-1\n\n## 任务目标(Task Target)\n对网络侧故障进行诊断，返回故障根因和"
                            + "修复建议等诊断结果信息。\n"
                },
                new Object[] {
                    "english task prompt",
                    "## Task Type\nTransport private line service complaint diagnosis\n\n## Task Object\nAccess"
                            + " Port Name: P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1\n\n## Task Target\nDiagnose"
                            + " network-side faults and return diagnostic result information.\n"
                });
    }

    @Test
    void nestedArrayParamsAreExtractedThroughTheMergedSchema() {
        Map<String, Object> repairTargetItemSchema = Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "latencyTarget", Map.of("type", "string"),
                        "completionDeadline", Map.of("type", "string")));
        Map<String, Object> callerSchema = Map.of(
                "type",
                "object",
                "properties",
                Map.of("repairTargets", Map.of("type", "array", "items", repairTargetItemSchema)),
                "required",
                List.of("repairTargets"),
                "additionalProperties",
                false);
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":true,\"negotiation_type\":\"target\",\"errors\":[],"
                        + "\"params\":{\"repairTargets\":[{\"latencyTarget\":\"within 20ms\","
                        + "\"completionDeadline\":\"48 hours\"}]}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = targetProposeMessage(orchestrator);

        FilledParamData filled = orchestrator.validateProposePromptAndDataFilling(message, TARGET_CONTEXT, callerSchema, TARGET_PROPOSE_URI);

        assertEquals(1, llm.calls);
        assertEquals(SESSION_ID, filled.data().get("id"));
        assertEquals(
                List.of(Map.of("latencyTarget", "within 20ms", "completionDeadline", "48 hours")),
                filled.data().get("repairTargets"));

        Map<String, Object> mergedSchema = llm.lastSchema;
        assertEquals(List.of("semantic_verdict", "negotiation_type", "errors", "params"), mergedSchema.get("required"));
        assertEquals(false, mergedSchema.get("additionalProperties"));
        Map<?, ?> properties = (Map<?, ?>) mergedSchema.get("properties");
        Map<?, ?> paramsSchema = (Map<?, ?>) properties.get("params");
        assertEquals("object", paramsSchema.get("type"));
        Map<?, ?> repairTargets = (Map<?, ?>) ((Map<?, ?>) paramsSchema.get("properties")).get("repairTargets");
        assertEquals("array", repairTargets.get("type"));
    }

    @Test
    void callerSchemaWithoutATypeKeywordIsWrappedAndTheChainSucceeds() {
        Map<String, Object> schemaWithoutType = Map.of(
                "properties",
                Map.of("accessPort", Map.of("type", "string")),
                "required",
                List.of("accessPort"));
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"accessPort\":\"P533-珠江旧城-PTN3900-23-TPA1EG24-1\"}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationProposeMessage(orchestrator);

        FilledParamData filled =
                orchestrator.validateProposePromptAndDataFilling(message, CONTEXT, schemaWithoutType, INFORMATION_PROPOSE_URI);

        assertEquals(1, llm.calls);
        assertEquals("P533-珠江旧城-PTN3900-23-TPA1EG24-1", filled.data().get("accessPort"));
        Map<?, ?> paramsSchema = (Map<?, ?>) ((Map<?, ?>) llm.lastSchema.get("properties")).get("params");
        assertEquals("object", paramsSchema.get("type"));
        assertEquals(Map.of("accessPort", Map.of("type", "string")), paramsSchema.get("properties"));
        assertEquals(List.of("accessPort"), paramsSchema.get("required"));
    }

    @Test
    void missingNegotiationTypeKeyIsRetriedThenFailsAsAnInfrastructureError() {
        ScriptedLlmClient llm = new ScriptedLlmClient("{\"semantic_verdict\":true,\"errors\":[],\"params\":{}}");
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .maxAttempts(3)
                .build();
        String message = informationProposeMessage(orchestrator);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(message, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR, exception.getCode());
        assertEquals(3, llm.calls, "a shape-invalid response is a retryable infrastructure failure");
        assertEquals(1, exception.getErrors().size());
        assertEquals("_llm", exception.getErrors().get(0).slotName());
        assertTrue(exception.getMessage().contains("negotiation_type"));
    }

    @Test
    void declaredTypeMismatchIsASemanticRejection() {
        ScriptedLlmClient llm = new ScriptedLlmClient(
                "{\"semantic_verdict\":true,\"negotiation_type\":\"target\",\"errors\":[],\"params\":{}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationProposeMessage(orchestrator);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(message, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED, exception.getCode());
        assertEquals(1, llm.calls, "a type mismatch is a semantic decision and must not be retried");
        assertEquals(1, exception.getErrors().size());
        assertEquals("section.target", exception.getErrors().get(0).slotName());
        assertEquals("template_type_mismatch", exception.getErrors().get(0).code());
    }

    @ParameterizedTest
    @MethodSource("structuralSemanticCases")
    void structuralSemanticChecksSurfaceThroughTheSemanticErrors(
            String caseName, String payload, SlotValidationError expectedError) {
        ScriptedLlmClient llm = new ScriptedLlmClient(payload);
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationProposeMessage(orchestrator);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(message, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED, exception.getCode(), caseName);
        assertEquals(List.of(expectedError), exception.getErrors(), caseName);
        assertEquals(1, llm.calls, caseName);
    }

    static List<Object[]> structuralSemanticCases() {
        return List.of(
                new Object[] {
                    "conclusion outside accept and reject",
                    "{\"semantic_verdict\":false,\"negotiation_type\":\"information\",\"errors\":["
                            + "{\"slot_name\":\"section.target_conclusion\",\"code\":\"invalid_conclusion\","
                            + "\"message\":\"Conclusion Abort is not one of Accept or Reject.\"}],\"params\":{}}",
                    new SlotValidationError(
                            "section.target_conclusion",
                            "invalid_conclusion",
                            "Conclusion Abort is not one of Accept or Reject.")
                },
                new Object[] {
                    "ending result content section missing",
                    "{\"semantic_verdict\":false,\"negotiation_type\":\"information\",\"errors\":["
                            + "{\"slot_name\":\"section.target_result_content\",\"code\":\"missing_section\","
                            + "\"message\":\"The ending result content section is missing.\"}],\"params\":{}}",
                    new SlotValidationError(
                            "section.target_result_content",
                            "missing_section",
                            "The ending result content section is missing.")
                },
                new Object[] {
                    "information propose carries both conditional sections",
                    "{\"semantic_verdict\":false,\"negotiation_type\":\"information\",\"errors\":["
                            + "{\"slot_name\":\"section.info_static\",\"code\":\"conflicting_sections\","
                            + "\"message\":\"The static section and the required information items coexist.\"}],"
                            + "\"params\":{}}",
                    new SlotValidationError(
                            "section.info_static",
                            "conflicting_sections",
                            "The static section and the required information items coexist.")
                });
    }

    @Test
    void falseVerdictWithNullTypeIsAShapeLegalOutcome() {
        ScriptedLlmClient llm = new ScriptedLlmClient("{\"semantic_verdict\":false,\"negotiation_type\":null,"
                + "\"errors\":[{\"slot_name\":\"section.context\",\"code\":\"inconsistent_context\","
                + "\"message\":\"Context contradicts the message body.\"}],\"params\":{}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationProposeMessage(orchestrator);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(message, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED, exception.getCode());
        assertEquals(1, exception.getErrors().size());
        assertEquals("section.context", exception.getErrors().get(0).slotName());
        assertEquals(1, llm.calls, "verdict false with a null type is shape-legal and must not be retried");
    }

    @Test
    void trueVerdictWithNullTypeIsASemanticRejection() {
        ScriptedLlmClient llm = new ScriptedLlmClient(
                "{\"semantic_verdict\":true,\"negotiation_type\":null,\"errors\":[],\"params\":{}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationProposeMessage(orchestrator);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(message, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED, exception.getCode());
        assertEquals(1, llm.calls, "a null type with a true verdict is a semantic rejection, not a retryable failure");
        assertEquals(1, exception.getErrors().size());
        assertEquals("section.info_static", exception.getErrors().get(0).slotName());
        assertEquals("template_type_mismatch", exception.getErrors().get(0).code());
    }

    @Test
    void semanticLlmFailureIsRetriedAndExhaustsWithTheLlmPseudoSlot() {
        ScriptedLlmClient llm = new ScriptedLlmClient("unused");
        llm.failure = new LLMRuntimeError("LLM endpoint unavailable");
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .maxAttempts(2)
                .build();
        String message = informationProposeMessage(orchestrator);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(message, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR, exception.getCode());
        assertEquals(2, llm.calls);
        assertEquals(1, exception.getErrors().size());
        assertEquals("_llm", exception.getErrors().get(0).slotName());
        assertTrue(exception.getMessage().contains("endpoint unavailable"));
    }

    @Test
    void missingPromptResourcesCloseAsTemplateNotFoundWithoutBubblingTheResourceException() {
        ScriptedLlmClient llm = new ScriptedLlmClient("unused");
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .semanticValidator((prompt, callerSchema, reference, templateContent) -> {
                    throw new ResourceNotFoundException(
                            "Negotiation semantic validation prompt resource does not exist.",
                            "prompt_resources/prompts/negotiation_semantic_validation/zh-CN/system.md");
                })
                .build();

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(
                        "## 所需信息项\n1. 接入端口名称\n",
                        new NegotiationContext(SESSION_ID, 1, 5),
                        ACCESS_PORT_SCHEMA,
                        INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.TEMPLATE_NOT_FOUND, exception.getCode());
        assertEquals(
                NegotiationParamExtractionException.class,
                exception.getClass(),
                "the raw resource exception must not bubble out of the pipeline");
        assertTrue(exception instanceof A2ATError, "the mapped failure stays catchable through the A2ATError root");
        assertEquals(0, llm.calls);
    }

    @Test
    void proposeUriValidatingAResultMessageIsASemanticRejection() {
        SlotValidationError phaseError = new SlotValidationError(
                "section.info_result_content",
                "phase_mismatch",
                "The message is a terminal accept-reject message but the template URI declares the propose phase.");
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":false,\"negotiation_type\":\"information\",\"errors\":["
                        + "{\"slot_name\":\"section.info_result_content\",\"code\":\"phase_mismatch\","
                        + "\"message\":\"The message is a terminal accept-reject message but the template URI"
                        + " declares the propose phase.\"}],\"params\":{}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String rejectMessage = informationEndingMessage(orchestrator, NegotiationConclusion.REJECT);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(
                        rejectMessage, CONTEXT, ACCESS_PORT_SCHEMA, INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.NEGOTIATION_SEMANTIC_REJECTED, exception.getCode());
        assertEquals(List.of(phaseError), exception.getErrors());
        assertEquals(1, llm.calls);
    }

    private static NegotiationGenerationOrchestrator orchestrator(ScriptedLlmClient llm) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();
    }

    private static String informationProposeMessage(NegotiationGenerationOrchestrator orchestrator) {
        MetadataContent content = orchestrator.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(SESSION_ID, 2, 5),
                        new InformationProposeContent(
                                List.of(new NegotiationItem("接入端口名称", "P533-珠江旧城-PTN3900-23-TPA1EG24-1")), null)),
                INFORMATION_PROPOSE_URI);
        return content.promptText();
    }

    private static String informationEndingMessage(
            NegotiationGenerationOrchestrator orchestrator, NegotiationConclusion conclusion) {
        NegotiationEndingData data = new NegotiationEndingData(
                new NegotiationContext(SESSION_ID, 2, 5),
                new InformationEndingContent(
                        conclusion, List.of(new NegotiationItem("接入端口名称", "P533-珠江旧城-PTN3900-23-TPA1EG24-1"))));
        MetadataContent content = conclusion == NegotiationConclusion.ACCEPT
                ? orchestrator.generateAcceptFromData(data, INFORMATION_ACCEPT_REJECT_URI)
                : orchestrator.generateRejectFromData(data, INFORMATION_ACCEPT_REJECT_URI);
        return content.promptText();
    }

    private static String targetProposeMessage(NegotiationGenerationOrchestrator orchestrator) {
        MetadataContent content = orchestrator.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(SESSION_ID, 1, 5),
                        new TargetProposeContent(
                                "确认专线质差投诉的时延修复目标调整方案",
                                List.of(new NegotiationItem("修复意图", "在2026年5月15日前将深圳至广州专线的平均时延恢复至20ms以内")),
                                null,
                                null)),
                TARGET_PROPOSE_URI);
        return content.promptText();
    }

    /** LLM boundary fake replaying scripted payloads and recording every structured call. */
    private static final class ScriptedLlmClient implements LLMClient {

        private final List<String> payloads;

        private int calls;

        private RuntimeException failure;

        private List<Map<String, String>> lastMessages;

        private Map<String, Object> lastSchema;

        private ScriptedLlmClient(String... payloads) {
            this.payloads = List.of(payloads);
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls++;
            lastMessages = messages;
            lastSchema = jsonSchema;
            if (failure != null) {
                throw failure;
            }
            String payload = payloads.get(Math.min(calls - 1, payloads.size() - 1));
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }
}
