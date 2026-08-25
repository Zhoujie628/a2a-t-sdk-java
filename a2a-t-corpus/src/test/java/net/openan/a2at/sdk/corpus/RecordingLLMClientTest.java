package net.openan.a2at.sdk.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests of {@link RecordingLLMClient}: transparent forwarding to the real delegate, full recording of the
 * request, the response and the duration of every call, and the record-and-rethrow behavior on infrastructure
 * failures.
 */
class RecordingLLMClientTest {

    private static final List<Map<String, String>> MESSAGES =
            List.of(Map.of("role", "system", "content", "system prompt"), Map.of("role", "user", "content", "user prompt"));

    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties", Map.of());

    @Test
    void forwardsToTheDelegateAndRecordsRequestAndResponse() {
        RecordingLLMClient client = new RecordingLLMClient(new StubDelegate(
                new LLMResponse("{\"a\":1}", "qwen3-27b", Map.of("prompt_tokens", 11, "completion_tokens", 7), Map.of())));

        LLMResponse response = client.structured(MESSAGES, SCHEMA, 0.0, 512);

        assertEquals("{\"a\":1}", response.content(), "the wrapper must forward the delegate answer untouched");
        assertEquals(1, client.callCount());
        LiveLlmCall call = client.snapshot().get(0);
        assertEquals(MESSAGES, call.messages());
        assertEquals(SCHEMA, call.jsonSchema());
        assertEquals(0.0, call.temperature());
        assertEquals(512, call.maxTokens());
        assertEquals("{\"a\":1}", call.content());
        assertEquals("qwen3-27b", call.model());
        assertEquals(Map.of("prompt_tokens", 11, "completion_tokens", 7), call.usage());
        assertNull(call.error());
        assertTrue(call.durationMs() >= 0, "the wall-clock duration is recorded even when it rounds to zero");
        assertFalse(call.failed());
    }

    @Test
    void nullSchemaAndOverridesPassThroughAsNulls() {
        RecordingLLMClient client = new RecordingLLMClient(new StubDelegate(new LLMResponse("{}", "m", Map.of(), Map.of())));

        client.structured(MESSAGES, null, null, null);

        LiveLlmCall call = client.snapshot().get(0);
        assertNull(call.jsonSchema());
        assertNull(call.temperature());
        assertNull(call.maxTokens());
        assertEquals(Map.of(), call.usage(), "a null usage records as an empty map");
    }

    @Test
    void aNullValuedUsageEntryIsDroppedInsteadOfBreakingTheRecording() {
        // A lenient endpoint may answer {"prompt_tokens": null}; the recording must survive it and keep the answered
        // values, never mask the delegate outcome with a null-hostile map copy.
        Map<String, Integer> lenientUsage = new LinkedHashMap<>();
        lenientUsage.put("prompt_tokens", null);
        lenientUsage.put("completion_tokens", 5);
        RecordingLLMClient client =
                new RecordingLLMClient(new StubDelegate(new LLMResponse("{}", "m", lenientUsage, Map.of())));

        client.structured(MESSAGES, SCHEMA, null, null);

        assertEquals(Map.of("completion_tokens", 5), client.snapshot().get(0).usage());
    }

    @Test
    void aNullDelegateAnswerRecordsNullResponseFields() {
        RecordingLLMClient client = new RecordingLLMClient(new StubDelegate(null));

        assertNull(client.structured(MESSAGES, SCHEMA, null, null));

        LiveLlmCall call = client.snapshot().get(0);
        assertEquals(1, client.callCount(), "the answered-null call counts as a call");
        assertNull(call.content());
        assertNull(call.model());
        assertNull(call.error());
    }

    @Test
    void anInfrastructureFailureIsRecordedAndRethrown() {
        LLMRuntimeError failure = new LLMRuntimeError("connection reset");
        RecordingLLMClient client = new RecordingLLMClient(new FailingDelegate(failure));

        LLMRuntimeError thrown =
                assertThrows(LLMRuntimeError.class, () -> client.structured(MESSAGES, SCHEMA, null, null));

        assertEquals(failure, thrown, "the wrapper must rethrow the original exception, not wrap it");
        assertEquals(1, client.callCount(), "the failed call counts as a call");
        LiveLlmCall call = client.snapshot().get(0);
        assertTrue(call.failed());
        assertTrue(
                call.error().contains(LLMRuntimeError.class.getName()) && call.error().contains("connection reset"),
                "the error record names the exception class and message, but was: " + call.error());
        assertNull(call.content());
        assertEquals(List.of(call), client.snapshot(), "the snapshot carries the failed call for the transcript");
    }

    @Test
    void callsRecordInOrderAndTheAccessorsReturnImmutableCopies() {
        RecordingLLMClient client = new RecordingLLMClient(new StubDelegate(
                new LLMResponse("{\"a\":1}", "m", Map.of(), Map.of()),
                new LLMResponse("{\"a\":2}", "m", Map.of(), Map.of())));

        client.structured(MESSAGES, SCHEMA, null, null);
        client.structured(MESSAGES, SCHEMA, null, null);

        assertEquals(2, client.callCount());
        assertEquals("{\"a\":1}", client.snapshot().get(0).content());
        assertEquals("{\"a\":2}", client.snapshot().get(1).content());
        List<LiveLlmCall> snapshot = client.snapshot();
        assertEquals(2, snapshot.size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.add(new LiveLlmCall(MESSAGES, null, null, null, null, null, Map.of(), 0, null)),
                "the snapshot must be an immutable list");
    }

    /** Hand-written fake of the real delegate: it answers its canned responses one by one, null once exhausted. */
    private static final class StubDelegate implements LLMClient {

        private final List<LLMResponse> responses;

        private int cursor;

        private StubDelegate(LLMResponse... responses) {
            this.responses = responses == null ? List.of() : List.of(responses);
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return cursor < responses.size() ? responses.get(cursor++) : null;
        }
    }

    /** Hand-written fake of the real delegate that always throws the given infrastructure failure. */
    private static final class FailingDelegate implements LLMClient {

        private final LLMRuntimeError failure;

        private FailingDelegate(LLMRuntimeError failure) {
            this.failure = failure;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            throw failure;
        }
    }
}
