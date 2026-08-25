package net.openan.a2at.sdk.corpus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.jspecify.annotations.Nullable;

/**
 * Recording decorator around the real LLM client of the live corpus (design document §4): it forwards every call to
 * the delegate untouched — the wrapped client is the real provider instance, so the production behavior does not
 * deviate — and records the request, the response (or the thrown exception) and the wall-clock duration of each call.
 *
 * <p>The recorded calls are the single source of the live family's calibration points: the {@code maxLlmCalls} upper
 * bound is checked against {@link #callCount()}, and the transcript embeds {@link #snapshot()} verbatim. A call that
 * throws is recorded and then rethrown, so an infrastructure failure stays a failure while still showing up in the
 * transcript.
 *
 * <p>JUnit runs the corpus suites on one thread, but the client is defensive anyway: all accessors are synchronized
 * and every read returns an immutable copy.
 *
 * @since 2026-08
 */
public final class RecordingLLMClient implements LLMClient {

    private final LLMClient delegate;

    private final List<LiveLlmCall> calls = new ArrayList<>();

    /**
     * Creates a recording wrapper around the real delegate client.
     *
     * @param delegate the real provider client, such as the {@code OpenAIClient} built by the live harness
     */
    public RecordingLLMClient(LLMClient delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public synchronized LLMResponse structured(
            List<Map<String, String>> messages,
            Map<String, Object> jsonSchema,
            Double temperature,
            Integer maxTokens) {
        long startNanos = System.nanoTime();
        LLMResponse response = null;
        String error = null;
        try {
            response = delegate.structured(messages, jsonSchema, temperature, maxTokens);
            return response;
        } catch (RuntimeException exception) {
            error = exception.getClass().getName() + ": " + exception.getMessage();
            throw exception;
        } finally {
            calls.add(new LiveLlmCall(
                    messages,
                    jsonSchema,
                    temperature,
                    maxTokens,
                    response == null ? null : response.content(),
                    response == null ? null : response.model(),
                    response == null ? Map.of() : sanitizedUsage(response.usage()),
                    Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                    error));
        }
    }

    /**
     * Copies the delegate's usage summary defensively: a lenient OpenAI-compatible endpoint may answer a JSON null
     * value (such as {@code {"prompt_tokens": null}}), and passing that on would make the recording itself throw and
     * mask the delegate outcome — so null-valued entries are dropped instead.
     */
    private static Map<String, Integer> sanitizedUsage(@Nullable Map<String, Integer> usage) {
        if (usage == null) {
            return Map.of();
        }
        Map<String, Integer> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : usage.entrySet()) {
            if (entry.getValue() != null) {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized;
    }

    /**
     * Returns the number of LLM calls made through this client, including the calls that threw.
     *
     * @return non-negative call count
     */
    public synchronized int callCount() {
        return calls.size();
    }

    /**
     * Returns the transcript-facing snapshot of every recorded call, one entry per call in call order; the engine
     * embeds this list into the live transcript's per-case {@code llmCalls} section.
     *
     * @return immutable list of recorded calls at snapshot time
     */
    public synchronized List<LiveLlmCall> snapshot() {
        return List.copyOf(calls);
    }
}
