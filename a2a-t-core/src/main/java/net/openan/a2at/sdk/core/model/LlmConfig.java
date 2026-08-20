package net.openan.a2at.sdk.core.model;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured LLM runtime configuration resolved from unified SDK config.
 *
 * @since 2026-06
 */
public record LlmConfig(
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        int historyWindow,
        int maxTokens,
        double temperature,
        double timeoutSeconds,
        int sessionMaxTotal,
        int sessionMaxPerProvider,
        int maxAttempts) {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmConfig.class);

    private static final String DEFAULT_PROVIDER = "openai";

    private static final int DEFAULT_HISTORY_WINDOW = 12;

    private static final int DEFAULT_MAX_TOKENS = 2048;

    private static final double DEFAULT_TEMPERATURE = 0.2d;

    private static final double DEFAULT_TIMEOUT_SECONDS = 30.0d;

    private static final int DEFAULT_SESSION_MAX_TOTAL = 300;

    private static final int DEFAULT_SESSION_MAX_PER_PROVIDER = 100;

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private static final int MAX_ATTEMPTS_LOWER_BOUND = 1;

    private static final int MAX_ATTEMPTS_UPPER_BOUND = 10;

    /**
     * Builds one LLM config from raw `.env` values.
     *
     * @param values raw config values
     * @return resolved LLM config
     */
    public static LlmConfig fromMap(Map<String, String> values) {
        return new LlmConfig(
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.PROVIDER), DEFAULT_PROVIDER),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.MODEL), ""),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.API_KEY), ""),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.BASE_URL), ""),
                NumberUtils.toInt(values.get(A2ATConfigKeys.Llm.HISTORY_WINDOW), DEFAULT_HISTORY_WINDOW),
                NumberUtils.toInt(values.get(A2ATConfigKeys.Llm.MAX_TOKENS), DEFAULT_MAX_TOKENS),
                NumberUtils.toDouble(values.get(A2ATConfigKeys.Llm.TEMPERATURE), DEFAULT_TEMPERATURE),
                NumberUtils.toDouble(values.get(A2ATConfigKeys.Llm.TIMEOUT_SECONDS), DEFAULT_TIMEOUT_SECONDS),
                NumberUtils.toInt(values.get(A2ATConfigKeys.Llm.SESSION_MAX_TOTAL), DEFAULT_SESSION_MAX_TOTAL),
                NumberUtils.toInt(
                        values.get(A2ATConfigKeys.Llm.SESSION_MAX_PER_PROVIDER), DEFAULT_SESSION_MAX_PER_PROVIDER),
                parseMaxAttempts(values.get(A2ATConfigKeys.Llm.MAX_ATTEMPTS)));
    }

    /**
     * Resolves the retry attempt limit for LLM steps from one raw config value.
     *
     * <p>Missing or blank values use the default of 3. Unparseable values fall back to the default. Values outside the
     * allowed range 1-10 are clamped to the nearest bound. Both the fallback and the clamp cases emit a warning log
     * carrying the raw value.
     *
     * @param rawValue raw config value, may be null or blank
     * @return resolved attempt limit between 1 and 10 inclusive
     */
    private static int parseMaxAttempts(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        try {
            int parsed = Integer.parseInt(rawValue.trim());
            if (parsed < MAX_ATTEMPTS_LOWER_BOUND) {
                LOGGER.atWarn()
                        .log(
                                "LLM max attempts value is below the allowed minimum, clamped to bound. key={} raw_value={} clamped_value={}",
                                A2ATConfigKeys.Llm.MAX_ATTEMPTS,
                                rawValue,
                                MAX_ATTEMPTS_LOWER_BOUND);
                return MAX_ATTEMPTS_LOWER_BOUND;
            }
            if (parsed > MAX_ATTEMPTS_UPPER_BOUND) {
                LOGGER.atWarn()
                        .log(
                                "LLM max attempts value is above the allowed maximum, clamped to bound. key={} raw_value={} clamped_value={}",
                                A2ATConfigKeys.Llm.MAX_ATTEMPTS,
                                rawValue,
                                MAX_ATTEMPTS_UPPER_BOUND);
                return MAX_ATTEMPTS_UPPER_BOUND;
            }
            return parsed;
        } catch (NumberFormatException error) {
            LOGGER.atWarn()
                    .log(
                            "LLM max attempts value is not a valid integer, falling back to default. key={} raw_value={} default_value={}",
                            A2ATConfigKeys.Llm.MAX_ATTEMPTS,
                            rawValue,
                            DEFAULT_MAX_ATTEMPTS);
            return DEFAULT_MAX_ATTEMPTS;
        }
    }
}
