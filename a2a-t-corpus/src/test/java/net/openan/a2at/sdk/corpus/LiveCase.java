package net.openan.a2at.sdk.corpus;

import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

/**
 * One live-LLM corpus case record expanded for exactly one language: the sibling of {@link NegotiationCase} that
 * runs against a real LLM endpoint instead of a scripted one.
 *
 * <p>The live family lives under {@code live/} of the corpus root and shares the base record fields with the offline
 * family, but carries no {@code llm} script, no {@code inject} hook and no typed {@code inputData} — the behavior
 * under test comes from the real model — and replaces the offline expectation block with the looser
 * {@link LiveExpectation} (subset assertions and an LLM call upper bound). Live phase 1 covers the two TASK APIs
 * and zh-CN only; the loader enforces both, and live records land in {@link LoadedCorpus#liveCases()}, never in the
 * offline {@code cases} list.
 *
 * @param id expanded id such as {@code LIVE-GEN-01/zh-CN}
 * @param baseId corpus record id before the language expansion, such as {@code LIVE-GEN-01}
 * @param sourceFile corpus file path relative to the corpus root, such as {@code live/generate.json}
 * @param api the TASK-family API under test
 * @param language language this expansion renders for (zh-CN in live phase 1)
 * @param priority P0, P1 or P2, or null when the record does not state one
 * @param tags free tags, empty when the record states none
 * @param summary business-facing one-line summary, or null when the record states none
 * @param context inline negotiation context spec, or null for the null-context probes
 * @param templateUri template URI, or null when the case probes a null URI
 * @param inputText free-text input of this language, or null when the case declares none
 * @param prompt resolved prompt source of the validate API, or null otherwise
 * @param schema resolved JSON Schema of the validate API, or null otherwise
 * @param liveExpect validated live expectation block
 * @since 2026-08
 */
public record LiveCase(
        String id,
        String baseId,
        String sourceFile,
        NegotiationApi api,
        String language,
        @Nullable String priority,
        List<String> tags,
        @Nullable String summary,
        @Nullable ContextSpec context,
        @Nullable String templateUri,
        @Nullable String inputText,
        @Nullable PromptSource prompt,
        @Nullable JsonNode schema,
        LiveExpectation liveExpect) {

    public LiveCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(baseId, "baseId");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(liveExpect, "liveExpect");
        tags = List.copyOf(tags);
    }

    /**
     * Returns the JSON path prefix for failure messages of this case, naming the file and the expanded id.
     *
     * @return prefix such as {@code live/generate.json [LIVE-GEN-01/zh-CN]}
     */
    public String errorPrefix() {
        return sourceFile + " [" + id + "]";
    }
}
