package net.openan.a2at.sdk.negotiation.testdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests of {@link ScriptedNegotiationLlmClient}: strict step-by-step consumption, the six failure markers,
 * the call and message recording, and the fail-on-overconsumption default that forbids repeat-last semantics.
 */
class ScriptedNegotiationLlmClientTest {

    private static final List<Map<String, String>> MESSAGES =
            List.of(Map.of("role", "system", "content", "system prompt"), Map.of("role", "user", "content", "user prompt"));

    @Test
    void consumesPayloadStepsStrictlyInOrder() {
        ScriptedNegotiationLlmClient client = new ScriptedNegotiationLlmClient(
                List.of(new LlmScriptStep.Payload("{\"a\":1}"), new LlmScriptStep.Payload("{\"a\":2}")));

        LLMResponse first = client.structured(MESSAGES, Map.of(), null, null);
        LLMResponse second = client.structured(MESSAGES, Map.of(), null, null);

        assertEquals("{\"a\":1}", first.content());
        assertEquals("{\"a\":2}", second.content());
        assertEquals(2, client.callCount());
        assertEquals(2, client.recordedMessages().size());
        assertEquals(MESSAGES, client.lastMessages());
        assertEquals("scripted-model", first.model());
    }

    @Test
    void runtimeExceptionMarkerThrowsAnInfrastructureRuntimeException() {
        ScriptedNegotiationLlmClient client =
                new ScriptedNegotiationLlmClient(List.of(new LlmScriptStep.Fail(LlmFailMarker.RUNTIME_EXCEPTION)));

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> client.structured(MESSAGES, Map.of(), null, null));

        assertEquals(ScriptedNegotiationLlmClient.RUNTIME_EXCEPTION_DETAIL, failure.getMessage());
        assertEquals(1, client.callCount());
        assertEquals(List.of(ScriptedNegotiationLlmClient.RUNTIME_EXCEPTION_DETAIL), client.leakedFailureDetails());
    }

    @Test
    void llmErrorMarkerThrowsTheLlmInfrastructureErrorType() {
        ScriptedNegotiationLlmClient client =
                new ScriptedNegotiationLlmClient(List.of(new LlmScriptStep.Fail(LlmFailMarker.LLM_ERROR)));

        LLMRuntimeError failure =
                assertThrows(LLMRuntimeError.class, () -> client.structured(MESSAGES, Map.of(), null, null));

        assertEquals(ScriptedNegotiationLlmClient.LLM_ERROR_DETAIL, failure.getMessage());
        assertEquals(1, client.callCount());
        assertEquals(List.of(ScriptedNegotiationLlmClient.LLM_ERROR_DETAIL), client.leakedFailureDetails());
    }

    @Test
    void nullResponseMarkerReturnsNull() {
        ScriptedNegotiationLlmClient client =
                new ScriptedNegotiationLlmClient(List.of(new LlmScriptStep.Fail(LlmFailMarker.NULL_RESPONSE)));

        assertNull(client.structured(MESSAGES, Map.of(), null, null));
        assertEquals(1, client.callCount());
    }

    @Test
    void blankContentMarkerAnswersABlankPayload() {
        ScriptedNegotiationLlmClient client =
                new ScriptedNegotiationLlmClient(List.of(new LlmScriptStep.Fail(LlmFailMarker.BLANK_CONTENT)));

        LLMResponse response = client.structured(MESSAGES, Map.of(), null, null);

        assertNotNull(response);
        assertTrue(response.content().isBlank());
        assertEquals(1, client.callCount());
    }

    @Test
    void nonJsonMarkerAnswersAnUnparseablePayload() {
        ScriptedNegotiationLlmClient client =
                new ScriptedNegotiationLlmClient(List.of(new LlmScriptStep.Fail(LlmFailMarker.NON_JSON)));

        LLMResponse response = client.structured(MESSAGES, Map.of(), null, null);

        assertEquals(ScriptedNegotiationLlmClient.NON_JSON_CONTENT, response.content());
        assertEquals(1, client.callCount());
    }

    @Test
    void assertionMarkerFailsOnAnyCall() {
        ScriptedNegotiationLlmClient client = ScriptedNegotiationLlmClient.assertionOnly();

        AssertionError failure =
                assertThrows(AssertionError.class, () -> client.structured(MESSAGES, Map.of(), null, null));

        assertTrue(
                failure.getMessage().contains("No LLM call was expected"),
                "the assertion marker must name the zero-call expectation but was: " + failure.getMessage());
        assertEquals(1, client.callCount());
    }

    @Test
    void exhaustedScriptFailsInsteadOfRepeatingTheLastAnswer() {
        ScriptedNegotiationLlmClient client =
                new ScriptedNegotiationLlmClient(List.of(new LlmScriptStep.Payload("{\"a\":1}")));

        client.structured(MESSAGES, Map.of(), null, null);
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> client.structured(MESSAGES, Map.of(), null, null));

        assertTrue(
                failure.getMessage().contains("consumed its whole script"),
                "the over-consumption failure must explain the exhausted script but was: " + failure.getMessage());
        assertEquals(2, client.callCount(), "the rejected call still counts as a call");
    }

    @Test
    void overconsumptionCanBeDisabledForEngineSelfTests() {
        ScriptedNegotiationLlmClient client =
                new ScriptedNegotiationLlmClient(List.of(new LlmScriptStep.Payload("{\"a\":1}")), false);

        client.structured(MESSAGES, Map.of(), null, null);
        LLMResponse repeated = client.structured(MESSAGES, Map.of(), null, null);

        assertEquals("{\"a\":1}", repeated.content(), "the explicit escape hatch repeats the last step");
        assertEquals(2, client.callCount());
    }
}
