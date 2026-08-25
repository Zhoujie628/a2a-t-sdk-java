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
 * <p>Loads scenarios from the resource configured via {@code authz.scenarios} (default:
 * {@code sample/authz-policy/scenarios.json}), generates authorization prompts through
 * {@link A2ATClient}, validates them through {@link A2ATServer} against the business-level parameter
 * schema ({@code sample/authz-policy/param-schema.json}), and writes a JSON report with staged
 * client/server expectations, actual behaviour and per-assertion match details.
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

    private static final String PARAM_SCHEMA_RESOURCE = "sample/authz-policy/param-schema.json";

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

        Map<String, Object> paramSchema = loadParamSchema();

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
            ScenarioOutcome outcome = runner.run(scenario, paramSchema, templateUri);
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

    static Map<String, Object> loadParamSchema() {
        try (InputStream stream = AuthzSampleMain.class.getClassLoader().getResourceAsStream(PARAM_SCHEMA_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Param schema resource not found: " + PARAM_SCHEMA_RESOURCE);
            }
            return MAPPER.readValue(stream, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load param schema: " + PARAM_SCHEMA_RESOURCE, e);
        }
    }

    static void printScenarioReport(AuthzScenario scenario, ScenarioOutcome outcome) {
        System.out.println("--- Scenario: " + scenario.label() + " ---");
        System.out.println("  Entry: " + scenario.entry());
        System.out.println("[Client]");
        AuthzExpected expected = scenario.expected();
        if (outcome.metadata() == null) {
            System.out.println("  生成结果: " + outcome.result().outcome());
            System.out.println("  错误明细: " + outcome.result().slotErrors());
        } else {
            System.out.println("  生成结果: generated");
            System.out.println("  Prompt:");
            System.out.println(indent(outcome.metadata().promptText(), "    "));
        }
        System.out.println("[Server]");
        if (outcome.metadata() == null) {
            System.out.println("  (跳过 - 客户端生成失败)");
        } else if (outcome.filled() == null) {
            System.out.println("  校验结果: " + outcome.result().outcome());
            System.out.println("  校验明细: " + outcome.result().slotErrors());
        } else {
            System.out.println("  校验结果: success");
            System.out.println("  校验明细: []");
            System.out.println("  提参结果: " + outcome.filled().data());
        }
        System.out.println("[判定] " + verdictLine(outcome.result()));
        System.out.println();
    }

    static String verdictLine(ScenarioResult result) {
        StringBuilder sb = new StringBuilder("match=" + result.match());
        if (result.clientPromptMatch() != null) {
            sb.append(" (client: prompt=").append(result.clientPromptMatch() ? "OK" : "FAIL");
            if (result.serverOutcomeMatch() != null) {
                sb.append(" | server: outcome=").append(result.serverOutcomeMatch() ? "OK" : "FAIL");
                sb.append(" slot_errors=").append(result.serverOutcomeMatch() ? "OK" : "FAIL");
            }
            if (result.serverParamsMatch() != null) {
                sb.append(" params=").append(result.serverParamsMatch() ? "OK" : "FAIL");
            }
            sb.append(")");
        } else {
            sb.append(" (client: outcome=").append(result.match() ? "OK" : "FAIL").append(")");
        }
        return sb.toString();
    }

    static String indent(String text, String prefix) {
        return text.lines().map(line -> prefix + line).reduce((a, b) -> a + "\n" + b).orElse("");
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
            entry.put("expected_client", formatClientExpectation(scenario));
            entry.put("expected_server", formatServerExpectation(scenario));
            entry.put("actual_outcome", outcome.result().outcome());
            entry.put("match", outcome.result().match());
            entry.put("actual_slot_errors", formatActualSlotErrors(outcome.result().slotErrors()));
            entry.put("prompt_text", outcome.metadata() != null ? outcome.metadata().promptText() : null);
            entry.put("actual_params", outcome.filled() != null ? outcome.filled().data() : null);
            Map<String, Object> assertions = new LinkedHashMap<>();
            assertions.put("client_prompt", outcome.result().clientPromptMatch());
            assertions.put("server_outcome", outcome.result().serverOutcomeMatch());
            assertions.put("server_params", outcome.result().serverParamsMatch());
            entry.put("assertions", assertions);
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

    private static Map<String, Object> formatClientExpectation(AuthzScenario scenario) {
        Map<String, Object> m = new LinkedHashMap<>();
        var client = scenario.expected().client();
        m.put("outcome", client.outcome());
        m.put("prompt_text", client.promptText());
        m.put("slot_errors", formatExpectedSlotErrors(client.slotErrors()));
        return m;
    }

    private static Map<String, Object> formatServerExpectation(AuthzScenario scenario) {
        var server = scenario.expected().server();
        if (server == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", server.outcome());
        m.put("slot_errors", formatExpectedSlotErrors(server.slotErrors()));
        m.put("params", server.params());
        return m;
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
