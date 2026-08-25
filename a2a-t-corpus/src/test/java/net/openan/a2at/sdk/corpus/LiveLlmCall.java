package net.openan.a2at.sdk.corpus;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One LLM call recorded by {@link RecordingLLMClient}: the request the pipeline issued plus the response (or the
 * failure) the live endpoint produced, and the wall-clock duration of the call.
 *
 * <p>A call that threw carries a non-null {@code error} (the exception class name and message) and null response
 * fields; a call that succeeded carries null {@code error}. The record is the unit both of the {@code maxLlmCalls}
 * upper bound and of the per-case {@code llmCalls} section of the live transcript.
 *
 * @param messages messages of the request, one map per message
 * @param jsonSchema JSON schema of the request, or null when the pipeline passed none
 * @param temperature temperature of the request, or null when the request carries none
 * @param maxTokens max-tokens override of the request, or null when the request carries none
 * @param content raw response content, or null when the call failed or answered null
 * @param model resolved model name of the response, or null when the call failed or answered null
 * @param usage token usage summary of the response, empty when the call failed or answered null
 * @param durationMs wall-clock duration of the call in milliseconds
 * @param error exception class name and message when the call threw, or null on success
 * @since 2026-08
 */
public record LiveLlmCall(
        List<Map<String, String>> messages,
        @Nullable Map<String, Object> jsonSchema,
        @Nullable Double temperature,
        @Nullable Integer maxTokens,
        @Nullable String content,
        @Nullable String model,
        Map<String, Integer> usage,
        long durationMs,
        @Nullable String error) {

    public LiveLlmCall {
        messages = messages == null ? List.of() : List.copyOf(messages);
        usage = usage == null ? Map.of() : Map.copyOf(usage);
    }

    /**
     * Returns whether this call threw instead of answering.
     *
     * @return true when the call failed with an exception
     */
    public boolean failed() {
        return error != null;
    }
}
