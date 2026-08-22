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

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final TemplateUri INFORMATION_ACCEPT_REJECT_URI =
            StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;

    private static final TemplateUri TARGET_PROPOSE_URI = StandardTemplates.TARGET_NEGOTIATION_PROPOSE;

    private static final Map<String, Object> REGION_SCHEMA = Map.of(
            "type", "object", "properties", Map.of("region", Map.of("type", "string")), "required", List.of("region"));

    @Test
    void legalProposeMessageRunsTheFullChainWithASingleLlmCall() {
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"region\":\"松山湖\",\"confirmed_rate_mbps\":2}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationProposeMessage(orchestrator);

        FilledParamData filled =
                orchestrator.validateProposePromptAndDataFilling(message, REGION_SCHEMA, INFORMATION_PROPOSE_URI);

        assertEquals(1, llm.calls);
        assertEquals(SESSION_ID, filled.data().get("id"));
        assertEquals(2, filled.data().get("round"));
        assertEquals(5, filled.data().get("maxRounds"));
        assertEquals("松山湖", filled.data().get("region"));
        assertEquals(2, filled.data().get("confirmed_rate_mbps"));
        assertEquals(5, filled.data().size());
        assertInstanceOf(String.class, filled.data().get("id"), "context id must be a string");
        assertInstanceOf(Integer.class, filled.data().get("round"), "context round must be a number");
        assertInstanceOf(Integer.class, filled.data().get("maxRounds"), "context maxRounds must be a number");
    }

    @Test
    void legalAcceptMessageRunsTheFullChainWithASingleLlmCall() {
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"energy_saving_area\":\"松山湖\"}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationEndingMessage(orchestrator, NegotiationConclusion.ACCEPT);

        FilledParamData filled =
                orchestrator.validateAcceptPromptAndDataFilling(message, REGION_SCHEMA, INFORMATION_ACCEPT_REJECT_URI);

        assertEquals(1, llm.calls);
        assertEquals(SESSION_ID, filled.data().get("id"));
        assertEquals(2, filled.data().get("round"));
        assertEquals(5, filled.data().get("maxRounds"));
        assertEquals("松山湖", filled.data().get("energy_saving_area"));
        assertEquals(4, filled.data().size());
    }

    @Test
    void legalRejectMessageRunsTheFullChainWithASingleLlmCall() {
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"unavailable_item\":\"节能区域信息\"}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationEndingMessage(orchestrator, NegotiationConclusion.REJECT);

        FilledParamData filled =
                orchestrator.validateRejectPromptAndDataFilling(message, REGION_SCHEMA, INFORMATION_ACCEPT_REJECT_URI);

        assertEquals(1, llm.calls);
        assertEquals(SESSION_ID, filled.data().get("id"));
        assertEquals("节能区域信息", filled.data().get("unavailable_item"));
        assertEquals(4, filled.data().size());
    }

    @ParameterizedTest
    @MethodSource("ruleViolationCases")
    void ruleViolationsFailBeforeAnyLlmCall(
            String caseName, String idValue, String roundValue, String maxRoundsValue, List<String> expectedSlots) {
        ScriptedLlmClient llm = new ScriptedLlmClient(
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String violatingMessage = zhContextMessage(idValue, roundValue, maxRoundsValue);

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(
                        violatingMessage, REGION_SCHEMA, INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.NEGOTIATION_RULE_VIOLATION, exception.getCode(), caseName);
        assertEquals(expectedSlots.size(), exception.getErrors().size(), caseName);
        for (int index = 0; index < expectedSlots.size(); index++) {
            String slotName = exception.getErrors().get(index).slotName();
            assertEquals(expectedSlots.get(index), slotName, caseName);
            assertTrue(
                    List.of("id", "round", "maxRounds").contains(slotName),
                    caseName + ": rule errors must only address context slots but was " + slotName);
        }
        assertEquals(0, llm.calls, caseName + ": the rule gate must run before any LLM call");
    }

    static List<Object[]> ruleViolationCases() {
        return List.of(
                new Object[] {"non-uuid id", "not-a-uuid", null, null, List.of("id")},
                new Object[] {"round zero", null, "0", null, List.of("round")},
                new Object[] {"negative round", null, "-1", null, List.of("round")},
                new Object[] {"zero maxRounds", null, null, "0", List.of("maxRounds")},
                new Object[] {"round above maxRounds", null, "3", "2", List.of("round")},
                new Object[] {"non-integer round", null, "two", null, List.of("round")},
                new Object[] {"non-integer maxRounds", null, null, "many", List.of("maxRounds")});
    }

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
                () -> orchestrator.validateProposePromptAndDataFilling(message, REGION_SCHEMA, INFORMATION_PROPOSE_URI));

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
                () -> orchestrator.validateProposePromptAndDataFilling(taskTMessage, REGION_SCHEMA, INFORMATION_PROPOSE_URI));

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
                    "## 任务类型(Task Type)\n无线能效优化\n\n## 任务对象(Task Object)\n松山湖管委会\n\n## 任务目标(Task"
                            + " Target)\n总功耗降低30%\n"
                },
                new Object[] {
                    "english task prompt",
                    "## Task Type\nWireless energy saving\n\n## Task Object\nSongshan Lake\n\n## Task"
                            + " Target\nReduce total power consumption by 30 percent\n"
                });
    }

    @Test
    void nestedArrayParamsAreExtractedThroughTheMergedSchema() {
        Map<String, Object> timeRateItemSchema = Map.of(
                "type",
                "object",
                "properties",
                Map.of("time", Map.of("type", "string"), "rate", Map.of("type", "string")));
        Map<String, Object> callerSchema = Map.of(
                "type",
                "object",
                "properties",
                Map.of("timeRateTargets", Map.of("type", "array", "items", timeRateItemSchema)),
                "required",
                List.of("timeRateTargets"),
                "additionalProperties",
                false);
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":true,\"negotiation_type\":\"target\",\"errors\":[],"
                        + "\"params\":{\"timeRateTargets\":[{\"time\":\"08:00-18:00\",\"rate\":\"2Mbps\"}]}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = targetProposeMessage(orchestrator);

        FilledParamData filled = orchestrator.validateProposePromptAndDataFilling(message, callerSchema, TARGET_PROPOSE_URI);

        assertEquals(1, llm.calls);
        assertEquals(SESSION_ID, filled.data().get("id"));
        assertEquals(
                List.of(Map.of("time", "08:00-18:00", "rate", "2Mbps")),
                filled.data().get("timeRateTargets"));

        Map<String, Object> mergedSchema = llm.lastSchema;
        assertEquals(List.of("semantic_verdict", "negotiation_type", "errors", "params"), mergedSchema.get("required"));
        assertEquals(false, mergedSchema.get("additionalProperties"));
        Map<?, ?> properties = (Map<?, ?>) mergedSchema.get("properties");
        Map<?, ?> paramsSchema = (Map<?, ?>) properties.get("params");
        assertEquals("object", paramsSchema.get("type"));
        Map<?, ?> timeRateTargets = (Map<?, ?>) ((Map<?, ?>) paramsSchema.get("properties")).get("timeRateTargets");
        assertEquals("array", timeRateTargets.get("type"));
    }

    @Test
    void callerSchemaWithoutATypeKeywordIsWrappedAndTheChainSucceeds() {
        Map<String, Object> schemaWithoutType =
                Map.of("properties", Map.of("region", Map.of("type", "string")), "required", List.of("region"));
        ScriptedLlmClient llm =
                new ScriptedLlmClient("{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"region\":\"松山湖\"}}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        String message = informationProposeMessage(orchestrator);

        FilledParamData filled =
                orchestrator.validateProposePromptAndDataFilling(message, schemaWithoutType, INFORMATION_PROPOSE_URI);

        assertEquals(1, llm.calls);
        assertEquals("松山湖", filled.data().get("region"));
        Map<?, ?> paramsSchema = (Map<?, ?>) ((Map<?, ?>) llm.lastSchema.get("properties")).get("params");
        assertEquals("object", paramsSchema.get("type"));
        assertEquals(Map.of("region", Map.of("type", "string")), paramsSchema.get("properties"));
        assertEquals(List.of("region"), paramsSchema.get("required"));
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
                () -> orchestrator.validateProposePromptAndDataFilling(message, REGION_SCHEMA, INFORMATION_PROPOSE_URI));

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
                () -> orchestrator.validateProposePromptAndDataFilling(message, REGION_SCHEMA, INFORMATION_PROPOSE_URI));

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
                () -> orchestrator.validateProposePromptAndDataFilling(message, REGION_SCHEMA, INFORMATION_PROPOSE_URI));

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
                () -> orchestrator.validateProposePromptAndDataFilling(message, REGION_SCHEMA, INFORMATION_PROPOSE_URI));

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
                () -> orchestrator.validateProposePromptAndDataFilling(message, REGION_SCHEMA, INFORMATION_PROPOSE_URI));

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
                () -> orchestrator.validateProposePromptAndDataFilling(message, REGION_SCHEMA, INFORMATION_PROPOSE_URI));

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
                .semanticValidator((prompt, callerSchema, reference) -> {
                    throw new ResourceNotFoundException(
                            "Negotiation semantic validation prompt resource does not exist.",
                            "prompt_resources/prompts/negotiation_semantic_validation/zh-CN/system.md");
                })
                .build();

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(
                        "## 协商上下文\n- id: " + SESSION_ID + "\n- round: 1\n- maxRounds: 5\n\n## 所需信息项\n1. 区域\n",
                        REGION_SCHEMA,
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
                        rejectMessage, REGION_SCHEMA, INFORMATION_PROPOSE_URI));

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
                        new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);
        return content.promptText();
    }

    private static String informationEndingMessage(
            NegotiationGenerationOrchestrator orchestrator, NegotiationConclusion conclusion) {
        NegotiationEndingData data = new NegotiationEndingData(
                new NegotiationContext(SESSION_ID, 2, 5),
                new InformationEndingContent(conclusion, List.of(new NegotiationItem("节能区域", "松山湖"))));
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
                                "确认站点级节能任务的速率保障目标调整方案",
                                List.of(new NegotiationItem("任务意图", "在08:00-18:00对目标站点实施节能")),
                                null,
                                null)),
                TARGET_PROPOSE_URI);
        return content.promptText();
    }

    private static String zhContextMessage(String idValue, String roundValue, String maxRoundsValue) {
        StringBuilder prompt = new StringBuilder("## 协商上下文\n");
        prompt.append("- id: ").append(idValue == null ? SESSION_ID : idValue).append('\n');
        prompt.append("- round: ").append(roundValue == null ? "1" : roundValue).append('\n');
        prompt.append("- maxRounds: ")
                .append(maxRoundsValue == null ? "5" : maxRoundsValue)
                .append('\n');
        prompt.append("\n## 所需信息项\n1. 节能区域\n");
        return prompt.toString();
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
