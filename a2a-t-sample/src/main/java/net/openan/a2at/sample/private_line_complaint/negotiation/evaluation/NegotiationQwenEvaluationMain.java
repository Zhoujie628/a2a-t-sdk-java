package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.InformationNegotiationSchemas;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleEnvironment;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleFlow;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATParamExtractionError;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * Runs the 100 manually labelled cases against a configured real LLM and writes a JSON report.
 *
 * <p>The automatic result measures generated-prompt to schema-extraction consistency. The report preserves each
 * prompt so a reviewer can perform the required semantic spot check without rerunning the model.
 */
public final class NegotiationQwenEvaluationMain {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private NegotiationQwenEvaluationMain() {
    }

    public static void main(String[] args) throws IOException {
        Path envPath = args.length > 0 ? Path.of(args[0]) : NegotiationSampleEnvironment.defaultEnvPath("client");
        Path reportPath = args.length > 1 ? Path.of(args[1]) : Path.of("a2a-t-sample", "target", "negotiation-qwen-report.json");
        Path processLogPath = args.length > 2 ? Path.of(args[2]) : defaultProcessLogPath(reportPath);
        List<NegotiationEvaluationCase> testCases = args.length > 3
                ? NegotiationEvaluationCaseLoader.loadSelected(parseCaseIds(args[3]))
                : NegotiationEvaluationCaseLoader.load();
        Map<String, String> environment = NegotiationSampleEnvironment.read(envPath);
        requireQwenConfiguration(environment);

        A2ATClient client = new A2ATClient(envPath);
        A2ATServer server = new A2ATServer(envPath);
        List<Map<String, Object>> results = new ArrayList<>();
        String runId = UUID.randomUUID().toString();
        try (NegotiationEvaluationProcessLogger processLogger = new NegotiationEvaluationProcessLogger(OBJECT_MAPPER, processLogPath)) {
            processLogger.write(runStartedEvent(runId, environment, envPath));
            for (NegotiationEvaluationCase testCase : testCases) {
                results.add(runCase(client, server, testCase, runId, processLogger));
            }
        }

        long passed = results.stream().filter(result -> Boolean.TRUE.equals(result.get("passed"))).count();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generated_at", Instant.now().toString());
        report.put("run_id", runId);
        report.put("model", environment.get("A2AT_LLM_MODEL"));
        report.put("base_url", environment.get("A2AT_LLM_BASE_URL"));
        report.put("git_revision", gitRevision());
        report.put("case_ids", testCases.stream().map(NegotiationEvaluationCase::id).toList());
        report.put("total", results.size());
        report.put("passed", passed);
        report.put("automatic_consistency_rate", (double) passed / results.size());
        report.put("note", "Automatic consistency is not semantic accuracy; manually review the preserved prompts, especially failures and a representative sample of passes.");
        report.put("process_log", processLogPath.toAbsolutePath().toString());
        report.put("cases", results);
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        OBJECT_MAPPER.writeValue(reportPath.toFile(), report);
        System.out.printf(
                "Qwen evaluation complete: %d/%d passed; report=%s; process-log=%s%n",
                passed, results.size(), reportPath.toAbsolutePath(), processLogPath.toAbsolutePath());
    }

    private static Map<String, Object> runCase(
            A2ATClient client,
            A2ATServer server,
            NegotiationEvaluationCase testCase,
            String runId,
            NegotiationEvaluationProcessLogger processLogger) throws IOException {
        long startedAt = System.nanoTime();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", testCase.id());
        result.put("phase", testCase.phase());
        result.put("category", testCase.category());
        result.put("input", testCase.text());
        result.put("expected", testCase.expected());
        NegotiationContext context = new NegotiationContext(UUID.randomUUID().toString(), 1, 3);
        try {
            MetadataContent content = generateWithLog(client, testCase, context, runId, processLogger);
            FilledParamData filled = validateWithLog(server, testCase, content.promptText(), runId, processLogger);
            Map<String, Object> actual = filled.data();
            result.put("prompt", content.promptText());
            result.put("template_uri", content.templateUri());
            result.put("extension_uri", content.extensionUri());
            result.put("actual", actual);
            result.put("passed", expectedValuesMatch(testCase.expected(), actual) && contextMatches(context, actual));
        } catch (RuntimeException exception) {
            result.put("passed", false);
            result.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        result.put("elapsed_ms", (System.nanoTime() - startedAt) / 1_000_000);
        return result;
    }

    private static MetadataContent generateWithLog(
            A2ATClient client,
            NegotiationEvaluationCase testCase,
            NegotiationContext context,
            String runId,
            NegotiationEvaluationProcessLogger processLogger) throws IOException {
        long startedAt = System.nanoTime();
        try {
            MetadataContent content = generate(client, testCase, context);
            processLogger.write(stageEvent(runId, testCase, "generate", context, Map.of(
                    "text", testCase.text(),
                    "template_uri", templateUri(testCase)), Map.of(
                    "prompt", content.promptText(),
                    "template_uri", content.templateUri(),
                    "extension_uri", content.extensionUri()), startedAt, null));
            return content;
        } catch (RuntimeException exception) {
            processLogger.write(stageEvent(runId, testCase, "generate", context, Map.of(
                    "text", testCase.text(),
                    "template_uri", templateUri(testCase)), null, startedAt, exception));
            throw exception;
        }
    }

    private static FilledParamData validateWithLog(
            A2ATServer server,
            NegotiationEvaluationCase testCase,
            String prompt,
            String runId,
            NegotiationEvaluationProcessLogger processLogger) throws IOException {
        long startedAt = System.nanoTime();
        Map<String, Object> schema = schema(testCase);
        try {
            FilledParamData filled = validate(server, testCase, prompt);
            processLogger.write(stageEvent(runId, testCase, "validate_and_fill", null, Map.of(
                    "prompt", prompt,
                    "schema", schema,
                    "template_uri", templateUri(testCase)), Map.of("filled_data", filled.data()), startedAt, null));
            return filled;
        } catch (RuntimeException exception) {
            processLogger.write(stageEvent(runId, testCase, "validate_and_fill", null, Map.of(
                    "prompt", prompt,
                    "schema", schema,
                    "template_uri", templateUri(testCase)), null, startedAt, exception));
            throw exception;
        }
    }

    private static Map<String, Object> stageEvent(
            String runId,
            NegotiationEvaluationCase testCase,
            String stage,
            NegotiationContext context,
            Map<String, Object> request,
            Map<String, Object> response,
            long startedAt,
            RuntimeException exception) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("run_id", runId);
        event.put("case_id", testCase.id());
        event.put("phase", testCase.phase());
        event.put("stage", stage);
        event.put("expected", testCase.expected());
        if (context != null) {
            event.put("context", Map.of("id", context.id(), "round", context.round(), "max_rounds", context.maxRounds()));
        }
        event.put("request", request);
        if (response != null) {
            event.put("response", response);
        }
        event.put("elapsed_ms", (System.nanoTime() - startedAt) / 1_000_000);
        event.put("outcome", exception == null ? "success" : "failure");
        if (exception != null) {
            event.put("error", errorDetails(exception));
        }
        return event;
    }

    private static Map<String, Object> runStartedEvent(String runId, Map<String, String> environment, Path envPath) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("run_id", runId);
        event.put("stage", "run_started");
        event.put("env_path", envPath.toAbsolutePath().toString());
        event.put("model", environment.get("A2AT_LLM_MODEL"));
        event.put("base_url", environment.get("A2AT_LLM_BASE_URL"));
        return event;
    }

    private static Map<String, Object> errorDetails(RuntimeException exception) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("class", exception.getClass().getName());
        error.put("message", exception.getMessage());
        if (exception instanceof A2ATError a2atError) {
            error.put("code", a2atError.getCode());
        }
        if (exception instanceof A2ATParamExtractionError extractionError) {
            error.put("slot_errors", extractionError.getErrors());
        }
        error.put("cause_chain", causeChain(exception));
        error.put("stack_trace", Arrays.stream(exception.getStackTrace()).limit(20).map(StackTraceElement::toString).toList());
        return error;
    }

    private static List<String> causeChain(Throwable exception) {
        List<String> causes = new ArrayList<>();
        Throwable current = exception;
        while (current != null) {
            causes.add(current.getClass().getName() + ": " + current.getMessage());
            current = current.getCause();
        }
        return causes;
    }

    private static List<String> parseCaseIds(String argument) {
        List<String> caseIds = Arrays.stream(argument.split(","))
                .map(String::trim)
                .filter(caseId -> !caseId.isEmpty())
                .toList();
        if (caseIds.isEmpty()) {
            throw new IllegalArgumentException("The fourth argument must contain at least one comma-separated case ID");
        }
        return caseIds;
    }

    private static String gitRevision() {
        String configuredRevision = System.getProperty("a2at.sample.gitRevision");
        if (configuredRevision != null && !configuredRevision.isBlank()) {
            return configuredRevision;
        }
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            String revision;
            try (var input = process.getInputStream()) {
                revision = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            return process.waitFor() == 0 && !revision.isBlank() ? revision : "unknown";
        } catch (IOException exception) {
            return "unknown";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }

    private static Path defaultProcessLogPath(Path reportPath) {
        String filename = reportPath.getFileName().toString();
        int extensionStart = filename.lastIndexOf('.');
        String logFilename = (extensionStart < 0 ? filename : filename.substring(0, extensionStart)) + "-process.jsonl";
        Path parent = reportPath.getParent();
        return parent == null ? Path.of(logFilename) : parent.resolve(logFilename);
    }

    private static String templateUri(NegotiationEvaluationCase testCase) {
        return testCase.phase().equals("propose")
                ? NegotiationSampleFlow.PROPOSE_TEMPLATE_URI.uri()
                : NegotiationSampleFlow.ENDING_TEMPLATE_URI.uri();
    }

    private static Map<String, Object> schema(NegotiationEvaluationCase testCase) {
        return switch (testCase.phase()) {
            case "propose" -> InformationNegotiationSchemas.propose();
            case "accept" -> InformationNegotiationSchemas.accept();
            case "reject" -> InformationNegotiationSchemas.reject();
            default -> throw new IllegalArgumentException("Unsupported evaluation phase: " + testCase.phase());
        };
    }

    private static MetadataContent generate(A2ATClient client, NegotiationEvaluationCase testCase, NegotiationContext context) {
        return switch (testCase.phase()) {
            case "propose" -> client.generateNegotiationProposePromptFromText(
                    testCase.text(), context, NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
            case "accept" -> client.generateNegotiationAcceptPromptFromText(
                    testCase.text(), context, NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            case "reject" -> client.generateNegotiationRejectPromptFromText(
                    testCase.text(), context, NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            default -> throw new IllegalArgumentException("Unsupported evaluation phase: " + testCase.phase());
        };
    }

    private static FilledParamData validate(A2ATServer server, NegotiationEvaluationCase testCase, String prompt) {
        return switch (testCase.phase()) {
            case "propose" -> server.validateAndFillingProposeData(
                    prompt, InformationNegotiationSchemas.propose(), NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
            case "accept" -> server.validateAndFillingAcceptData(
                    prompt, InformationNegotiationSchemas.accept(), NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            case "reject" -> server.validateAndFillingRejectData(
                    prompt, InformationNegotiationSchemas.reject(), NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            default -> throw new IllegalArgumentException("Unsupported evaluation phase: " + testCase.phase());
        };
    }

    private static boolean expectedValuesMatch(Map<String, Object> expected, Map<String, Object> actual) {
        return expected.entrySet().stream().allMatch(entry -> entry.getValue().equals(actual.get(entry.getKey())));
    }

    private static boolean contextMatches(NegotiationContext context, Map<String, Object> actual) {
        return context.id().equals(actual.get("id"))
                && numberEquals(context.round(), actual.get("round"))
                && numberEquals(context.maxRounds(), actual.get("maxRounds"));
    }

    private static boolean numberEquals(int expected, Object actual) {
        return actual instanceof Number number && expected == number.intValue();
    }

    private static void requireQwenConfiguration(Map<String, String> environment) {
        if (!"openai".equals(environment.get("A2AT_LLM_PROVIDER")) || environment.get("A2AT_LLM_BASE_URL") == null) {
            throw new IllegalArgumentException("Qwen evaluation requires A2AT_LLM_PROVIDER=openai and A2AT_LLM_BASE_URL in the supplied env file");
        }
    }
}
