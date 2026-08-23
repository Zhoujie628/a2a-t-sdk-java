package net.openan.a2at.sdk.negotiation.testdata;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import net.openan.a2at.sdk.negotiation.testdata.ScenarioCase.ExpectFlow;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

/**
 * Direct unit tests of the {@link ScenarioEngine} against inline scenario objects: the {@code prompt.fromStep}
 * resolution, the fail-fast step execution, and the three {@code expectFlow} fields (terminal condition, rounds used,
 * pairwise distinct messages).
 */
class ScenarioEngineTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String ZH_CN = "zh-CN";

    private static final String INFORMATION_PROPOSE_URI = "Negotiation-T/information-negotiation/propose/v1";

    private static final String INFORMATION_ACCEPT_REJECT_URI =
            "Negotiation-T/information-negotiation/accept-reject/v1";

    private static final String ACCEPT_PAYLOAD =
            "{\"conclusion\":\"Accept\",\"items\":[{\"name\":\"energy-saving area information\",\"value\":\"Songshan"
                    + " Lake\"},{\"name\":\"energy-saving rate guarantee target\",\"value\":\"20Mbps\"}]}";

    private static final String PROPOSE_PAYLOAD =
            "{\"items\":[{\"name\":\"energy-saving area information\",\"value\":\"e.g. Songshan Lake\"},{\"name\":"
                    + "\"energy-saving rate guarantee target\",\"value\":\"e.g. 20Mbps\"},{\"name\":\"VLANId\","
                    + "\"value\":null}],\"relationship\":\"OR\"}";

    private static final String SEMANTIC_ACCEPT_PAYLOAD =
            "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}";

    private final ScenarioEngine engine = new ScenarioEngine();

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode schema;

    @BeforeEach
    void readSchema() throws Exception {
        schema = mapper.readTree("{\"type\":\"object\",\"properties\":{\"region\":{\"type\":\"string\"}}}");
    }

    // ------------------------------------------------------------------ fromStep resolution

    @Test
    void runsATwoStepScenarioResolvingTheFromStepPrompt() {
        ScenarioCase scenario = scenario(
                "SC-INFO-01",
                List.of(
                        step(1, "A", acceptStep("SC-INFO-01", ok(1, "information_accept"))),
                        step(
                                2,
                                "B",
                                validateAcceptStep(
                                        "SC-INFO-01",
                                        1,
                                        ok(1, null)))),
                new ScenarioCase.ExpectFlow("accept", 2, null));

        engine.runScenario(scenario);
    }

    @Test
    void failsWhenAFromStepReferencesAnUnknownStep() {
        ScenarioCase scenario = scenario(
                "SC-INFO-02",
                List.of(step(1, "A", validateAcceptStep("SC-INFO-02", 3, ok(1, null)))),
                null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(scenario));

        assertTrue(
                failure.getMessage().contains("SC-INFO-02/zh-CN")
                        && failure.getMessage().contains("prompt.fromStep 3")
                        && failure.getMessage().contains("produced no prompt text"),
                "the unknown fromStep reference must fail with the scenario id and the step number but was: "
                        + failure.getMessage());
    }

    // ------------------------------------------------------------------ fail-fast

    @Test
    void failsFastWhenAStepFails() {
        NegotiationCase failingStep = stepCase(
                "SC-ERR-01",
                1,
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                new LlmScript(null, List.of(new LlmScriptStep.Fail(LlmFailMarker.NON_JSON))),
                null,
                null,
                failed("NegotiationGenerationException", "negotiation_invalid_input", null));
        ScenarioCase scenario = scenario(
                "SC-ERR-01",
                List.of(
                        step(1, "A", failingStep),
                        step(2, "B", validateAcceptStep("SC-ERR-01", 1, ok(1, null)))),
                null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(scenario));

        assertTrue(
                failure.getMessage().contains("#step-1") && failure.getMessage().contains("$.expect.code"),
                "the scenario must abort on the failing step 1 but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ expectFlow

    @Test
    void assertsDistinctMessagesAndRoundsUsedAcrossSteps() {
        ScenarioCase scenario = scenario(
                "SC-INFO-03",
                List.of(
                        step(1, "A", proposeStep("SC-INFO-03", 2, ok(1, "information_propose"))),
                        step(2, "B", acceptStep("SC-INFO-03", 3, ok(1, "information_accept")))),
                new ScenarioCase.ExpectFlow("accept", 3, Boolean.TRUE));

        engine.runScenario(scenario);
    }

    @Test
    void distinctMessagesFailsOnDuplicateMessages() {
        ScenarioCase scenario = scenario(
                "SC-INFO-04",
                List.of(
                        step(1, "A", acceptStep("SC-INFO-04", 2, ok(1, "information_accept"))),
                        step(2, "B", acceptStep("SC-INFO-04", 3, ok(1, "information_accept")))),
                new ScenarioCase.ExpectFlow("accept", 3, Boolean.TRUE));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(scenario));

        assertTrue(
                failure.getMessage().contains("$.expectFlow.distinctMessages"),
                "duplicate messages must fail the distinctMessages expectation but was: " + failure.getMessage());
    }

    @Test
    void exhaustedRequiresTheRoundLimitToBeReached() {
        ScenarioCase exhausted = scenario(
                "SC-EXH-01",
                List.of(step(1, "A", acceptStep("SC-EXH-01", 5, ok(1, "information_accept")))),
                new ScenarioCase.ExpectFlow("exhausted", 5, null));
        engine.runScenario(exhausted);

        ScenarioCase notExhausted = scenario(
                "SC-EXH-02",
                List.of(step(1, "A", acceptStep("SC-EXH-02", 2, ok(1, "information_accept")))),
                new ScenarioCase.ExpectFlow("exhausted", 2, null));
        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(notExhausted));

        assertTrue(
                failure.getMessage().contains("$.expectFlow.terminalCondition"),
                "a round below the limit must fail the exhausted expectation but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ builders

    private static ScenarioCase scenario(
            String id, List<ScenarioCase.ScenarioStep> steps, @Nullable ExpectFlow expectFlow) {
        return new ScenarioCase(
                id + "/zh-CN", id, "scenarios/information-flows.json", ZH_CN, null, List.of("A", "B"), steps,
                expectFlow);
    }

    private static ScenarioCase.ScenarioStep step(int number, String role, NegotiationCase caseData) {
        return new ScenarioCase.ScenarioStep(number, role, caseData);
    }

    private NegotiationCase acceptStep(String scenarioId, Expectation expect) {
        return stepCase(
                scenarioId,
                1,
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "我确认第一阶段的信息。",
                script(ACCEPT_PAYLOAD),
                null,
                null,
                expect);
    }

    private NegotiationCase acceptStep(String scenarioId, int round, Expectation expect) {
        return stepCase(
                scenarioId,
                1,
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                new ContextSpec(SESSION_ID, round, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "我确认第一阶段的信息。",
                script(ACCEPT_PAYLOAD),
                null,
                null,
                expect);
    }

    private NegotiationCase proposeStep(String scenarioId, int round, Expectation expect) {
        return stepCase(
                scenarioId,
                1,
                NegotiationApi.GENERATE_PROPOSE_FROM_TEXT,
                new ContextSpec(SESSION_ID, round, 5),
                INFORMATION_PROPOSE_URI,
                "请补充节能区域、节能速率保障目标与VLANId信息。",
                script(PROPOSE_PAYLOAD),
                null,
                null,
                expect);
    }

    private NegotiationCase validateAcceptStep(String scenarioId, int fromStep, Expectation expect) {
        return stepCase(
                scenarioId,
                2,
                NegotiationApi.VALIDATE_ACCEPT_PROMPT_AND_DATA_FILLING,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                null,
                script(SEMANTIC_ACCEPT_PAYLOAD),
                new PromptSource.FromStep(fromStep),
                schema,
                expect);
    }

    private NegotiationCase stepCase(
            String scenarioId,
            int stepNumber,
            NegotiationApi api,
            ContextSpec context,
            String templateUri,
            @Nullable String inputText,
            LlmScript llmScript,
            @Nullable PromptSource prompt,
            @Nullable JsonNode schema,
            Expectation expect) {
        return new NegotiationCase(
                scenarioId + "/zh-CN#step-" + stepNumber,
                scenarioId,
                "scenarios/information-flows.json",
                api,
                ZH_CN,
                null,
                List.of(),
                null,
                context,
                templateUri,
                inputText,
                null,
                llmScript,
                prompt,
                schema,
                null,
                expect);
    }

    private static LlmScript script(String payload) {
        return new LlmScript(null, List.of(new LlmScriptStep.Payload(payload)));
    }

    // ------------------------------------------------------------------ expectation helpers

    private static Expectation ok(@Nullable Integer llmCalls, @Nullable String promptTextEqualsGolden) {
        return new Expectation(
                true,
                null,
                null,
                List.of(),
                List.of(),
                llmCalls,
                promptTextEqualsGolden,
                null,
                java.util.Map.of(),
                List.of(),
                false);
    }

    private static Expectation failed(String exception, String code, @Nullable Integer llmCalls) {
        return new Expectation(
                false, exception, code, List.of(), List.of(), llmCalls, null, null, java.util.Map.of(), List.of(),
                false);
    }
}
