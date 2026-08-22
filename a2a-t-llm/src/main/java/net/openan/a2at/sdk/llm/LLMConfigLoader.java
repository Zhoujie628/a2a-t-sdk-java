package net.openan.a2at.sdk.llm;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.openan.a2at.sdk.core.model.DotEnvConfigSource;

/**
 * Loads LLM client configuration from `.env` files.
 *
 * @since 2026-06
 */
public final class LLMConfigLoader {

    private static final int DEFAULT_HISTORY_WINDOW = 10;

    private static final int DEFAULT_SESSION_MAX_TOTAL = 300;

    private static final int DEFAULT_SESSION_MAX_PER_PROVIDER = 100;

    private static final int MAX_HISTORY_WINDOW = 100;

    private static final int MAX_SESSION_MAX_TOTAL = 3000;

    private static final int MAX_SESSION_MAX_PER_PROVIDER = 1000;

    /** Reasoning effort levels accepted by A2AT_LLM_REASONING_EFFORT (mirrors the OpenAI API enum). */
    private static final java.util.Set<String> REASONING_EFFORT_VALUES =
            java.util.Set.of("none", "minimal", "low", "medium", "high", "xhigh");

    private LLMConfigLoader() {}

    public static LLMClientConfig load(Path envPath) {
        Map<String, String> values = DotEnvConfigSource.load(envPath);
        String provider = required(values, "A2AT_LLM_PROVIDER");
        String model = required(values, "A2AT_LLM_MODEL");
        String apiKey = required(values, "A2AT_LLM_API_KEY");

        int historyWindow = parseBoundedInt(
                values.get("A2AT_LLM_HISTORY_WINDOW"),
                "A2AT_LLM_HISTORY_WINDOW",
                DEFAULT_HISTORY_WINDOW,
                MAX_HISTORY_WINDOW);
        int sessionMaxTotal = parseBoundedInt(
                values.get("A2AT_LLM_SESSION_MAX_TOTAL"),
                "A2AT_LLM_SESSION_MAX_TOTAL",
                DEFAULT_SESSION_MAX_TOTAL,
                MAX_SESSION_MAX_TOTAL);
        int sessionMaxPerProvider = parseBoundedInt(
                values.get("A2AT_LLM_SESSION_MAX_PER_PROVIDER"),
                "A2AT_LLM_SESSION_MAX_PER_PROVIDER",
                DEFAULT_SESSION_MAX_PER_PROVIDER,
                MAX_SESSION_MAX_PER_PROVIDER);
        if (sessionMaxTotal < sessionMaxPerProvider) {
            throw new LLMConfigError("A2AT_LLM_SESSION_MAX_TOTAL must be greater than or equal to "
                    + "A2AT_LLM_SESSION_MAX_PER_PROVIDER");
        }

        OptionalInt maxTokens = parseOptionalInt(values.get("A2AT_LLM_MAX_TOKENS"), "A2AT_LLM_MAX_TOKENS");
        OptionalDouble temperature = parseOptionalDouble(values.get("A2AT_LLM_TEMPERATURE"), "A2AT_LLM_TEMPERATURE");
        OptionalDouble timeoutSeconds =
                parseOptionalDouble(values.get("A2AT_LLM_TIMEOUT_SECONDS"), "A2AT_LLM_TIMEOUT_SECONDS");
        boolean disableSystemProxy = parseOptionalBoolean(
                values.get("A2AT_LLM_DISABLE_SYSTEM_PROXY"), "A2AT_LLM_DISABLE_SYSTEM_PROXY", false);

        return new LLMClientConfig(
                provider,
                model,
                apiKey,
                optional(values.get("A2AT_LLM_BASE_URL")).orElse(null),
                historyWindow,
                maxTokens.isPresent() ? maxTokens.getAsInt() : null,
                temperature.isPresent() ? temperature.getAsDouble() : null,
                timeoutSeconds.isPresent() ? timeoutSeconds.getAsDouble() : null,
                disableSystemProxy,
                sessionMaxTotal,
                sessionMaxPerProvider,
                parseReasoningEffort(values.get("A2AT_LLM_REASONING_EFFORT")));
    }

    private static String required(Map<String, String> values, String key) {
        return optional(values.get(key))
                .orElseThrow(() -> new LLMConfigError(
                        "A2AT_LLM_PROVIDER, A2AT_LLM_MODEL, and A2AT_LLM_API_KEY must be set in the .env file"));
    }

    private static Optional<String> optional(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    private static OptionalInt parseOptionalInt(String rawValue, String key) {
        Optional<String> value = optional(rawValue);
        if (!value.isPresent()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value.orElseThrow()));
        } catch (NumberFormatException exception) {
            throw new LLMConfigError(key + " must be an integer", exception);
        }
    }

    private static OptionalDouble parseOptionalDouble(String rawValue, String key) {
        Optional<String> value = optional(rawValue);
        if (!value.isPresent()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(value.orElseThrow()));
        } catch (NumberFormatException exception) {
            throw new LLMConfigError(key + " must be a float", exception);
        }
    }

    private static boolean parseOptionalBoolean(String rawValue, String key, boolean defaultValue) {
        Optional<String> value = optional(rawValue);
        if (!value.isPresent()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value.orElseThrow())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.orElseThrow())) {
            return false;
        }
        throw new LLMConfigError(key + " must be true or false");
    }

    private static int parseBoundedInt(String rawValue, String key, int defaultValue, int maxValue) {
        Optional<String> value = optional(rawValue);
        if (!value.isPresent()) {
            return defaultValue;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.orElseThrow());
        } catch (NumberFormatException exception) {
            throw new LLMConfigError(key + " must be an integer", exception);
        }
        if (parsed <= 0) {
            throw new LLMConfigError(key + " must be greater than zero");
        }
        if (parsed > maxValue) {
            throw new LLMConfigError(key + " must be less than or equal to " + maxValue);
        }
        return parsed;
    }

    /**
     * Parses the optional reasoning effort level.
     *
     * @param rawValue raw value of A2AT_LLM_REASONING_EFFORT; null or blank means the key is not set
     * @return the validated effort level, or null when the key is not set
     * @throws LLMConfigError when the value is not one of the supported levels; failing at config load time beats
     *     discovering a bad value through a provider 400 on the first LLM call
     */
    private static String parseReasoningEffort(String rawValue) {
        Optional<String> value = optional(rawValue);
        if (!value.isPresent()) {
            return null;
        }
        String effort = value.orElseThrow().toLowerCase(java.util.Locale.ROOT);
        if (!REASONING_EFFORT_VALUES.contains(effort)) {
            throw new LLMConfigError(
                    "A2AT_LLM_REASONING_EFFORT must be one of " + REASONING_EFFORT_VALUES + " but was " + effort);
        }
        return effort;
    }
}
