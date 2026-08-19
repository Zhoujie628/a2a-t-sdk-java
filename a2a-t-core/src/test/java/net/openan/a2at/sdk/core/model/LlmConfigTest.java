package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LlmConfig}.
 *
 * <p>Tests cover the following scenarios:
 *
 * <ul>
 *   <li>Default values when configuration keys are missing
 *   <li>Overriding defaults with environment variable values
 *   <li>Max attempts parsing: in-range values, clamping, fallback, and blank values
 * </ul>
 *
 * @since 2026-06
 */
class LlmConfigTest {

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} applies default values when no configuration keys are provided.
     *
     * <p>Scenario: An empty map is passed to fromMap(). Expected result: All fields use predefined defaults: -
     * provider: "openai" - model, apiKey, baseUrl: empty strings - historyWindow: 12 - maxTokens: 2048 - temperature:
     * 0.2 - timeoutSeconds: 30.0 - sessionMaxTotal: 300 - sessionMaxPerProvider: 100 - maxAttempts: 3
     */
    @Test
    void should_useDefaults_When_keysAreMissing() {
        Map<String, String> values = Map.of();

        LlmConfig config = LlmConfig.fromMap(values);

        assertEquals("openai", config.provider());
        assertEquals("", config.model());
        assertEquals("", config.apiKey());
        assertEquals("", config.baseUrl());
        assertEquals(12, config.historyWindow());
        assertEquals(2048, config.maxTokens());
        assertEquals(0.2d, config.temperature());
        assertEquals(30.0d, config.timeoutSeconds());
        assertEquals(300, config.sessionMaxTotal());
        assertEquals(100, config.sessionMaxPerProvider());
        assertEquals(3, config.maxAttempts());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} overrides default values with values from the provided map.
     *
     * <p>Scenario: A map containing all LLM configuration keys with custom values. Expected result: All fields use the
     * values from the map instead of defaults.
     */
    @Test
    void should_overrideDefaults_When_keysAreProvided() {
        Map<String, String> values = Map.ofEntries(
                Map.entry("A2AT_LLM_PROVIDER", "example_provider"),
                Map.entry("A2AT_LLM_MODEL", "example-model"),
                Map.entry("A2AT_LLM_API_KEY", "test-api-key"),
                Map.entry("A2AT_LLM_BASE_URL", "https://llm.example.test/v1"),
                Map.entry("A2AT_LLM_HISTORY_WINDOW", "20"),
                Map.entry("A2AT_LLM_MAX_TOKENS", "4096"),
                Map.entry("A2AT_LLM_TEMPERATURE", "0.5"),
                Map.entry("A2AT_LLM_TIMEOUT_SECONDS", "60"),
                Map.entry("A2AT_LLM_SESSION_MAX_TOTAL", "500"),
                Map.entry("A2AT_LLM_SESSION_MAX_PER_PROVIDER", "150"),
                Map.entry("A2AT_LLM_MAX_ATTEMPTS", "5"));

        LlmConfig config = LlmConfig.fromMap(values);

        assertEquals("example_provider", config.provider());
        assertEquals("example-model", config.model());
        assertEquals("test-api-key", config.apiKey());
        assertEquals("https://llm.example.test/v1", config.baseUrl());
        assertEquals(20, config.historyWindow());
        assertEquals(4096, config.maxTokens());
        assertEquals(0.5d, config.temperature());
        assertEquals(60.0d, config.timeoutSeconds());
        assertEquals(500, config.sessionMaxTotal());
        assertEquals(150, config.sessionMaxPerProvider());
        assertEquals(5, config.maxAttempts());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} clamps an out-of-range max attempts value to the nearest allowed
     * bound.
     *
     * <p>Scenario: A2AT_LLM_MAX_ATTEMPTS is set to "0" (below the lower bound of 1) and to "99" (above the upper bound
     * of 10). Expected result: maxAttempts resolves to 1 and to 10 respectively.
     */
    @Test
    void should_clampMaxAttempts_When_valueIsOutOfRange() {
        LlmConfig tooSmall = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "0"));
        LlmConfig tooLarge = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "99"));

        assertEquals(1, tooSmall.maxAttempts());
        assertEquals(10, tooLarge.maxAttempts());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} falls back to the default max attempts value when the configured
     * value is not a valid integer.
     *
     * <p>Scenario: A2AT_LLM_MAX_ATTEMPTS is set to a non-numeric value. Expected result: maxAttempts resolves to the
     * default of 3.
     */
    @Test
    void should_fallBackToDefaultMaxAttempts_When_valueIsUnparseable() {
        LlmConfig config = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "garbage"));

        assertEquals(3, config.maxAttempts());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} uses the default max attempts value when the key is absent or blank.
     *
     * <p>Scenario: A2AT_LLM_MAX_ATTEMPTS is missing from the map, or set to a blank value. Expected result: maxAttempts
     * resolves to the default of 3 in both cases.
     */
    @Test
    void should_useDefaultMaxAttempts_When_valueIsAbsentOrBlank() {
        LlmConfig absent = LlmConfig.fromMap(Map.of());
        LlmConfig blank = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", ""));

        assertEquals(3, absent.maxAttempts());
        assertEquals(3, blank.maxAttempts());
    }
}
