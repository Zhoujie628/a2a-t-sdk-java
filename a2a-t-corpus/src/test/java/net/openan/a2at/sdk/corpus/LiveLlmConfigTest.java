package net.openan.a2at.sdk.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.providers.OpenAIClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/**
 * Unit tests of the live-LLM configuration bridge (live design document §1.1): property-over-environment resolution,
 * the not-configured null, the optional defaults, and the production-validation probe.
 *
 * <p>The tests stay hermetic for {@code mvn test}: every case drives the resolution through system properties and
 * restores the values it found afterwards, so a developer's {@code -Da2at.test.llm.*} command-line setup survives the
 * run; the assertions that involve the OS environment are guarded by
 * {@link Assumptions#assumeTrue(boolean, String)} so they run only on a machine that exports the variable (the
 * developer machine that configured the live endpoint), never in CI.
 *
 * @since 2026-08
 */
class LiveLlmConfigTest {

    private static final List<String> ALL_PROPERTY_KEYS = List.of(
            LiveLlmConfig.BASE_URL_PROPERTY,
            LiveLlmConfig.API_KEY_PROPERTY,
            LiveLlmConfig.MODEL_PROPERTY,
            LiveLlmConfig.TEMPERATURE_PROPERTY,
            LiveLlmConfig.TIMEOUT_SECONDS_PROPERTY);

    /**
     * The property values the tests found before overriding them: the tests of one JVM may run after a developer
     * enabled the live family through {@code -Da2at.test.llm.*}, so the teardown restores the command-line values
     * instead of clearing the keys — otherwise this test class could silently switch the live suite off.
     */
    private final Map<String, String> savedProperties = new LinkedHashMap<>();

    @BeforeEach
    void saveProperties() {
        for (String key : ALL_PROPERTY_KEYS) {
            savedProperties.put(key, System.getProperty(key));
        }
    }

    @AfterEach
    void restoreProperties() {
        for (Map.Entry<String, String> saved : savedProperties.entrySet()) {
            if (saved.getValue() == null) {
                System.clearProperty(saved.getKey());
            } else {
                System.setProperty(saved.getKey(), saved.getValue());
            }
        }
    }

    @Test
    void absentWhenNoRequiredVariableIsConfigured() {
        assumeEnvironmentAbsent(
                LiveLlmConfig.BASE_URL_VARIABLE, LiveLlmConfig.API_KEY_VARIABLE, LiveLlmConfig.MODEL_VARIABLE);
        assertNull(LiveLlmConfig.fromCurrentProcess(), "no required variable resolves, so the family is not configured");
        assertThrows(TestAbortedException.class, LiveLlmConfig::assumeConfigured);
    }

    @Test
    void nullWhenOnlyPartiallyConfigured() {
        System.setProperty(LiveLlmConfig.MODEL_PROPERTY, "qwen3-27b");
        assumeEnvironmentAbsent(LiveLlmConfig.BASE_URL_VARIABLE, LiveLlmConfig.API_KEY_VARIABLE);
        assertNull(
                LiveLlmConfig.fromCurrentProcess(),
                "any missing required variable means the family is not configured (skip, not a half-configured run)");
    }

    @Test
    void blankValueCountsAsNotConfigured() {
        System.setProperty(LiveLlmConfig.BASE_URL_PROPERTY, "https://live.example.test/v1");
        System.setProperty(LiveLlmConfig.API_KEY_PROPERTY, "   ");
        System.setProperty(LiveLlmConfig.MODEL_PROPERTY, "qwen3-27b");
        assumeEnvironmentAbsent(LiveLlmConfig.API_KEY_VARIABLE);
        assertNull(LiveLlmConfig.fromCurrentProcess(), "a blank value is an unset value");
    }

    @Test
    void systemPropertyTakesPrecedenceOverEnvironmentVariable() {
        System.setProperty(LiveLlmConfig.BASE_URL_PROPERTY, "https://property.example.test/v1");
        System.setProperty(LiveLlmConfig.API_KEY_PROPERTY, "property-key");
        System.setProperty(LiveLlmConfig.MODEL_PROPERTY, "property-model");
        LiveLlmConfig config = LiveLlmConfig.fromCurrentProcess();
        assertNotNull(config, "the three required properties configure the family on their own");
        assertEquals("https://property.example.test/v1", config.baseUrl());
        assertEquals("property-key", config.apiKey());
        assertEquals("property-model", config.model());
    }

    @Test
    void optionalValuesFallBackToTheirDefaults() {
        System.setProperty(LiveLlmConfig.BASE_URL_PROPERTY, "https://live.example.test/v1");
        System.setProperty(LiveLlmConfig.API_KEY_PROPERTY, "live-key");
        System.setProperty(LiveLlmConfig.MODEL_PROPERTY, "qwen3-27b");
        assumeEnvironmentAbsent(LiveLlmConfig.TEMPERATURE_VARIABLE, LiveLlmConfig.TIMEOUT_SECONDS_VARIABLE);
        LiveLlmConfig config = LiveLlmConfig.fromCurrentProcess();
        assertNotNull(config);
        assertEquals(LiveLlmConfig.DEFAULT_TEMPERATURE, config.temperature(), "temperature defaults to 0");
        assertEquals(LiveLlmConfig.DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds(), "timeout defaults to 60 seconds");
    }

    @Test
    void environmentVariableIsUsedWhenNoPropertyIsSet() {
        Assumptions.assumeTrue(
                System.getenv(LiveLlmConfig.MODEL_VARIABLE) != null,
                "runs only on a machine that exports " + LiveLlmConfig.MODEL_VARIABLE);
        LiveLlmConfig config = LiveLlmConfig.fromCurrentProcess();
        assertNotNull(config, "the environment variables configure the family when no property intervenes");
        assertEquals(System.getenv(LiveLlmConfig.MODEL_VARIABLE).trim(), config.model());
    }

    @Test
    void validConfigurationBuildsTheRealOpenAiClient() {
        System.setProperty(LiveLlmConfig.BASE_URL_PROPERTY, "https://live.example.test/v1");
        System.setProperty(LiveLlmConfig.API_KEY_PROPERTY, "live-key");
        System.setProperty(LiveLlmConfig.MODEL_PROPERTY, "qwen3-27b");
        LiveLlmConfig config = LiveLlmConfig.fromCurrentProcess();
        assertNotNull(config);
        assertNull(config.validationError(), "the defaults pass the production validation");
        assertEquals("openai", config.toLlmConfig().provider());
        assertEquals("qwen3-27b", config.toLlmConfig().model());
        assertEquals(0.0, config.toLlmConfig().temperature());
        assertEquals(60.0, config.toLlmConfig().timeoutSeconds());
        assertInstanceOf(OpenAIClient.class, LiveLlmConfig.createLlmClient(config));
    }

    @Test
    void invalidOptionalValueSurfacesAsConfigurationFailure() {
        System.setProperty(LiveLlmConfig.BASE_URL_PROPERTY, "https://live.example.test/v1");
        System.setProperty(LiveLlmConfig.API_KEY_PROPERTY, "live-key");
        System.setProperty(LiveLlmConfig.MODEL_PROPERTY, "qwen3-27b");
        System.setProperty(LiveLlmConfig.TEMPERATURE_PROPERTY, "not-a-number");
        LiveLlmConfig config = LiveLlmConfig.fromCurrentProcess();
        assertNotNull(config);
        String error = config.validationError();
        assertNotNull(error, "the production validation rejects the non-numeric temperature");
        assertTrue(
                error.contains("A2AT_LLM_TEMPERATURE"),
                "the error names the offending variable: " + error);
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> LiveLlmConfig.createLlmClient(config));
        assertTrue(
                thrown.getMessage().contains("live LLM configuration is invalid"),
                "the client factory surfaces a configuration failure message: " + thrown.getMessage());
        assertThrows(IllegalStateException.class, LiveLlmConfig::assumeConfigured);
    }

    private static void assumeEnvironmentAbsent(String... variables) {
        for (String variable : variables) {
            Assumptions.assumeTrue(
                    System.getenv(variable) == null,
                    "runs only when " + variable + " is not exported by the environment");
        }
    }
}
