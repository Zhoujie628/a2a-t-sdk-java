package net.openan.a2at.sdk.negotiation.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;

/**
 * Scripted LLM client double of the negotiation scenario tests.
 *
 * <p>The client replays an ordered list of JSON response payloads — the last payload repeats once the list is exhausted
 * — and records the messages of every call so the tests can assert the call counts and the prompt content of each LLM
 * step. No real network access happens.
 */
final class ScriptedLlmClient implements LLMClient {

    private final List<String> payloads;

    private final List<List<Map<String, String>>> recordedMessages = new ArrayList<>();

    ScriptedLlmClient(String... payloads) {
        if (payloads.length == 0) {
            throw new IllegalArgumentException("At least one scripted payload is required.");
        }
        this.payloads = List.of(payloads);
    }

    @Override
    public LLMResponse structured(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema, Double temperature, Integer maxTokens) {
        recordedMessages.add(List.copyOf(messages));
        String payload = payloads.get(Math.min(recordedMessages.size() - 1, payloads.size() - 1));
        return new LLMResponse(
                payload, "scripted-scenario-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
    }

    int callCount() {
        return recordedMessages.size();
    }

    String systemContentOfCall(int callIndex) {
        return roleContentOfCall(callIndex, "system");
    }

    String userContentOfCall(int callIndex) {
        return roleContentOfCall(callIndex, "user");
    }

    private String roleContentOfCall(int callIndex, String role) {
        return recordedMessages.get(callIndex).stream()
                .filter(message -> role.equals(message.get("role")))
                .map(message -> message.get("content"))
                .findFirst()
                .orElseThrow();
    }
}
