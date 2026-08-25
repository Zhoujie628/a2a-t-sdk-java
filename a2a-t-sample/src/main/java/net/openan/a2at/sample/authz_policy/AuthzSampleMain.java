package net.openan.a2at.sample.authz_policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.SlotErrorExpectation;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioResult;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * Entry point for the Authorization-T demo.
 *
 * <p>Loads scenarios from {@code sample/authz-policy/scenarios.json}, generates authorization prompts through
 * {@link A2ATClient}, validates them through {@link A2ATServer}, and writes a JSON report with
 * expected/actual outcome and per-slot error details for each scenario.
 *
 * @since 2026-08
 */
public final class AuthzSampleMain {

    private static final String DEFAULT_ENV_FILE = "authz.env";

    private static final String BUNDLED_ENV_FILE = Path.of(
                    "a2a-t-sample", "src", "main", "resources", "sample", "authz-policy", "authz.env")
            .toString();

    private static final String SCENARIOS_RESOURCE =
            System.getProperty("authz.scenarios", "sample/authz-policy/scenarios.json");

    private static final String SLOT_SCHEMA_PATH_TEMPLATE =
            "/prompt_resources/slots/Authorization-T/authorization-policy-management/v1/%s/slot.json";

    private static final List<String> REQUIRED_LLM_KEYS =
            List.of("A2AT_LLM_PROVIDER", "A2AT_LLM_MODEL", "A2AT_LLM_API_KEY");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuthzSampleMain() {}

    public static void main(String[] args) {
        Path envPath = resolveEnvPath(args);
        if (!hasRequiredLlmKeys(envPath)) {
            System.err.println("Required LLM keys not configured in env file: " + envPath);
            System.exit(1);
        }

        A2ATClient client = new A2ATClient(envPath);
        A2ATServer server = new A2ATServer(envPath);

        String language = A2ATConfig.load(envPath).prompt().language();
        Map<String, Object> slotSchemaMap = loadSlotSchemaMap(language);

        List<AuthzScenario> scenarios = AuthzScenarioLoader.load(SCENARIOS_RESOURCE);
        TemplateUri templateUri = StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT;

        AuthzPromptGenerator generator = scenario -> {
            if (AuthzScenario.FROM_TEXT.equals(scenario.entry())) {
                String text = (String) scenario.input().get("text");
                return client.generateAuthPromptFromText(text, templateUri);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) scenario.input().get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) scenario.input().get("schema");
            return client.generateAuthPromptFromDataWithSchema(data, schema, templateUri);
        };

        AuthzPromptValidator validator = server::validateAuthPromptAndDataFilling;
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);

        List<ScenarioResult> results = new ArrayList<>();
        List<ScenarioOutcome> outcomes = new ArrayList<>();
        for (AuthzScenario scenario : scenarios) {
            ScenarioOutcome outcome = runner.run(scenario, slotSchemaMap, templateUri);
            printScenarioReport(scenario, outcome);
            outcomes.add(outcome);
            results.add(outcome.result());
        }

        Path reportPath = writeReport(scenarios, outcomes, Path.of("a2a-t-sample", "target"));
        System.out.println("Report written to: " + reportPath.toAbsolutePath().normalize());
        System.out.println();
        printSummary(scenarios, results);
        System.exit(exitCode(results));
    }

    static Path resolveEnvPath(String[] args) {
        if (args.length > 0) {
            return Path.of(args[0]);
        }
        Path cwdEnv = Path.of(DEFAULT_ENV_FILE);
        if (Files.exists(cwdEnv) && hasRequiredLlmKeys(cwdEnv)) {
            return cwdEnv;
        }
        return Path.of(BUNDLED_ENV_FILE);
    }

    static boolean hasRequiredLlmKeys(Path envFile) {
        Map<String, String> entries = new HashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                entries.put(
                        trimmed.substring(0, separator).trim(),
                        trimmed.substring(separator + 1).trim());
            }
        } catch (IOException exception) {
            return false;
        }
        for (String key : REQUIRED_LLM_KEYS) {
            if (entries.getOrDefault(key, "").isBlank()) {
                return false;
            }
        }
        return true;
    }

    static Map<String, Object> loadSlotSchemaMap(String language) {
        String resourcePath = String.format(SLOT_SCHEMA_PATH_TEMPLATE, language);
        try (InputStream stream = AuthzSampleMain.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Slot schema resource not found: " + resourcePath);
            }
            return MAPPER.readValue(stream, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load slot schema: " + resourcePath, e);
        }
    }

    static void printScenarioReport(AuthzScenario scenario, ScenarioOutcome outcome) {
        System.out.println("--- Scenario: " + scenario.label() + " ---");
        System.out.println("  Entry: " + scenario.entry() + ", Expected Outcome: " + scenario.expected().outcome());
        MetadataContent metadata = outcome.metadata();
        if (metadata == null) {
            System.out.println("  Prompt: <生成失败>");
        } else {
            System.out.println("  Prompt: " + metadata.promptText());
            System.out.println("  TemplateUri: " + metadata.templateUri());
            System.out.println("  ExtensionUri: " + metadata.extensionUri());
        }
        System.out.println("  Actual Outcome: " + outcome.result().outcome());
        System.out.println("  Match: " + outcome.result().match());
        FilledParamData filled = outcome.filled();
        if (filled == null) {
            System.out.println("  Extracted Params: <未提取参数>");
        } else {
            System.out.println("  Extracted Params: " + filled.data());
        }
        if (!outcome.result().slotErrors().isEmpty()) {
            System.out.println("  Slot Errors: " + outcome.result().slotErrors());
        }
        if (outcome.result().error() != null) {
            System.out.println("  Error: [" + outcome.result().error().getCode() + "] "
                    + outcome.result().error().getMessage());
        }
        System.out.println();
    }

    static void printSummary(List<AuthzScenario> scenarios, List<ScenarioResult> results) {
        long matchCount = results.stream().filter(ScenarioResult::match).count();
        long mismatchCount = results.size() - matchCount;
        System.out.println("========== Summary ==========");
        System.out.println("  Total: " + results.size() + ", Match: " + matchCount + ", Mismatch: " + mismatchCount);
        System.out.println("  Exit Code: " + exitCode(results));
        System.out.println();
    }

    static int exitCode(List<ScenarioResult> results) {
        boolean allMatch = results.stream().allMatch(ScenarioResult::match);
        return allMatch ? 0 : 1;
    }

    static Path writeReport(List<AuthzScenario> scenarios, List<ScenarioOutcome> outcomes, Path reportDir) {
        Map<String, Object> report = new LinkedHashMap<>();
        long matchCount = outcomes.stream().filter(o -> o.result().match()).count();
        long mismatchCount = outcomes.size() - matchCount;
        report.put("summary", Map.of(
                "total", scenarios.size(),
                "match", matchCount,
                "mismatch", mismatchCount));
        List<Map<String, Object>> scenarioEntries = new ArrayList<>();
        for (int i = 0; i < scenarios.size(); i++) {
            AuthzScenario scenario = scenarios.get(i);
            ScenarioOutcome outcome = outcomes.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("label", scenario.label());
            entry.put("entry", scenario.entry());
            entry.put("expected_outcome", scenario.expected().outcome());
            entry.put("actual_outcome", outcome.result().outcome());
            entry.put("match", outcome.result().match());
            entry.put("expected_slot_errors", formatExpectedSlotErrors(scenario.expected().slotErrors()));
            entry.put("actual_slot_errors", formatActualSlotErrors(outcome.result().slotErrors()));
            entry.put("expected_prompt_text", scenario.expected().promptText());
            entry.put("prompt_text", outcome.metadata() != null ? outcome.metadata().promptText() : null);
            entry.put("expected_params", scenario.expected().filledParams());
            entry.put("actual_params", outcome.filled() != null ? outcome.filled().data() : null);
            entry.put("error", outcome.result().error() != null
                    ? Map.of(
                            "code", outcome.result().error().getCode(),
                            "message", outcome.result().error().getMessage())
                    : null);
            scenarioEntries.add(entry);
        }
        report.put("scenarios", scenarioEntries);
        try {
            Files.createDirectories(reportDir);
            Path reportFile = reportDir.resolve("authz-report.json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), report);
            return reportFile;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write report to: " + reportDir, e);
        }
    }

    private static List<Map<String, Object>> formatExpectedSlotErrors(List<SlotErrorExpectation> slotErrors) {
        if (slotErrors == null || slotErrors.isEmpty()) {
            return List.of();
        }
        return slotErrors.stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("slot_name", e.slotName());
                    m.put("code", e.code());
                    return m;
                })
                .toList();
    }

    private static List<Map<String, Object>> formatActualSlotErrors(List<SlotValidationError> slotErrors) {
        if (slotErrors == null || slotErrors.isEmpty()) {
            return List.of();
        }
        return slotErrors.stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("slot_name", e.slotName());
                    m.put("code", e.code());
                    m.put("message", e.message());
                    return m;
                })
                .toList();
    }
}
