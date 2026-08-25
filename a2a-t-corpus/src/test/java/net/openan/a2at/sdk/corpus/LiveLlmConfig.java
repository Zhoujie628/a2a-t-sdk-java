package net.openan.a2at.sdk.corpus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.model.A2ATConfigKeys;
import net.openan.a2at.sdk.core.model.LlmConfig;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMConfigError;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;

/**
 * The dedicated test-LLM configuration of the live corpus family (live design document §1.1), resolved once per process
 * and fully decoupled from the production {@code A2AT_LLM_*} variables: the live harness talks to a real
 * OpenAI-compatible endpoint (the customer's qwen3-27b-class model) while the offline corpus stays deterministic.
 *
 * <p>Resolution order per variable: the lowercase-dotted system property ({@code -Da2at.test.llm.base.url} and its
 * siblings, matching the repo's {@code case.filter} / {@code corpus.review.gate} property convention) takes precedence,
 * then the {@code A2AT_TEST_LLM_*} OS environment variable. A variable that is set but blank counts as absent. The three
 * required variables must all resolve — any one missing means the live family is not configured and every live test
 * skips through {@link #assumeConfigured()}; the two optional variables fall back to their documented defaults.
 *
 * <p>The values are kept as raw strings because their only destinations are string-keyed: the {@code .env} bridge of
 * {@link LiveLlmEnvWriter} and {@link LlmConfig#fromMap(Map)}. Numeric validation is delegated to
 * {@link LLMClientConfig#from(LlmConfig)} — the gate probe of the live design document reuses exactly that validation
 * semantics, so {@link #validationError()} reports what the production config layer would reject.
 *
 * @param baseUrl OpenAI-compatible endpoint base URL, such as a vLLM deployment of the test model
 * @param apiKey endpoint API key (a placeholder value is fine for a local deployment)
 * @param model model name to request
 * @param temperature sampling temperature, {@code "0"} unless overridden (stable output)
 * @param timeoutSeconds request timeout in seconds, {@code "60"} unless overridden
 * @since 2026-08
 */
public record LiveLlmConfig(
        String baseUrl, String apiKey, String model, String temperature, String timeoutSeconds) {

    /** Required test variable: OpenAI-compatible endpoint base URL. */
    static final String BASE_URL_VARIABLE = "A2AT_TEST_LLM_BASE_URL";

    /** Required test variable: endpoint API key. */
    static final String API_KEY_VARIABLE = "A2AT_TEST_LLM_API_KEY";

    /** Required test variable: model name. */
    static final String MODEL_VARIABLE = "A2AT_TEST_LLM_MODEL";

    /** Optional test variable: sampling temperature (default 0). */
    static final String TEMPERATURE_VARIABLE = "A2AT_TEST_LLM_TEMPERATURE";

    /** Optional test variable: request timeout in seconds (default 60). */
    static final String TIMEOUT_SECONDS_VARIABLE = "A2AT_TEST_LLM_TIMEOUT_SECONDS";

    /** System-property override of {@link #BASE_URL_VARIABLE}. */
    static final String BASE_URL_PROPERTY = "a2at.test.llm.base.url";

    /** System-property override of {@link #API_KEY_VARIABLE}. */
    static final String API_KEY_PROPERTY = "a2at.test.llm.api.key";

    /** System-property override of {@link #MODEL_VARIABLE}. */
    static final String MODEL_PROPERTY = "a2at.test.llm.model";

    /** System-property override of {@link #TEMPERATURE_VARIABLE}. */
    static final String TEMPERATURE_PROPERTY = "a2at.test.llm.temperature";

    /** System-property override of {@link #TIMEOUT_SECONDS_VARIABLE}. */
    static final String TIMEOUT_SECONDS_PROPERTY = "a2at.test.llm.timeout.seconds";

    /** Default of the optional temperature, mirroring the live design document §1.1. */
    static final String DEFAULT_TEMPERATURE = "0";

    /** Default of the optional timeout, mirroring the SDK default the live design document §1.1 keeps. */
    static final String DEFAULT_TIMEOUT_SECONDS = "60";

    /** The live family speaks the OpenAI-compatible protocol only (live design document §1.2). */
    static final String PROVIDER = "openai";

    public LiveLlmConfig {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(timeoutSeconds, "timeoutSeconds");
    }

    /**
     * Resolves the live test configuration from the system properties and the OS environment of the current process.
     *
     * @return the resolved configuration, or null when the family is not configured (any required variable missing)
     */
    public static @Nullable LiveLlmConfig fromCurrentProcess() {
        String baseUrl = resolve(BASE_URL_PROPERTY, BASE_URL_VARIABLE);
        String apiKey = resolve(API_KEY_PROPERTY, API_KEY_VARIABLE);
        String model = resolve(MODEL_PROPERTY, MODEL_VARIABLE);
        if (baseUrl == null || apiKey == null || model == null) {
            return null;
        }
        String temperature = resolveOrDefault(TEMPERATURE_PROPERTY, TEMPERATURE_VARIABLE, DEFAULT_TEMPERATURE);
        String timeoutSeconds =
                resolveOrDefault(TIMEOUT_SECONDS_PROPERTY, TIMEOUT_SECONDS_VARIABLE, DEFAULT_TIMEOUT_SECONDS);
        return new LiveLlmConfig(baseUrl, apiKey, model, temperature, timeoutSeconds);
    }

    /**
     * The skip gate of every live test (live design document §1.1): an unconfigured environment skips the whole family
     * through {@link Assumptions#assumeTrue(boolean, String)}, mirroring the opt-in gate precedent of
     * {@code CorpusContractTest}; a configured but invalid environment is a configuration failure, not a skip, so the
     * broken setup cannot hide behind a green build.
     */
    public static void assumeConfigured() {
        LiveLlmConfig config = fromCurrentProcess();
        Assumptions.assumeTrue(config != null, configurationHint());
        String error = config.validationError();
        if (error != null) {
            throw new IllegalStateException("live LLM configuration is invalid: " + error + " (" + configurationHint()
                    + ")");
        }
    }

    /**
     * Lists what to set for users hitting the skip: the three required variables in both spellings plus the two
     * optional ones with their defaults. Variables that resolve already are omitted, so a partial setup names exactly
     * the missing pieces.
     *
     * @return the configuration-method hint carried by the skip message
     */
    private static String configurationHint() {
        StringBuilder hint = new StringBuilder("live LLM validation is disabled; set ");
        appendVariable(hint, BASE_URL_PROPERTY, BASE_URL_VARIABLE);
        appendVariable(hint, API_KEY_PROPERTY, API_KEY_VARIABLE);
        appendVariable(hint, MODEL_PROPERTY, MODEL_VARIABLE);
        hint.append(" to enable it (optional: ")
                .append(TEMPERATURE_VARIABLE)
                .append(", default ")
                .append(DEFAULT_TEMPERATURE)
                .append("; ")
                .append(TIMEOUT_SECONDS_VARIABLE)
                .append(", default ")
                .append(DEFAULT_TIMEOUT_SECONDS)
                .append(")");
        return hint.toString();
    }

    private static void appendVariable(StringBuilder hint, String propertyKey, String envName) {
        if (resolve(propertyKey, envName) != null) {
            return;
        }
        hint.append("-D").append(propertyKey).append(" or ").append(envName).append(", ");
    }

    /**
     * Builds the unified LLM configuration the same way the {@code .env} loader builds it: raw string values through
     * {@link LlmConfig#fromMap(Map)}, so every default and parse-error semantics of the production path apply.
     *
     * @return the unified LLM configuration of this test setup
     */
    public LlmConfig toLlmConfig() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(A2ATConfigKeys.Llm.PROVIDER, PROVIDER);
        values.put(A2ATConfigKeys.Llm.MODEL, model);
        values.put(A2ATConfigKeys.Llm.API_KEY, apiKey);
        values.put(A2ATConfigKeys.Llm.BASE_URL, baseUrl);
        values.put(A2ATConfigKeys.Llm.TEMPERATURE, temperature);
        values.put(A2ATConfigKeys.Llm.TIMEOUT_SECONDS, timeoutSeconds);
        return LlmConfig.fromMap(values);
    }

    /**
     * Probes this configuration with the production validation semantics (live design document §1.1: a configuration
     * that can build a legal {@link LLMClientConfig} is enabled).
     *
     * @return the validation error message, or null when the configuration is legal
     */
    public @Nullable String validationError() {
        try {
            LLMClientConfig.from(toLlmConfig());
            return null;
        } catch (LLMConfigError error) {
            return error.getMessage();
        }
    }

    /**
     * Creates the real provider client of this configuration — {@code LLMClientFactory} instantiates the production
     * {@code OpenAIClient} for the {@code openai} provider, the exact client the facades would build from the same
     * configuration. Configuration problems surface as a readable configuration failure instead of the raw
     * {@link LLMConfigError}.
     *
     * @param config resolved live test configuration
     * @return the real OpenAI-compatible LLM client
     * @throws IllegalStateException when the configuration fails the production validation
     */
    public static LLMClient createLlmClient(LiveLlmConfig config) {
        try {
            return LLMClientFactory.create(PROVIDER, LLMClientConfig.from(config.toLlmConfig()));
        } catch (LLMConfigError error) {
            throw new IllegalStateException(
                    "live LLM configuration is invalid: " + error.getMessage()
                            + " (fix the A2AT_TEST_LLM_* variables or their -Da2at.test.llm.* overrides)",
                    error);
        }
    }

    private static @Nullable String resolve(String propertyKey, String envName) {
        String value = System.getProperty(propertyKey);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String resolveOrDefault(String propertyKey, String envName, String defaultValue) {
        String value = resolve(propertyKey, envName);
        return value == null ? defaultValue : value;
    }
}
