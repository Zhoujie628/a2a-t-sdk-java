package net.openan.a2at.sdk.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes the temporary {@code .env} file that bridges the dedicated test variables into the SDK configuration (live
 * design document §1.2): the SDK config only accepts a caller-provided {@code .env} file, so the live harness resolves
 * {@link LiveLlmConfig} and materializes it exactly once per distinct configuration into a temporary directory,
 * following the {@code TaskApiAssembler} env-file precedent.
 *
 * <p>Entry-by-entry rationale: the prompt resources come from the classpath jar with an empty local override root
 * ([R2]); the LLM entries carry the real test-endpoint values with an explicit temperature, timeout and retry limit
 * ([R7] — all three default to null in {@code LlmConfig}, and an unset temperature would let the server-side default
 * decide, which is unstable); language and state store mirror the minimal facade env.
 *
 * @since 2026-08
 */
final class LiveLlmEnvWriter {

    private static final Map<String, Path> ENV_FILES = new ConcurrentHashMap<>();

    /** Fixed retry limit of the live runs ([R6]: not per-record, it comes from the env bridge). */
    private static final String MAX_ATTEMPTS = "3";

    private LiveLlmEnvWriter() {}

    /**
     * Writes (once per distinct configuration) the live {@code .env} file the live harness hands to the SDK config
     * loading, mirroring the facade-test minimal-env precedent.
     *
     * @param config resolved live test configuration
     * @return path of the written {@code .env} file
     */
    static Path envFileFor(LiveLlmConfig config) {
        return ENV_FILES.computeIfAbsent(cacheKey(config), key -> writeEnvFile(config));
    }

    private static Path writeEnvFile(LiveLlmConfig config) {
        try {
            Path envFile = Files.createTempDirectory("a2at-corpus-live-env").resolve("live.env");
            Files.writeString(
                    envFile,
                    ("A2AT_LANGUAGE=zh-CN%n"
                            + "A2AT_PROMPT_SOURCE_TYPE=classpath%n"
                            + "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=%n"
                            + "A2AT_LLM_PROVIDER=openai%n"
                            + "A2AT_LLM_MODEL=%s%n"
                            + "A2AT_LLM_API_KEY=%s%n"
                            + "A2AT_LLM_BASE_URL=%s%n"
                            + "A2AT_LLM_TEMPERATURE=%s%n"
                            + "A2AT_LLM_TIMEOUT_SECONDS=%s%n"
                            + "A2AT_LLM_MAX_ATTEMPTS=%s%n"
                            + "A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory%n")
                            .formatted(
                                    config.model(),
                                    config.apiKey(),
                                    config.baseUrl(),
                                    config.temperature(),
                                    config.timeoutSeconds(),
                                    MAX_ATTEMPTS),
                    StandardCharsets.UTF_8);
            return envFile;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write the live corpus .env", exception);
        }
    }

    private static String cacheKey(LiveLlmConfig config) {
        return String.join(
                "\n", config.baseUrl(), config.apiKey(), config.model(), config.temperature(), config.timeoutSeconds());
    }
}
