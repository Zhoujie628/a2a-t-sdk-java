package net.openan.a2at.sdk.corpus;

/**
 * The aggregate of one live corpus run (design document §5): the case verdict counts, the total LLM traffic and the
 * schema-adherence statistic.
 *
 * <p>{@code schemaParseFailureCount} is the M5 schema-adherence data point: the number of successful LLM responses
 * whose content did not parse as a JSON object — the share the design's Q10 decision measures before deciding whether
 * a native {@code json_schema} request mode is needed.
 *
 * @param totalCases number of cases appended to the run
 * @param passCount number of PASS cases
 * @param failCount number of FAIL cases
 * @param skipCount number of SKIP cases
 * @param errorCount number of ERROR cases
 * @param totalLlmCalls number of LLM calls across all cases, including the calls that threw
 * @param totalPromptTokens sum of the {@code prompt_tokens} usage entries across all recorded calls
 * @param totalCompletionTokens sum of the {@code completion_tokens} usage entries across all recorded calls
 * @param schemaParseFailureCount number of answered calls whose content did not parse as a JSON object
 * @since 2026-08
 */
public record LiveRunSummary(
        int totalCases,
        int passCount,
        int failCount,
        int skipCount,
        int errorCount,
        int totalLlmCalls,
        long totalPromptTokens,
        long totalCompletionTokens,
        int schemaParseFailureCount) {}
