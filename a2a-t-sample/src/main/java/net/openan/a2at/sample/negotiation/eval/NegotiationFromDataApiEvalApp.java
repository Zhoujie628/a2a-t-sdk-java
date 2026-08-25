package net.openan.a2at.sample.negotiation.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * Focused capability evaluator for the three Negotiation-T fromData generation interfaces:
 * {@code generateNegotiationProposePromptFromData}, {@code generateNegotiationAcceptPromptFromData} and
 * {@code generateNegotiationRejectPromptFromData}.
 *
 * <p>Unlike the closed-loop evaluator ({@link NegotiationEvalApp}), this suite touches ONLY the negotiation
 * generation interfaces - no Task-T or other non-negotiation APIs are involved. Every input is pure structured
 * JSON: each item is one key (the slot name) carrying its value (the field content); no natural-language
 * passages. Because fromData negotiation generation is deterministic template rendering (no LLM call), the
 * assertions are exact:
 *
 * <ul>
 *   <li>positive cases - the generated prompt must carry every item name and value, the propose relationship
 *       sentence (when provided), every {@code expect.contains} marker, and the matching template URI;
 *   <li>negative cases - an empty item set or a phase-mismatched template must make the call fail.
 * </ul>
 *
 * <p>The run needs no real LLM: the {@code .env} only has to carry a non-empty {@code A2AT_LLM_API_KEY}
 * because the facade constructors validate it; no LLM call is ever made by these interfaces.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * java @a2a-t-sample/target/fromdata-eval.javaargs.txt [--out fromdata-eval-report.json] /path/to/.env
 * }</pre>
 *
 * @since 2026-08
 */
public final class NegotiationFromDataApiEvalApp {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SUITE_RESOURCE = "sample/negotiation/eval/fromdata-api-suite.json";

    private static Consumer<String> sink = System.out::println;

    private NegotiationFromDataApiEvalApp() {}

    /** Entry point. */
    public static void main(String[] args) {
        Path envPath = null;
        Path outPath = Path.of("fromdata-eval-report.json");
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--out".equals(arg) && i + 1 < args.length) {
                outPath = Path.of(args[++i]);
            } else if (!arg.startsWith("--")) {
                envPath = Path.of(arg);
            }
        }
        if (envPath == null) {
            System.err.println(
                    "Usage: java @a2a-t-sample/target/fromdata-eval.javaargs.txt [--out fromdata-eval-report.json]"
                            + " /path/to/.env");
            System.exit(1);
        }

        Map<String, Object> suite = loadSuite();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", suite.get("suite"));
        report.put("description", suite.get("description"));
        report.put("scenario", suite.get("scenario"));
        report.put("deterministic", true);
        report.put("generated_at", LocalDateTime.now().format(TIMESTAMP));

        List<Map<String, Object>> cases = new ArrayList<>();
        report.put("cases", cases);
        int index = 0;
        List<Map<String, Object>> suiteCases = asMapList(suite.get("cases"));
        for (Map<String, Object> testCase : suiteCases) {
            index++;
            emit("\n[fromdata-eval] [" + index + "/" + suiteCases.size() + "] case " + testCase.get("id") + ": "
                    + testCase.get("intent"));
            long startNanos = System.nanoTime();
            Map<String, Object> record = runCase(envPath, testCase);
            record.put("duration_ms", (System.nanoTime() - startNanos) / 1_000_000);
            cases.add(record);
            writeReport(report, outPath);
        }
        report.put("metrics", metrics(cases));
        writeReport(report, outPath);
        emit("\n[fromdata-eval] report written to " + outPath.toAbsolutePath());
    }

    // -- one case: one API call with exact assertions --

    private static Map<String, Object> runCase(Path envPath, Map<String, Object> testCase) {
        String caseId = String.valueOf(testCase.get("id"));
        String api = String.valueOf(testCase.get("api"));
        Map<String, Object> input = asMap(testCase.get("input"));
        Map<String, Object> expect = asMap(testCase.get("expect"));
        boolean expectSuccess = Boolean.TRUE.equals(expect.get("succeeds"));

        Map<String, Object> itemsInput = asMap(input.get("items"));
        String relationship = String.valueOf(input.get("relationship"));
        TemplateUri template = resolveTemplate(api, input);

        NegotiationContext context =
                new NegotiationContext(UUID.randomUUID().toString(), 1, NegotiationContext.DEFAULT_MAX_ROUNDS);
        List<NegotiationItem> items = new ArrayList<>();
        for (Map.Entry<String, Object> entry : itemsInput.entrySet()) {
            items.add(new NegotiationItem(entry.getKey(), String.valueOf(entry.getValue())));
        }

        Map<String, Object> apiInput = new LinkedHashMap<>();
        apiInput.put("context", Map.of("round", 1, "maxRounds", NegotiationContext.DEFAULT_MAX_ROUNDS));
        apiInput.put("items", itemsInput);
        if (input.containsKey("relationship")) {
            apiInput.put("relationship", relationship);
        }
        apiInput.put("template_uri", template.uri());
        String method = "propose".equals(api)
                ? "A2ATServer.generateNegotiationProposePromptFromData"
                : "A2ATClient.generateNegotiation"
                        + ("accept".equals(api) ? "Accept" : "Reject") + "PromptFromData";
        String role = "propose".equals(api) ? "server" : "client";

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("case", caseId);
        record.put("api", method);
        record.put("role", role);
        record.put("input", apiInput);
        record.put("api_calls", List.of(apiCall(method, apiInput)));
        record.put("intent", testCase.get("intent"));

        List<Map<String, Object>> checks = new ArrayList<>();
        record.put("checks", checks);
        try {
            MetadataContent content = switch (api) {
                case "propose" -> new A2ATServer(envPath).generateNegotiationProposePromptFromData(
                        new NegotiationProposeData(
                                context, new InformationProposeContent(items, relationship)), template);
                case "accept" -> new A2ATClient(envPath).generateNegotiationAcceptPromptFromData(
                        new NegotiationEndingData(
                                context, new InformationEndingContent(NegotiationConclusion.ACCEPT, items)),
                        template);
                case "reject" -> new A2ATClient(envPath).generateNegotiationRejectPromptFromData(
                        new NegotiationEndingData(
                                context, new InformationEndingContent(NegotiationConclusion.REJECT, items)),
                        template);
                default -> throw new IllegalArgumentException("Unknown api: " + api);
            };
            record.put("generated_prompt", content.promptText());
            record.put("template_uri", content.templateUri());
            record.put("extension_uri", content.extensionUri());
            if (expectSuccess) {
                String prompt = normalize(content.promptText());
                for (Map.Entry<String, Object> entry : itemsInput.entrySet()) {
                    check(checks, "contains item name: " + entry.getKey(),
                            prompt.contains(normalize(entry.getKey())));
                    check(checks, "contains item value: " + entry.getKey(),
                            prompt.contains(normalize(String.valueOf(entry.getValue()))));
                }
                if (input.containsKey("relationship") && !relationship.isBlank()) {
                    check(checks, "contains relationship", prompt.contains(normalize(relationship)));
                }
                for (String marker : stringList(expect.get("contains"))) {
                    check(checks, "contains marker: " + marker, prompt.contains(normalize(marker)));
                }
                String expectedTemplate = String.valueOf(expect.get("template"));
                if (!expect.isEmpty() && expect.get("template") != null) {
                    check(checks, "template uri matches",
                            expectedTemplate.equals(content.templateUri()));
                }
            } else {
                check(checks, "expected failure but generation succeeded", false);
            }
        } catch (RuntimeException error) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("type", error.getClass().getSimpleName());
            if (error instanceof A2ATError a2atError) {
                failure.put("code", a2atError.getCode());
            }
            failure.put("message", String.valueOf(error.getMessage()));
            record.put("error", failure);
            if (expectSuccess) {
                check(checks, "expected success but failed: " + failure.get("type"), false);
            } else {
                check(checks, "failed as expected (" + failure.get("type") + ")", true);
            }
        }

        boolean pass = checks.stream().allMatch(check -> Boolean.TRUE.equals(check.get("pass")));
        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("pass", pass);
        List<String> failed = new ArrayList<>();
        for (Map<String, Object> check : checks) {
            if (!Boolean.TRUE.equals(check.get("pass"))) {
                failed.add(String.valueOf(check.get("name")));
            }
        }
        verdict.put("reason", failed.isEmpty() ? "" : "failed checks: " + String.join("; ", failed));
        record.put("verdict", verdict);
        emit("[fromdata-eval]   verdict: " + (pass ? "PASS" : "FAIL")
                + (failed.isEmpty() ? "" : " (" + verdict.get("reason") + ")"));
        return record;
    }

    private static TemplateUri resolveTemplate(String api, Map<String, Object> input) {
        if (input.containsKey("template")) {
            return parseTemplate(String.valueOf(input.get("template")));
        }
        return "propose".equals(api)
                ? StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE
                : StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;
    }

    private static void check(List<Map<String, Object>> checks, String name, boolean pass) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("name", name);
        check.put("pass", pass);
        checks.add(check);
    }

    private static Map<String, Object> apiCall(String method, Map<String, Object> input) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("method", method);
        call.put("input", input);
        return call;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    // -- report --

    private static Map<String, Object> metrics(List<Map<String, Object>> cases) {
        int pass = 0;
        for (Map<String, Object> record : cases) {
            Map<?, ?> verdict = (Map<?, ?>) record.get("verdict");
            if (Boolean.TRUE.equals(verdict.get("pass"))) {
                pass++;
            }
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("cases", cases.size());
        metrics.put("passed", pass);
        metrics.put("pct", cases.isEmpty() ? 0 : Math.round(pass * 1000.0 / cases.size()) / 10.0);
        return metrics;
    }

    private static void writeReport(Map<String, Object> report, Path outPath) {
        try {
            Files.createDirectories(outPath.toAbsolutePath().getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), report);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write the evaluation report: " + outPath, exception);
        }
    }

    // -- suite loading and small helpers --

    private static Map<String, Object> loadSuite() {
        try (InputStream in = NegotiationFromDataApiEvalApp.class.getResourceAsStream("/" + SUITE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Eval suite resource not found: " + SUITE_RESOURCE);
            }
            return MAPPER.readValue(in, new TypeReference<Map<String, Object>>() {});
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load the eval suite: " + SUITE_RESOURCE, exception);
        }
    }

    private static TemplateUri parseTemplate(String templateUri) {
        return TemplateUri.parse(templateUri)
                .orElseThrow(() -> new IllegalArgumentException("Unparseable template URI: " + templateUri));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    private static void emit(String line) {
        sink.accept(line);
    }
}
