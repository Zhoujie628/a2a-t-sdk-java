package net.openan.a2at.sdk.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of the live {@code .env} bridge (live design document §1.2): the written file carries the live entries
 * exactly — classpath prompt source with an empty override root, the real test-endpoint LLM values with the explicit
 * stability knobs, and the fixed retry limit — and one file is cached per distinct configuration, following the
 * {@code TaskApiAssembler} env-file precedent.
 *
 * @since 2026-08
 */
class LiveLlmEnvWriterTest {

    @Test
    void envFileCarriesTheLiveBridgeEntries() throws IOException {
        Path envFile = LiveLlmEnvWriter.envFileFor(
                new LiveLlmConfig("https://live.example.test/v1", "live-key", "qwen3-27b", "0", "60"));
        assertEquals(
                List.of(
                        "A2AT_LANGUAGE=zh-CN",
                        "A2AT_PROMPT_SOURCE_TYPE=classpath",
                        "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=",
                        "A2AT_LLM_PROVIDER=openai",
                        "A2AT_LLM_MODEL=qwen3-27b",
                        "A2AT_LLM_API_KEY=live-key",
                        "A2AT_LLM_BASE_URL=https://live.example.test/v1",
                        "A2AT_LLM_TEMPERATURE=0",
                        "A2AT_LLM_TIMEOUT_SECONDS=60",
                        "A2AT_LLM_MAX_ATTEMPTS=3",
                        "A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory"),
                Files.readAllLines(envFile, StandardCharsets.UTF_8));
    }

    @Test
    void overriddenOptionalValuesFlowIntoTheEnvFile() throws IOException {
        Path envFile = LiveLlmEnvWriter.envFileFor(
                new LiveLlmConfig("https://live.example.test/v1", "live-key", "qwen3-27b", "0.2", "120"));
        List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
        assertEquals("A2AT_LLM_TEMPERATURE=0.2", lines.get(7));
        assertEquals("A2AT_LLM_TIMEOUT_SECONDS=120", lines.get(8));
    }

    @Test
    void envFileIsCachedPerDistinctConfiguration() {
        LiveLlmConfig config = new LiveLlmConfig("https://live.example.test/v1", "live-key", "qwen3-27b", "0", "60");
        assertEquals(LiveLlmEnvWriter.envFileFor(config), LiveLlmEnvWriter.envFileFor(config));
        LiveLlmConfig otherTemperature =
                new LiveLlmConfig("https://live.example.test/v1", "live-key", "qwen3-27b", "0.2", "60");
        assertNotEquals(LiveLlmEnvWriter.envFileFor(config), LiveLlmEnvWriter.envFileFor(otherTemperature));
    }
}
