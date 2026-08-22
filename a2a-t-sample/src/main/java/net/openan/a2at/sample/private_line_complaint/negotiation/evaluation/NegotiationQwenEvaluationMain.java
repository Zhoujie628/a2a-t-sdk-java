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
 * Runs a selected manually labelled case set against a configured real LLM and writes a JSON report.
 *
 * <p>Each case runs a complete negotiation path: propose generation and validation, a manual client
 * supplement, then accept/reject generation and validation. Generated prompts are passed directly to
 * their matching validation APIs.
 */
public final class NegotiationQwenEvaluationMain {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private NegotiationQwenEvaluationMain() {
    }

    public static void main(String[] args) throws IOException {
        Path envPath = args.length > 0 ? Path.of(args[0]) : NegotiationSampleEnvironment.defaultEnvPath("client");
        Path reportPath = args.length > 1 ? Path.of(args[1]) : Path.of("a2a-t-sample", "target", "negotiation-qwen-report.json");
        Path processLogPath = args.length > 2 ? Path.of(args[2]) : defaultProcessLogPath(reportPath);
        String caseSet = args.length > 3 ? args[3].trim() : "full";
        List<NegotiationEvaluationFlowCase> testCases = loadCases(caseSet);
        Map<String, String> environment = NegotiationSampleEnvironment.read(envPath);
        requireQwenConfiguration(environment);

        A2ATClient client = new A2ATClient(envPath);
        A2ATServer server = new A2ATServer(envPath);
        List<Map<String, Object>> results = new ArrayList<>();
        String runId = UUID.randomUUID().toString();
        try (NegotiationEvaluationProcessLogger processLogger = new NegotiationEvaluationProcessLogger(OBJECT_MAPPER, processLogPath)) {
            processLogger.write(runStartedEvent(runId, environment, envPath));
            for (NegotiationEvaluationFlowCase testCase : testCases) {
                results.add(runCase(client, server, testCase, runId, processLogger));
            }
        }

        long passed = results.stream().filter(result -> Boolean.TRUE.equals(result.get("passed"))).count();
        long proposeSucceeded = results.stream()
                .filter(result -> Boolean.TRUE.equals(result.get("propose_succeeded")))
                .count();
        long endingSucceeded = results.stream()
                .filter(result -> Boolean.TRUE.equals(result.get("ending_succeeded")))
                .count();
        long goldenMatched = results.stream()
                .filter(result -> Boolean.TRUE.equals(result.get("golden_matched")))
                .count();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generated_at", Instant.now().toString());
        report.put("run_id", runId);
        report.put("model", environment.get("A2AT_LLM_MODEL"));
        report.put("base_url", environment.get("A2AT_LLM_BASE_URL"));
        report.put("git_revision", gitRevision());
        report.put("case_set", caseSet);
        report.put("case_ids", testCases.stream().map(NegotiationEvaluationFlowCase::id).toList());
        report.put("total", results.size());
        report.put("propose_succeeded", proposeSucceeded);
        report.put("ending_succeeded", endingSucceeded);
        report.put("passed", passed);
        report.put("end_to_end_success_rate", (double) passed / results.size());
        report.put("golden_matched", goldenMatched);
        report.put("golden_exact_match_rate", (double) goldenMatched / results.size());
        report.put("note", "A flow passes when all four generation/validation API calls return successfully. Golden exact matching is an auxiliary diagnostic because semantically equivalent natural-language values may differ in wording.");
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
            NegotiationEvaluationFlowCase testCase,
            String runId,
            NegotiationEvaluationProcessLogger processLogger) throws IOException {
        long startedAt = System.nanoTime();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", testCase.id());
        result.put("decision", testCase.decision());
        result.put("category", testCase.category());
        result.put("propose_case_id", testCase.proposeCase().id());
        result.put("ending_case_id", testCase.endingCase().id());
        result.put("propose_input", testCase.proposeCase().text());
        result.put("expected_propose", testCase.expectedPropose());
        result.put("expected_ending", testCase.expectedEnding());
        NegotiationContext context = new NegotiationContext(UUID.randomUUID().toString(), 1, 3);
        result.put("client_supplement", testCase.clientSupplement(context.id(), context.round(), context.maxRounds()));
        result.put("ending_input", testCase.endingGenerationText(context.id(), context.round(), context.maxRounds()));
        result.put("actual_propose", null);
        result.put("actual_ending", null);
        result.put("propose_generation_succeeded", false);
        result.put("propose_validation_succeeded", false);
        result.put("ending_generation_succeeded", false);
        result.put("ending_validation_succeeded", false);
        result.put("propose_succeeded", false);
        result.put("ending_succeeded", false);
        result.put("golden_matched", false);
        List<Map<String, Object>> apiTrace = new ArrayList<>();
        result.put("api_trace", apiTrace);
        try {
            MetadataContent propose = generateProposeWithLog(
                    client, testCase, context, runId, processLogger, apiTrace);
            result.put("propose_generation_succeeded", true);
            result.put("generated_propose_prompt", propose.promptText());
            FilledParamData proposeFilled = validateProposeWithLog(
                    server, testCase, propose.promptText(), runId, processLogger, apiTrace);
            result.put("propose_validation_succeeded", true);
            result.put("actual_propose", proposeFilled.data());
            boolean proposeMatched = expectedValuesMatch(testCase.expectedPropose(), proposeFilled.data());
            result.put("propose_expected_matched", proposeMatched);
            result.put("propose_context_matched", contextMatches(context, proposeFilled.data()));
            result.put("propose_succeeded", true);

            NegotiationContext responseContext = NegotiationSampleFlow.contextFrom(proposeFilled.data());
            MetadataContent ending = generateEndingWithLog(
                    server, testCase, responseContext, runId, processLogger, apiTrace);
            result.put("ending_generation_succeeded", true);
            result.put("generated_ending_prompt", ending.promptText());
            FilledParamData endingFilled = validateEndingWithLog(
                    client, testCase, ending.promptText(), runId, processLogger, apiTrace);
            result.put("ending_validation_succeeded", true);
            result.put("actual_ending", endingFilled.data());
            boolean endingMatched = expectedValuesMatch(testCase.expectedEnding(), endingFilled.data());
            result.put("ending_expected_matched", endingMatched);
            result.put("ending_context_matched", contextMatches(responseContext, endingFilled.data()));
            result.put("ending_succeeded", true);
            result.put("golden_matched", proposeMatched && endingMatched);
            result.put("passed", true);
        } catch (RuntimeException exception) {
            result.put("passed", false);
            result.put("error", errorDetails(exception));
        }
        result.put("elapsed_ms", (System.nanoTime() - startedAt) / 1_000_000);
        return result;
    }

    private static MetadataContent generateProposeWithLog(
            A2ATClient client,
            NegotiationEvaluationFlowCase testCase,
            NegotiationContext context,
            String runId,
            NegotiationEvaluationProcessLogger processLogger,
            List<Map<String, Object>> apiTrace) throws IOException {
        long startedAt = System.nanoTime();
        try {
            MetadataContent content = client.generateNegotiationProposePromptFromText(
                    testCase.proposeCase().text(), context, NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "generate_propose", "propose", context, Map.of(
                    "text", testCase.proposeCase().text(),
                    "template_uri", NegotiationSampleFlow.PROPOSE_TEMPLATE_URI.uri()), Map.of(
                    "prompt", content.promptText(),
                    "template_uri", content.templateUri(),
                    "extension_uri", content.extensionUri()), startedAt, null));
            return content;
        } catch (RuntimeException exception) {
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "generate_propose", "propose", context, Map.of(
                    "text", testCase.proposeCase().text(),
                    "template_uri", NegotiationSampleFlow.PROPOSE_TEMPLATE_URI.uri()), null, startedAt, exception));
            throw exception;
        }
    }

    private static FilledParamData validateProposeWithLog(
            A2ATServer server,
            NegotiationEvaluationFlowCase testCase,
            String prompt,
            String runId,
            NegotiationEvaluationProcessLogger processLogger,
            List<Map<String, Object>> apiTrace) throws IOException {
        long startedAt = System.nanoTime();
        Map<String, Object> schema = InformationNegotiationSchemas.propose();
        try {
            FilledParamData filled = server.validateProposePromptAndDataFilling(
                    prompt, schema, NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "validate_propose_and_fill", "propose", null, Map.of(
                    "prompt", prompt,
                    "schema", schema,
                    "template_uri", NegotiationSampleFlow.PROPOSE_TEMPLATE_URI.uri()), Map.of("filled_data", filled.data()), startedAt, null));
            return filled;
        } catch (RuntimeException exception) {
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "validate_propose_and_fill", "propose", null, Map.of(
                    "prompt", prompt,
                    "schema", schema,
                    "template_uri", NegotiationSampleFlow.PROPOSE_TEMPLATE_URI.uri()), null, startedAt, exception));
            throw exception;
        }
    }

    private static MetadataContent generateEndingWithLog(
            A2ATServer server,
            NegotiationEvaluationFlowCase testCase,
            NegotiationContext context,
            String runId,
            NegotiationEvaluationProcessLogger processLogger,
            List<Map<String, Object>> apiTrace) throws IOException {
        long startedAt = System.nanoTime();
        try {
            String endingInput = testCase.endingGenerationText(context.id(), context.round(), context.maxRounds());
            MetadataContent content = "accept".equals(testCase.decision())
                    ? server.generateNegotiationAcceptPromptFromText(
                            endingInput, context, NegotiationSampleFlow.ENDING_TEMPLATE_URI)
                    : server.generateNegotiationRejectPromptFromText(
                            endingInput, context, NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "generate_" + testCase.decision(), testCase.decision(), context,
                    Map.of("text", endingInput,
                            "client_supplement", testCase.clientSupplement(context.id(), context.round(), context.maxRounds()),
                            "template_uri", NegotiationSampleFlow.ENDING_TEMPLATE_URI.uri()),
                    Map.of("prompt", content.promptText(), "template_uri", content.templateUri(),
                            "extension_uri", content.extensionUri()), startedAt, null));
            return content;
        } catch (RuntimeException exception) {
            String endingInput = testCase.endingGenerationText(context.id(), context.round(), context.maxRounds());
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "generate_" + testCase.decision(), testCase.decision(), context,
                    Map.of("text", endingInput,
                            "client_supplement", testCase.clientSupplement(context.id(), context.round(), context.maxRounds()),
                            "template_uri", NegotiationSampleFlow.ENDING_TEMPLATE_URI.uri()), null, startedAt, exception));
            throw exception;
        }
    }

    private static FilledParamData validateEndingWithLog(
            A2ATClient client,
            NegotiationEvaluationFlowCase testCase,
            String prompt,
            String runId,
            NegotiationEvaluationProcessLogger processLogger,
            List<Map<String, Object>> apiTrace) throws IOException {
        long startedAt = System.nanoTime();
        Map<String, Object> schema = "accept".equals(testCase.decision())
                ? InformationNegotiationSchemas.accept()
                : InformationNegotiationSchemas.reject();
        try {
            FilledParamData filled = "accept".equals(testCase.decision())
                    ? client.validateAcceptPromptAndDataFilling(prompt, schema, NegotiationSampleFlow.ENDING_TEMPLATE_URI)
                    : client.validateRejectPromptAndDataFilling(prompt, schema, NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "validate_" + testCase.decision() + "_and_fill",
                    testCase.decision(), null, Map.of("prompt", prompt, "schema", schema,
                            "template_uri", NegotiationSampleFlow.ENDING_TEMPLATE_URI.uri()),
                    Map.of("filled_data", filled.data()), startedAt, null));
            return filled;
        } catch (RuntimeException exception) {
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "validate_" + testCase.decision() + "_and_fill",
                    testCase.decision(), null, Map.of("prompt", prompt, "schema", schema,
                            "template_uri", NegotiationSampleFlow.ENDING_TEMPLATE_URI.uri()), null, startedAt, exception));
            throw exception;
        }
    }

    private static Map<String, Object> stageEvent(
            String runId,
            NegotiationEvaluationFlowCase testCase,
            String stage,
            String phase,
            NegotiationContext context,
            Map<String, Object> request,
            Map<String, Object> response,
            long startedAt,
            RuntimeException exception) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("run_id", runId);
        event.put("case_id", testCase.id());
        int step = stageStep(stage);
        event.put("step", step);
        event.put("step_label", step + "/4");
        event.put("api", stageApi(stage));
        event.put("caller", stageCaller(stage));
        event.put("phase", phase);
        event.put("decision", testCase.decision());
        event.put("stage", stage);
        event.put("expected", "propose".equals(phase) ? testCase.expectedPropose() : testCase.expectedEnding());
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

    private static void writeStage(
            NegotiationEvaluationProcessLogger processLogger,
            List<Map<String, Object>> apiTrace,
            Map<String, Object> event) throws IOException {
        processLogger.write(event);
        apiTrace.add(event);
    }

    private static int stageStep(String stage) {
        if ("generate_propose".equals(stage)) {
            return 1;
        }
        if ("validate_propose_and_fill".equals(stage)) {
            return 2;
        }
        return stage.startsWith("generate_") ? 3 : 4;
    }

    private static String stageApi(String stage) {
        return switch (stage) {
            case "generate_propose" -> "A2ATClient.generateNegotiationProposePromptFromText";
            case "validate_propose_and_fill" -> "A2ATServer.validateProposePromptAndDataFilling";
            case "generate_accept" -> "A2ATServer.generateNegotiationAcceptPromptFromText";
            case "validate_accept_and_fill" -> "A2ATClient.validateAcceptPromptAndDataFilling";
            case "generate_reject" -> "A2ATServer.generateNegotiationRejectPromptFromText";
            case "validate_reject_and_fill" -> "A2ATClient.validateRejectPromptAndDataFilling";
            default -> stage;
        };
    }

    private static String stageCaller(String stage) {
        return stage.startsWith("generate_propose") || stage.startsWith("validate_accept")
                || stage.startsWith("validate_reject") ? "client" : "server";
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

    private static List<NegotiationEvaluationFlowCase> loadCases(String selector) {
        return switch (selector.toLowerCase(java.util.Locale.ROOT)) {
            case "", "full" -> NegotiationEvaluationCaseLoader.loadFlows();
            case "smoke" -> NegotiationEvaluationCaseLoader.loadSmokeFlows();
            default -> NegotiationEvaluationCaseLoader.loadSelectedFlows(parseCaseIds(selector));
        };
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
