package net.openan.a2at.sdk.negotiation.testdata;

import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

/**
 * One corpus case record expanded for exactly one language.
 *
 * <p>The loader expands every corpus record once per entry of its {@code languages} array, appending {@code /<language>}
 * to the id (Q3). References are already resolved: the LLM script carries literal payload texts or failure markers and
 * the schema is the addressed JSON Schema node. A scenario step is also carried as a {@code NegotiationCase} with the
 * derived id {@code <scenarioId>/<language>#step-<n>}.
 *
 * @param id expanded id such as {@code FT-RETRY-02/zh-CN} or {@code SC-INFO-02/en-US#step-1}
 * @param baseId corpus record id before the language expansion, such as {@code FT-RETRY-02}
 * @param sourceFile corpus file path relative to the corpus root, such as {@code from-text/retry.json}
 * @param api the {@code NegotiationContentService} API under test
 * @param language language this expansion renders for
 * @param priority P0, P1 or P2, or null when the record does not state one
 * @param tags free tags, empty when the record states none
 * @param summary business-facing one-line summary, or null when the record states none
 * @param context inline negotiation context spec, or null for the null-context probes
 * @param templateUri template URI, or null when the case probes a null URI
 * @param inputText free-text input of this language, or null for the from-data and validate families
 * @param inputData language-independent typed input data of the from-data family and the differential runs
 * @param llm resolved scripted LLM behavior, or null when the record scripts none
 * @param prompt resolved prompt source of the validate family, or null otherwise
 * @param schema resolved JSON Schema of the validate family, or null otherwise
 * @param inject harness injection hook name, or null when the case injects none
 * @param expect validated expectation block
 * @since 2026-08
 */
public record NegotiationCase(
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
        @Nullable JsonNode inputData,
        @Nullable LlmScript llm,
        @Nullable PromptSource prompt,
        @Nullable JsonNode schema,
        @Nullable String inject,
        Expectation expect) {

    public NegotiationCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(baseId, "baseId");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(expect, "expect");
        tags = List.copyOf(tags);
    }

    /**
     * Returns the JSON path prefix for failure messages of this case, naming the file and the expanded id.
     *
     * @return prefix such as {@code from-text/retry.json [FT-RETRY-02/zh-CN]}
     */
    public String errorPrefix() {
        return sourceFile + " [" + id + "]";
    }
}
