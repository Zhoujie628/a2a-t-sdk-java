package net.openan.a2at.sdk.negotiation.testdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import org.junit.jupiter.api.BeforeEach;
import net.openan.a2at.sdk.negotiation.testdata.Expectation.Metadata;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

/**
 * Direct unit tests of the {@link CaseEngine} against inline case objects: the four P0 contracts, the Q17 C+
 * differential double run, the inject hook, the fail-on-overconsumption calibration through {@code llmCalls}, and the
 * red paths proving the engine is not a rubber stamp (every flipped expectation fails with the case id and the JSON path
 * of the expectation).
 *
 * <p>The engine runs against the real production wiring (builders, classpath resources, renderers, vocabulary, rule gate,
 * semantic validator); the golden comparisons use the committed golden fixtures of the test resources.
 */
class CaseEngineTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String ZH_CN = "zh-CN";

    private static final String INFORMATION_PROPOSE_URI = "Negotiation-T/information-negotiation/propose/v1";

    private static final String INFORMATION_ACCEPT_REJECT_URI =
            "Negotiation-T/information-negotiation/accept-reject/v1";

    /** Extraction payload that maps back to the typed content of the information accept golden fixture. */
    private static final String ACCEPT_PAYLOAD =
            "{\"conclusion\":\"Accept\",\"items\":[{\"name\":\"energy-saving area information\",\"value\":\"Songshan"
                    + " Lake\"},{\"name\":\"energy-saving rate guarantee target\",\"value\":\"20Mbps\"}]}";

    /** Extraction payload that maps back to the typed content of the information propose golden fixture. */
    private static final String PROPOSE_PAYLOAD =
            "{\"items\":[{\"name\":\"energy-saving area information\",\"value\":\"e.g. Songshan Lake\"},{\"name\":"
                    + "\"energy-saving rate guarantee target\",\"value\":\"e.g. 20Mbps\"},{\"name\":\"VLANId\","
                    + "\"value\":null}],\"relationship\":\"OR\"}";

    /** The same content as typed data, disagreeing in one item value for the differential red path. */
    private static final String PROPOSE_DATA_DISAGREEING =
            "{\"items\":[{\"name\":\"energy-saving area information\",\"value\":\"Another Lake\"},{\"name\":"
                    + "\"energy-saving rate guarantee target\",\"value\":\"e.g. 20Mbps\"},{\"name\":\"VLANId\","
                    + "\"value\":null}],\"relationship\":\"OR\"}";

    private static final String SEMANTIC_ACCEPT_PAYLOAD_WITH_PARAMS =
            "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{\"region\":"
                    + "\"Songshan Lake\"}}";

    private static final String SEMANTIC_REJECT_PAYLOAD =
            "{\"semantic_verdict\":false,\"negotiation_type\":\"information\",\"errors\":[{\"slot_name\":"
                    + "\"region\",\"code\":\"missing\",\"message\":\"The region parameter is missing.\"}],"
                    + "\"params\":{}}";

    private final CaseEngine engine = new CaseEngine();

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode schema;

    @BeforeEach
    void readSchema() throws Exception {
        schema = mapper.readTree("{\"type\":\"object\",\"properties\":{\"region\":{\"type\":\"string\"}}}");
    }

    // ------------------------------------------------------------------ green paths

    @Test
    void runsASuccessfulFromTextCaseAgainstTheGoldenFixture() {
        NegotiationCase testCase = acceptFromTextCase(ok(1, "information_accept", metadata(), false), null);

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        MetadataContent message = outcome.message();
        assertNotNull(message);
        assertEquals(1, outcome.llmCalls());
        assertEquals(INFORMATION_ACCEPT_REJECT_URI, message.templateUri());
        assertEquals(new NegotiationContext(SESSION_ID, 2, 5), message.negotiationContext());
    }

    @Test
    void runsFromDataCasesDeterministicallyWithoutAnyLlmCall() throws Exception {
        NegotiationCase testCase = new NegotiationCase(
                "FD-HAPPY-01/zh-CN",
                "FD-HAPPY-01",
                "from-data/happy.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_DATA,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                null,
                mapper.readTree(ACCEPT_PAYLOAD),
                null,
                null,
                null,
                null,
                ok(0, "information_accept", metadata(), false));

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        assertNotNull(outcome.message());
        assertEquals(0, outcome.llmCalls(), "the from-data run must not call the LLM (assertion-only client)");
    }

    @Test
    void injectsTheFailingTemplateLoaderForTheTemplateNotFoundMatrix() {
        NegotiationCase testCase = new NegotiationCase(
                "FT-TPL-01/zh-CN",
                "FT-TPL-01",
                "from-text/template-resolution.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(ACCEPT_PAYLOAD))),
                null,
                null,
                "failingTemplateLoader",
                failed("NegotiationGenerationException", "template_not_found", 0, List.of(), List.of()));

        engine.run(testCase);
    }

    @Test
    void injectsTheFailingSemanticValidatorForTheValidateTemplateNotFoundMapping() {
        NegotiationCase testCase = new NegotiationCase(
                "VAL-MAP-05/zh-CN",
                "VAL-MAP-05",
                "validate/error-code-mapping.json",
                NegotiationApi.VALIDATE_PROPOSE_PROMPT_AND_DATA_FILLING,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_PROPOSE_URI,
                null,
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Fail(LlmFailMarker.ASSERTION))),
                new PromptSource.Golden("information_propose"),
                schema,
                "failingSemanticValidator",
                failed("NegotiationParamExtractionException", "template_not_found", 0, List.of(), List.of()));

        engine.run(testCase);
    }

    // ------------------------------------------------------------------ P0 contracts

    @Test
    void assertConclusionLiteralPresentContract() {
        NegotiationCase testCase =
                acceptFromTextCase(okContracts(1, "information_accept", List.of("conclusionLiteralPresent")), null);

        engine.run(testCase);
    }

    @Test
    void conclusionLiteralPresentContractRejectsNonTerminalApis() {
        NegotiationCase testCase =
                proposeFromTextCase(okContracts(1, "information_propose", List.of("conclusionLiteralPresent")), null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("conclusionLiteralPresent")
                        && failure.getMessage().contains("a terminal or abort generation API"),
                "the contract must reject a propose API but was: " + failure.getMessage());
    }

    @Test
    void assertContextKeysInMergedParamsContract() {
        NegotiationCase testCase = validateProposeCase(
                okParams(
                        1,
                        Map.of("id", SESSION_ID, "round", 2, "maxRounds", 5, "region", "Songshan Lake"),
                        List.of("contextKeysInMergedParams")),
                SEMANTIC_ACCEPT_PAYLOAD_WITH_PARAMS);

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        FilledParamData filled = outcome.filledParams();
        assertNotNull(filled);
        assertEquals("Songshan Lake", filled.data().get("region"));
    }

    @Test
    void assertNoLlmLeakInUserMessageContract() {
        NegotiationCase testCase = new NegotiationCase(
                "FT-RETRY-01/zh-CN",
                "FT-RETRY-01",
                "from-text/retry.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                null,
                new LlmScript(
                        null,
                        List.of(
                                new LlmScriptStep.Fail(LlmFailMarker.RUNTIME_EXCEPTION),
                                new LlmScriptStep.Fail(LlmFailMarker.RUNTIME_EXCEPTION),
                                new LlmScriptStep.Fail(LlmFailMarker.RUNTIME_EXCEPTION))),
                null,
                null,
                null,
                failed(
                        "NegotiationGenerationException",
                        "negotiation_llm_infrastructure_error",
                        3,
                        List.of(),
                        List.of("noLlmLeakInUserMessage")));

        engine.run(testCase);
    }

    @Test
    void assertMetadataTripleShapeContract() {
        NegotiationCase testCase =
                acceptFromTextCase(okContracts(1, "information_accept", List.of("metadataTripleShape")), null);

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        assertEquals(
                3,
                outcome.message().buildMetadataContent().size(),
                "the contract must have verified the triple metadata shape");
    }

    @Test
    void rejectsUnknownAndNotYetLitContractNames() {
        NegotiationCase unknown =
                acceptFromTextCase(okContracts(1, "information_accept", List.of("not-a-contract")), null);
        AssertionError unknownFailure = assertThrows(AssertionError.class, () -> engine.run(unknown));
        assertTrue(
                unknownFailure.getMessage().contains("a registered contract name"),
                "an unknown contract name must fail but was: " + unknownFailure.getMessage());

        NegotiationCase notYetLit =
                acceptFromTextCase(okContracts(1, "information_accept", List.of("noRenderSlotLeak")), null);
        AssertionError notYetLitFailure = assertThrows(AssertionError.class, () -> engine.run(notYetLit));
        assertTrue(
                notYetLitFailure.getMessage().contains("not yet lit"),
                "a registered P1 contract must fail as not yet lit but was: " + notYetLitFailure.getMessage());
    }

    // ------------------------------------------------------------------ differential (Q17 C+)

    @Test
    void runsTheDifferentialDoubleRun() throws Exception {
        NegotiationCase testCase = proposeFromTextCase(
                ok(1, "information_propose", new Expectation.Metadata(INFORMATION_PROPOSE_URI, Boolean.TRUE), true),
                mapper.readTree(PROPOSE_PAYLOAD));

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        assertNotNull(outcome.message());
        assertEquals(1, outcome.llmCalls(), "only the from-text leg may call the LLM");
    }

    @Test
    void differentialFailsWhenTheTypedDataDisagreesWithTheText() throws Exception {
        NegotiationCase testCase = proposeFromTextCase(
                ok(1, "information_propose", new Expectation.Metadata(INFORMATION_PROPOSE_URI, Boolean.TRUE), true),
                mapper.readTree(PROPOSE_DATA_DISAGREEING));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.differential")
                        && failure.getMessage().contains("fromText == fromData"),
                "the differential must compare both legs but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ validate expectations

    @Test
    void comparesSlotErrorsOrderInsensitively() {
        NegotiationCase testCase = validateProposeCase(
                failed(
                        "NegotiationParamExtractionException",
                        "negotiation_semantic_rejected",
                        1,
                        List.of(new Expectation.SlotError("region", "missing")),
                        List.of()),
                SEMANTIC_REJECT_PAYLOAD);

        engine.run(testCase);
    }

    @Test
    void slotErrorsFailOnAnExtraExpectedPair() {
        NegotiationCase testCase = validateProposeCase(
                failed(
                        "NegotiationParamExtractionException",
                        "negotiation_semantic_rejected",
                        1,
                        List.of(
                                new Expectation.SlotError("region", "missing"),
                                new Expectation.SlotError("round", "out_of_range")),
                        List.of()),
                SEMANTIC_REJECT_PAYLOAD);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.slotErrors"),
                "an extra expected slot error must fail but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ red paths (not a rubber stamp)

    @Test
    void failsWithCaseIdAndJsonPathWhenTheExpectedCodeMismatches() {
        NegotiationCase testCase = new NegotiationCase(
                "FT-EXTRACT-01/zh-CN",
                "FT-EXTRACT-01",
                "from-text/extraction-failures.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload("{\"relationship\":null}"))),
                null,
                null,
                null,
                failed(null, "negotiation_invalid_input", 1, List.of(), List.of()));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("FT-EXTRACT-01/zh-CN")
                        && failure.getMessage().contains("$.expect.code")
                        && failure.getMessage().contains("negotiation_slot_missing"),
                "the failure must carry the case id, the JSON path and both codes but was: " + failure.getMessage());
    }

    @Test
    void failsWithCaseIdAndJsonPathWhenTheLlmCallCountMismatches() {
        NegotiationCase testCase = acceptFromTextCase(ok(2, "information_accept", null, false), null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.llmCalls") && failure.getMessage().contains("2"),
                "a wrong llmCalls expectation must fail but was: " + failure.getMessage());
    }

    @Test
    void failsWithCaseIdAndJsonPathWhenTheGoldenNameMismatches() {
        NegotiationCase testCase = acceptFromTextCase(ok(1, "information_reject", null, false), null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.promptTextEqualsGolden"),
                "a wrong golden name must fail but was: " + failure.getMessage());
    }

    @Test
    void failsWhenASuccessCaseActuallyFails() {
        NegotiationCase testCase = new NegotiationCase(
                "FT-HAPPY-99/zh-CN",
                "FT-HAPPY-99",
                "from-text/happy.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Fail(LlmFailMarker.NON_JSON))),
                null,
                null,
                null,
                ok(1, "information_accept", null, false));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.outcome") && failure.getMessage().contains("failure"),
                "an unexpected failure must flip the outcome expectation but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ expectation helpers

    private static Expectation ok(
            @Nullable Integer llmCalls,
            @Nullable String promptTextEqualsGolden,
            @Nullable Metadata metadata,
            boolean differential) {
        return new Expectation(
                true,
                null,
                null,
                List.of(),
                List.of(),
                llmCalls,
                promptTextEqualsGolden,
                metadata,
                Map.of(),
                List.of(),
                differential);
    }

    private static Expectation okContracts(
            @Nullable Integer llmCalls, @Nullable String promptTextEqualsGolden, List<String> contracts) {
        return new Expectation(
                true, null, null, List.of(), List.of(), llmCalls, promptTextEqualsGolden, null, Map.of(), contracts,
                false);
    }

    private static Expectation okParams(
            @Nullable Integer llmCalls, Map<String, Object> params, List<String> contracts) {
        return new Expectation(
                true, null, null, List.of(), List.of(), llmCalls, null, null, params, contracts, false);
    }

    private static Expectation failed(
            @Nullable String exception,
            @Nullable String code,
            @Nullable Integer llmCalls,
            List<Expectation.SlotError> slotErrors,
            List<String> contracts) {
        return new Expectation(
                false, exception, code, List.of(), slotErrors, llmCalls, null, null, Map.of(), contracts, false);
    }

    // ------------------------------------------------------------------ case builders

    private NegotiationCase acceptFromTextCase(Expectation expect, @Nullable JsonNode inputData) {
        return new NegotiationCase(
                "FT-HAPPY-01/zh-CN",
                "FT-HAPPY-01",
                "from-text/happy.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "我确认第一阶段的信息。",
                inputData,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(ACCEPT_PAYLOAD))),
                null,
                null,
                null,
                expect);
    }

    private NegotiationCase proposeFromTextCase(Expectation expect, @Nullable JsonNode inputData) {
        return new NegotiationCase(
                "FT-HAPPY-02/zh-CN",
                "FT-HAPPY-02",
                "from-text/happy.json",
                NegotiationApi.GENERATE_PROPOSE_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_PROPOSE_URI,
                "请补充节能区域、节能速率保障目标与VLANId信息。",
                inputData,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(PROPOSE_PAYLOAD))),
                null,
                null,
                null,
                expect);
    }

    private NegotiationCase validateProposeCase(Expectation expect, String semanticPayload) {
        return new NegotiationCase(
                "VAL-HAPPY-01/zh-CN",
                "VAL-HAPPY-01",
                "validate/happy.json",
                NegotiationApi.VALIDATE_PROPOSE_PROMPT_AND_DATA_FILLING,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_PROPOSE_URI,
                null,
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(semanticPayload))),
                new PromptSource.Golden("information_propose"),
                schema,
                null,
                expect);
    }

    private static Expectation.Metadata metadata() {
        return new Expectation.Metadata(INFORMATION_ACCEPT_REJECT_URI, Boolean.TRUE);
    }
}
