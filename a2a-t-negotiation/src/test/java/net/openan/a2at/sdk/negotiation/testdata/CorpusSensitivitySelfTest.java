package net.openan.a2at.sdk.negotiation.testdata;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

/**
 * Meta-meta test of the corpus engine (design document §8.6, Q16): it guards against the deadliest failure mode of a
 * data-driven suite — an always-green rubber-stamp engine whose bug lets every case pass silently.
 *
 * <p>Every class of expectation assertion is proven sensitive on a minimal inline sample case: the sample runs green
 * with its true expectation, then one flipped expectation value must make the {@link CaseEngine} red, with the case id
 * and the JSON path of the flipped expectation in the failure message. The samples are built inline in Java and never
 * touch the corpus files.
 *
 * <p>The second guard, {@code engineMustNotCrashOnAnyCorpusCase}, walks the whole loaded corpus: the engine may report
 * red (an AssertionError is the engine correctly rejecting a mismatch — that is what the suites turn into test
 * failures), but it must never crash with an unexpected exception such as an NPE or a ClassCastException escaping the
 * run. A crash is an engine bug; a red assertion is the engine working.
 *
 * @since 2026-08
 */
class CorpusSensitivitySelfTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String ZH_CN = "zh-CN";

    private static final String INFORMATION_PROPOSE_URI = "Negotiation-T/information-negotiation/propose/v1";

    private static final String INFORMATION_ACCEPT_REJECT_URI =
            "Negotiation-T/information-negotiation/accept-reject/v1";

    /** Extraction payload mapping to the typed content of the information accept golden fixture. */
    private static final String ACCEPT_PAYLOAD =
            "{\"conclusion\":\"Accept\",\"items\":[{\"name\":\"energy-saving area information\",\"value\":\"Songshan"
                    + " Lake\"},{\"name\":\"energy-saving rate guarantee target\",\"value\":\"20Mbps\"}]}";

    /** Extraction payload whose mapped content lacks every required slot (fails with negotiation_slot_missing). */
    private static final String SLOTS_MISSING_PAYLOAD = "{\"relationship\":null}";

    /** Semantic verdict payload of a successful validation carrying one business parameter. */
    private static final String SEMANTIC_ACCEPT_PAYLOAD_WITH_PARAMS =
            "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{\"region\":"
                    + "\"Songshan Lake\"}}";

    /** Semantic verdict payload rejecting the message with two slot errors. */
    private static final String SEMANTIC_REJECT_PAYLOAD_TWO_ERRORS =
            "{\"semantic_verdict\":false,\"negotiation_type\":\"information\",\"errors\":[{\"slot_name\":"
                    + "\"region\",\"code\":\"missing\",\"message\":\"The region parameter is missing.\"},{\"slot_name\":"
                    + "\"rate\",\"code\":\"out_of_range\",\"message\":\"The rate parameter is out of range.\"}],"
                    + "\"params\":{}}";

    private final CaseEngine engine = new CaseEngine();

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode schema;

    @BeforeEach
    void readSchema() throws Exception {
        schema = mapper.readTree("{\"type\":\"object\",\"properties\":{\"region\":{\"type\":\"string\"}}}");
    }

    // ------------------------------------------------------------------ flipped expectations must be red

    @Test
    void flippedErrorCodeMustFailTheEngine() {
        NegotiationCase trueExpectation = fromTextCase(
                "FT-SENS-CODE",
                SLOTS_MISSING_PAYLOAD,
                failed("negotiation_slot_missing", 1, null, null));
        engine.run(trueExpectation);

        NegotiationCase flipped = fromTextCase(
                "FT-SENS-CODE-FLIP",
                SLOTS_MISSING_PAYLOAD,
                failed("negotiation_invalid_input", 1, null, null));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(flipped));

        assertTrue(
                failure.getMessage().contains("FT-SENS-CODE-FLIP") && failure.getMessage().contains("$.expect.code"),
                "a swapped error code must fail with the case id and the JSON path but was: " + failure.getMessage());
    }

    @Test
    void flippedLlmCallCountMustFailTheEngine() {
        engine.run(fromTextCase("FT-SENS-CALLS", ACCEPT_PAYLOAD, ok(1, "information_accept")));

        AssertionError tooHigh = assertThrows(
                AssertionError.class,
                () -> engine.run(fromTextCase("FT-SENS-CALLS-HIGH", ACCEPT_PAYLOAD, ok(2, "information_accept"))));
        assertTrue(
                tooHigh.getMessage().contains("FT-SENS-CALLS-HIGH")
                        && tooHigh.getMessage().contains("$.expect.llmCalls"),
                "llmCalls + 1 must fail but was: " + tooHigh.getMessage());

        AssertionError tooLow = assertThrows(
                AssertionError.class,
                () -> engine.run(fromTextCase("FT-SENS-CALLS-LOW", ACCEPT_PAYLOAD, ok(0, "information_accept"))));
        assertTrue(
                tooLow.getMessage().contains("FT-SENS-CALLS-LOW") && tooLow.getMessage().contains("$.expect.llmCalls"),
                "llmCalls - 1 must fail but was: " + tooLow.getMessage());
    }

    @Test
    void flippedGoldenNameMustFailTheEngine() {
        engine.run(fromTextCase("FT-SENS-GOLDEN", ACCEPT_PAYLOAD, ok(1, "information_accept")));

        NegotiationCase flipped =
                fromTextCase("FT-SENS-GOLDEN-FLIP", ACCEPT_PAYLOAD, ok(1, "information_reject"));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(flipped));

        assertTrue(
                failure.getMessage().contains("FT-SENS-GOLDEN-FLIP")
                        && failure.getMessage().contains("$.expect.promptTextEqualsGolden"),
                "a swapped golden fixture must fail but was: " + failure.getMessage());
    }

    @Test
    void tamperedParamsMustFailTheEngine() {
        engine.run(validateCase(
                "VAL-SENS-PARAMS", Map.of("id", SESSION_ID, "round", 2, "maxRounds", 5, "region", "Songshan Lake")));

        NegotiationCase tampered = validateCase(
                "VAL-SENS-PARAMS-FLIP",
                Map.of("id", SESSION_ID, "round", 2, "maxRounds", 5, "region", "Another Lake"));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(tampered));

        assertTrue(
                failure.getMessage().contains("VAL-SENS-PARAMS-FLIP")
                        && failure.getMessage().contains("$.expect.params")
                        && failure.getMessage().contains("Another Lake"),
                "a tampered params value must fail but was: " + failure.getMessage());
    }

    @Test
    void flippedOutcomeMustFailTheEngine() {
        NegotiationCase successReadAsFailure = fromTextCase(
                "FT-SENS-OUTCOME-SF",
                ACCEPT_PAYLOAD,
                failed("negotiation_invalid_input", 1, null, null));
        AssertionError successFlipped = assertThrows(
                AssertionError.class, () -> engine.run(successReadAsFailure));
        assertTrue(
                successFlipped.getMessage().contains("FT-SENS-OUTCOME-SF")
                        && successFlipped.getMessage().contains("$.expect.outcome"),
                "a success run expected to fail must be red but was: " + successFlipped.getMessage());

        NegotiationCase failureReadAsSuccess = fromTextCase(
                "FT-SENS-OUTCOME-FS",
                null,
                ok(1, "information_accept"));
        AssertionError failureFlipped = assertThrows(AssertionError.class, () -> engine.run(failureReadAsSuccess));
        assertTrue(
                failureFlipped.getMessage().contains("FT-SENS-OUTCOME-FS")
                        && failureFlipped.getMessage().contains("$.expect.outcome"),
                "a failing run expected to succeed must be red but was: " + failureFlipped.getMessage());
    }

    @Test
    void flippedSlotErrorsMustFailTheEngine() {
        List<Expectation.SlotError> both = List.of(
                new Expectation.SlotError("region", "missing"), new Expectation.SlotError("rate", "out_of_range"));
        engine.run(validateCase("VAL-SENS-SLOTS", null, both));

        NegotiationCase removed = validateCase(
                "VAL-SENS-SLOTS-DEL", null, List.of(new Expectation.SlotError("region", "missing")));
        AssertionError removalFailure = assertThrows(AssertionError.class, () -> engine.run(removed));
        assertTrue(
                removalFailure.getMessage().contains("VAL-SENS-SLOTS-DEL")
                        && removalFailure.getMessage().contains("$.expect.slotErrors"),
                "a removed slot error must fail but was: " + removalFailure.getMessage());

        List<Expectation.SlotError> three = List.of(
                new Expectation.SlotError("region", "missing"),
                new Expectation.SlotError("rate", "out_of_range"),
                new Expectation.SlotError("round", "out_of_range"));
        NegotiationCase added = validateCase("VAL-SENS-SLOTS-ADD", null, three);
        AssertionError additionFailure = assertThrows(AssertionError.class, () -> engine.run(added));
        assertTrue(
                additionFailure.getMessage().contains("VAL-SENS-SLOTS-ADD")
                        && additionFailure.getMessage().contains("$.expect.slotErrors"),
                "an added slot error must fail but was: " + additionFailure.getMessage());
    }

    // ------------------------------------------------------------------ engine crash guard

    @Test
    void engineMustNotCrashOnAnyCorpusCase() {
        LoadedCorpus corpus = CorpusSuites.loadCorpus();
        CaseEngine caseEngine = new CaseEngine();
        for (NegotiationCase testCase : corpus.cases()) {
            try {
                caseEngine.run(testCase);
            } catch (AssertionError redIsTheEngineWorking) {
                // A red assertion is the engine rejecting a mismatch; the suites turn it into a test failure.
            } catch (Throwable crash) {
                fail("The case engine crashed on " + testCase.errorPrefix() + ": " + crash);
            }
        }
        ScenarioEngine scenarioEngine = new ScenarioEngine();
        for (ScenarioCase scenario : corpus.scenarios()) {
            try {
                scenarioEngine.runScenario(scenario);
            } catch (AssertionError redIsTheEngineWorking) {
                // See above: red is not a crash.
            } catch (Throwable crash) {
                fail("The scenario engine crashed on " + scenario.id() + ": " + crash);
            }
        }
    }

    // ------------------------------------------------------------------ inline sample cases

    private NegotiationCase fromTextCase(
            String id, @Nullable String payload, Expectation expect) {
        LlmScript script = new LlmScript(
                null,
                List.of(payload == null
                        ? new LlmScriptStep.Fail(LlmFailMarker.NON_JSON)
                        : new LlmScriptStep.Payload(payload)));
        return new NegotiationCase(
                id + "/zh-CN",
                id,
                "from-text/sensitivity-probes.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                null,
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                null,
                script,
                null,
                null,
                null,
                expect);
    }

    private NegotiationCase validateCase(String id, @Nullable Map<String, Object> params) {
        return validateCase(id, params, null);
    }

    private NegotiationCase validateCase(
            String id, @Nullable Map<String, Object> params, @Nullable List<Expectation.SlotError> slotErrors) {
        Expectation expect;
        if (params == null) {
            expect = new Expectation(
                    false,
                    null,
                    "negotiation_semantic_rejected",
                    List.of(),
                    slotErrors == null ? List.of() : slotErrors,
                    1,
                    null,
                    null,
                    Map.of(),
                    List.of(),
                    false);
        } else {
            expect = new Expectation(
                    true, null, null, List.of(), List.of(), 1, null, null, params, List.of(), false);
        }
        String payload = params == null ? SEMANTIC_REJECT_PAYLOAD_TWO_ERRORS : SEMANTIC_ACCEPT_PAYLOAD_WITH_PARAMS;
        return new NegotiationCase(
                id + "/zh-CN",
                id,
                "validate/sensitivity-probes.json",
                NegotiationApi.VALIDATE_PROPOSE_PROMPT_AND_DATA_FILLING,
                ZH_CN,
                null,
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_PROPOSE_URI,
                null,
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(payload))),
                new PromptSource.Golden("information_propose"),
                schema,
                null,
                expect);
    }

    private static Expectation ok(@Nullable Integer llmCalls, @Nullable String promptTextEqualsGolden) {
        return new Expectation(
                true, null, null, List.of(), List.of(), llmCalls, promptTextEqualsGolden, null, Map.of(), List.of(),
                false);
    }

    private static Expectation failed(
            @Nullable String code,
            @Nullable Integer llmCalls,
            @Nullable Map<String, Object> params,
            @Nullable List<Expectation.SlotError> slotErrors) {
        return new Expectation(
                false,
                null,
                code,
                List.of(),
                slotErrors == null ? List.of() : slotErrors,
                llmCalls,
                null,
                null,
                params == null ? Map.of() : params,
                List.of(),
                false);
    }
}
