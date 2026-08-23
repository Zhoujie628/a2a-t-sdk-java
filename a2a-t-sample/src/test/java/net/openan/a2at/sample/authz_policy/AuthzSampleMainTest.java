package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioResult;
import net.openan.a2at.sdk.core.model.MetadataContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthzSampleMainTest {

    private static final AuthzExpected SUCCESS = new AuthzExpected("success", null, null, null);
    private static final ScenarioResult MATCH_RESULT =
            new ScenarioResult("success", true, null, List.of());
    private static final ScenarioResult MISMATCH_RESULT =
            new ScenarioResult("slot_validation_error", false, null, List.of());

    private PrintStream originalOut;
    private ByteArrayOutputStream outContent;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void should_returnZeroExitCode_WhenAllScenariosMatch() {
        assertEquals(0, AuthzSampleMain.exitCode(List.of(MATCH_RESULT, MATCH_RESULT, MATCH_RESULT)));
    }

    @Test
    void should_returnNonZeroExitCode_WhenAnyMismatch() {
        assertTrue(AuthzSampleMain.exitCode(List.of(MATCH_RESULT, MISMATCH_RESULT, MATCH_RESULT)) != 0);
    }

    @Test
    void should_resolveEnvPath_ArgsFirst() {
        Path result = AuthzSampleMain.resolveEnvPath(new String[] {"custom.env"});
        assertEquals(Path.of("custom.env"), result);
    }

    @Test
    void should_resolveEnvPath_WorkingDirFallback() throws IOException {
        Path cwdEnv = Path.of("authz.env");
        try {
            Files.writeString(cwdEnv, "A2AT_LLM_PROVIDER=openai\nA2AT_LLM_MODEL=gpt-4\nA2AT_LLM_API_KEY=sk-test\n");
            Path result = AuthzSampleMain.resolveEnvPath(new String[0]);
            assertEquals(cwdEnv.toAbsolutePath(), result.toAbsolutePath());
        } finally {
            Files.deleteIfExists(cwdEnv);
        }
    }

    @Test
    void should_resolveEnvPath_ClasspathFallback() {
        Path result = AuthzSampleMain.resolveEnvPath(new String[0]);
        assertTrue(
                result.toString().contains("authz-policy") && result.toString().endsWith("authz.env"));
    }

    @Test
    void should_hasRequiredLlmKeys_ReturnTrue_WhenAllKeysPresent(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, "A2AT_LLM_PROVIDER=openai\nA2AT_LLM_MODEL=gpt-4\nA2AT_LLM_API_KEY=sk-12345\n");
        assertTrue(AuthzSampleMain.hasRequiredLlmKeys(envFile));
    }

    @Test
    void should_hasRequiredLlmKeys_ReturnFalse_WhenKeyMissing(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, "A2AT_LLM_PROVIDER=openai\nA2AT_LLM_MODEL=gpt-4\n");
        assertFalse(AuthzSampleMain.hasRequiredLlmKeys(envFile));
    }

    @Test
    void should_hasRequiredLlmKeys_ReturnFalse_WhenKeyBlank(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, "A2AT_LLM_PROVIDER=openai\nA2AT_LLM_MODEL=gpt-4\nA2AT_LLM_API_KEY=\n");
        assertFalse(AuthzSampleMain.hasRequiredLlmKeys(envFile));
    }

    @Test
    void should_hasRequiredLlmKeys_ReturnFalse_WhenFileNotFound() {
        assertFalse(AuthzSampleMain.hasRequiredLlmKeys(Path.of("nonexistent.env")));
    }

    @Test
    void should_loadSlotSchemaMap_ReturnMapWithPropertiesAndRequired() {
        Map<String, Object> schema = AuthzSampleMain.loadSlotSchemaMap("zh-CN");
        assertNotNull(schema);
        assertTrue(schema.containsKey("properties"));
        assertTrue(schema.containsKey("required"));
        assertTrue(schema.containsKey("$schema"));
        assertTrue(schema.containsKey("type"));
        assertTrue(schema.containsKey("additionalProperties"));
    }

    @Test
    void should_printScenarioReport_WhenMetadataNull() {
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);
        ScenarioOutcome outcome = new ScenarioOutcome(MISMATCH_RESULT, null, null);
        AuthzSampleMain.printScenarioReport(scenario, outcome);
        assertTrue(outContent.toString().contains("<生成失败>"));
    }

    @Test
    void should_printScenarioReport_WhenFilledNull() {
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);
        MetadataContent metadata = new MetadataContent("template-uri", "prompt text", "extension-uri");
        ScenarioOutcome outcome = new ScenarioOutcome(MATCH_RESULT, metadata, null);
        AuthzSampleMain.printScenarioReport(scenario, outcome);
        assertTrue(outContent.toString().contains("<未提取参数>"));
    }

    @Test
    void should_printSummary() {
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);
        AuthzSampleMain.printSummary(List.of(scenario), List.of(MATCH_RESULT, MISMATCH_RESULT, MISMATCH_RESULT));
        String output = outContent.toString();
        assertTrue(output.contains("Match"));
        assertTrue(output.contains("Mismatch"));
    }

    @Test
    void should_writeReport_WithExpectedAndActualOutcomeAndParams(@TempDir Path tempDir) throws IOException {
        AuthzScenario scenario = new AuthzScenario(
                "test", "from_text", Map.of("text", "hello"),
                new AuthzExpected("success", Map.of("slot1", "expected_value"), null, null));
        MetadataContent metadata = new MetadataContent("template-uri", "prompt text", "extension-uri");
        ScenarioOutcome outcome =
                new ScenarioOutcome(MATCH_RESULT, metadata, new net.openan.a2at.sdk.core.model.FilledParamData(
                        Map.of("slot1", "actual_value")));

        Path reportPath = AuthzSampleMain.writeReport(List.of(scenario), List.of(outcome), tempDir);

        assertTrue(Files.exists(reportPath));
        String content = Files.readString(reportPath);
        assertTrue(content.contains("\"expected_outcome\""));
        assertTrue(content.contains("\"success\""));
        assertTrue(content.contains("\"actual_outcome\""));
        assertTrue(content.contains("\"match\""));
        assertTrue(content.contains("\"expected_params\""));
        assertTrue(content.contains("\"expected_value\""));
        assertTrue(content.contains("\"actual_params\""));
        assertTrue(content.contains("\"actual_value\""));
        assertTrue(content.contains("\"prompt_text\""));
        assertTrue(content.contains("\"prompt text\""));
        assertTrue(content.contains("\"expected_slot_errors\""));
        assertTrue(content.contains("\"actual_slot_errors\""));
    }

    @Test
    void should_writeReport_WithSlotErrors(@TempDir Path tempDir) throws IOException {
        AuthzScenario scenario = new AuthzScenario(
                "test", "from_text", Map.of("text", "hello"),
                new AuthzExpected(
                        "slot_validation_error",
                        null,
                        null,
                        List.of(new AuthzScenario.SlotErrorExpectation("授权策略的操作类型", "missing_required"))));
        ScenarioResult result = new ScenarioResult(
                "slot_validation_error",
                true,
                null,
                List.of(new net.openan.a2at.sdk.core.model.SlotValidationError(
                        "授权策略的操作类型", "missing_required", "Required slot is missing or empty")));
        ScenarioOutcome outcome = new ScenarioOutcome(result, null, null);

        Path reportPath = AuthzSampleMain.writeReport(List.of(scenario), List.of(outcome), tempDir);

        String content = Files.readString(reportPath);
        assertTrue(content.contains("授权策略的操作类型"));
        assertTrue(content.contains("missing_required"));
        assertTrue(content.contains("Required slot is missing or empty"));
    }
}
