package net.openan.a2at.sample.task_t;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.StandardTemplates;
import net.openan.a2at.sdk.core.validation.TemplateUri;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * End-to-end accuracy demo for the {@code Task-T} extension.
 *
 * <p>The demo closes the loop between the client facade and the server facade over the
 * {@code Task-T/network-layer/private-line-complaint/v1} template (transfer / private-line business complaint
 * diagnosis) in two independent cases:
 *
 * <ol>
 *   <li>{@code A2ATClient#generateTaskPromptFromText} fed with natural language;</li>
 *   <li>{@code A2ATClient#generateTaskPromptFromDataWithSchema} fed with a slot-value map plus a semantics schema.
 * </ol>
 *
 * <p>Each generated prompt is passed to {@code A2ATServer#validateAndFillingTaskData}; the extracted parameters are
 * compared against the sample ground truth and the results are aggregated into a per-case field accuracy and sample
 * pass rate.
 *
 * <p>Run with {@code java ... TaskTDemoMain [env-file-path]}. The env file resolves as follows: an explicit first
 * argument wins; otherwise a {@code client.env} in the working directory (repo root) that carries the required LLM
 * keys; otherwise the bundled sample template {@code a2a-t-sample/src/main/resources/sample/task_t/client.env}.
 * A working-directory {@code client.env} is only honored when it defines non-blank {@code A2AT_LLM_PROVIDER},
 * {@code A2AT_LLM_MODEL} and {@code A2AT_LLM_API_KEY} (cf. the leftover {@code subscribe_incident} template in newer
 * checkouts would otherwise shadow the Task-T sample); otherwise the bundled template is used. The env must configure
 * a reachable OpenAI-compatible LLM ({@code A2AT_LLM_API_KEY}, {@code A2AT_LLM_BASE_URL}, {@code A2AT_LLM_MODEL},
 * {@code A2AT_LLM_PROVIDER=openai}) — the server-side semantic validation performs one LLM call per sample.
 */
public final class TaskTDemoMain {

    private static final String DEFAULT_ENV_FILE = "client.env";

    private static final String BUNDLED_ENV_FILE =
            Path.of("a2a-t-sample", "src", "main", "resources", "sample", "task_t", "client.env").toString();

    private static final List<String> REQUIRED_LLM_KEYS =
            List.of("A2AT_LLM_PROVIDER", "A2AT_LLM_MODEL", "A2AT_LLM_API_KEY");

    private TaskTDemoMain() {
    }

    /**
     * Runs both client-API cases against the built-in private-line complaint diagnosis samples.
     *
     * @param args optional first argument is the {@code .env} file path
     */
    public static void main(String[] args) {
        Path envPath = resolveEnvPath(args);
        println("Task-T 准确率验证样例，env: " + envPath.toAbsolutePath() + (Files.exists(envPath) ? "" : "  (不存在，请先配置)"));
        println("模板: " + StandardTemplates.PRIVATE_LINE_COMPLAINT.uri());
        println();

        A2ATClient client = new A2ATClient(envPath);
        A2ATServer server = new A2ATServer(envPath);

        List<TaskTAccuracyEvaluator.SampleScore> textScores = runTextCase(client, server);
        println();
        System.out.println("══════════════════════════════════════════════════════");
        List<TaskTAccuracyEvaluator.SampleScore> dataScores = runDataWithSchemaCase(client, server);

        println();
        System.out.println("══════════════════════════════════════════════════════");
        printSummary(TaskTAccuracyEvaluator.summarize("generateTaskPromptFromText", textScores));
        printSummary(TaskTAccuracyEvaluator.summarize("generateTaskPromptFromDataWithSchema", dataScores));
    }

    private static List<TaskTAccuracyEvaluator.SampleScore> runTextCase(A2ATClient client, A2ATServer server) {
        println("================ 用例一：generateTaskPromptFromText ================");
        List<TaskTSample> samples = TaskTPrivateLineComplaintSamples.textSamples();
        List<TaskTAccuracyEvaluator.SampleScore> scores = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            TaskTSample sample = samples.get(i);
            printSampleHeader("用例一", i, samples.size(), sample.name());
            println("[输入] 自然语言文本:");
            println(sample.text());
            println();
            try {
                MetadataContent metadata = client.generateTaskPromptFromText(sample.text(), StandardTemplates.PRIVATE_LINE_COMPLAINT);
                printGeneratedMetadata(metadata);
                TaskTAccuracyEvaluator.SampleScore score =
                        validateAndScore(server, sample, metadata, "用例一");
                scores.add(score);
            } catch (PromptGenerationException exception) {
                printFailure("生成失败", exception);
                scores.add(new TaskTAccuracyEvaluator.SampleScore(sample.name(), false, List.of()));
            }
            println();
        }
        return scores;
    }

    private static List<TaskTAccuracyEvaluator.SampleScore> runDataWithSchemaCase(A2ATClient client, A2ATServer server) {
        println("========== 用例二：generateTaskPromptFromDataWithSchema ==========");
        List<TaskTSample> samples = TaskTPrivateLineComplaintSamples.dataWithSchemaSamples();
        List<TaskTAccuracyEvaluator.SampleScore> scores = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            TaskTSample sample = samples.get(i);
            printSampleHeader("用例二", i, samples.size(), sample.name());
            println("[输入] 数据(data):");
            println(pretty(sample.data()));
            println("[输入] 语义schema(schema):");
            println(pretty(sample.semanticsSchema()));
            println();
            try {
                MetadataContent metadata = client.generateTaskPromptFromDataWithSchema(
                        sample.data(), sample.semanticsSchema(), StandardTemplates.PRIVATE_LINE_COMPLAINT);
                printGeneratedMetadata(metadata);
                TaskTAccuracyEvaluator.SampleScore score =
                        validateAndScore(server, sample, metadata, "用例二");
                scores.add(score);
            } catch (PromptGenerationException exception) {
                printFailure("生成失败", exception);
                scores.add(new TaskTAccuracyEvaluator.SampleScore(sample.name(), false, List.of()));
            }
            println();
        }
        return scores;
    }

    private static TaskTAccuracyEvaluator.SampleScore validateAndScore(
            A2ATServer server, TaskTSample sample, MetadataContent metadata, String caseLabel) {
        try {
            Map<String, Object> extracted =
                    server.validateAndFillingTaskData(metadata.promptText(), sample.validationSchema(), StandardTemplates.PRIVATE_LINE_COMPLAINT)
                            .data();
            println("[服务端] " + caseLabel + " validateAndFillingTaskData 通过，提取参数:");
            println(pretty(extracted));
            List<TaskTAccuracyEvaluator.FieldScore> fields = TaskTAccuracyEvaluator.scoreFields(sample, extracted);
            printFieldScores(fields);
            return new TaskTAccuracyEvaluator.SampleScore(sample.name(), true, fields);
        } catch (ContentValidationException exception) {
            printFailure("校验不通过", exception);
            return new TaskTAccuracyEvaluator.SampleScore(sample.name(), false, List.of());
        }
    }

    private static void printSampleHeader(String caseLabel, int index, int total, String name) {
        System.out.println("──────────── " + caseLabel + " 样本[" + (index + 1) + "/" + total + "] " + name + " ────────────");
    }

    private static void printGeneratedMetadata(MetadataContent metadata) {
        println("[生成] MetadataContent:");
        println("  extensionUri: " + metadata.extensionUri());
        println("  templateUri : " + metadata.templateUri());
        println("  promptText  : ");
        println(metadata.promptText());
    }

    private static void printFieldScores(List<TaskTAccuracyEvaluator.FieldScore> fields) {
        println("[比对] 字段级命中 (命中规则: 归一化后相同或互相包含):");
        for (TaskTAccuracyEvaluator.FieldScore field : fields) {
            String mark = field.matched() ? "✔ 命中" : "✘ 未命中";
            String detail = field.matched() ? "" : "  " + field.detail();
            println("  " + mark + " " + field.slot() + detail);
        }
    }

    private static void printFailure(String stage, A2ATError error) {
        println("[失败] " + stage + ": [" + error.getCode() + "] " + error.getMessage());
    }

    private static void printSummary(TaskTAccuracyEvaluator.Summary summary) {
        System.out.println("──────────── 汇总: " + summary.api() + " ────────────");
        println("  样本数: " + summary.sampleCount()
                + "  通过样本: " + summary.passedSamples()
                + "  字段命中: " + summary.matchedFields() + "/" + summary.expectedFields());
        println("  字段准确率: " + percent(summary.fieldAccuracyPercent())
                + "  样本通过率: " + percent(summary.samplePassRatePercent()));
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private static String pretty(Map<String, ?> map) {
        if (map == null) {
            return "  <null>";
        }
        String body = map.entrySet().stream()
                .map(entry -> entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.joining("\n  ", "  ", ""));
        return body;
    }

    private static void println(Object message) {
        System.out.println(message);
    }

    private static void println() {
        System.out.println();
    }

    private static Path resolveEnvPath(String[] args) {
        if (args.length > 0) {
            return Path.of(args[0]);
        }
        Path cwdEnv = Path.of(DEFAULT_ENV_FILE);
        if (Files.exists(cwdEnv) && hasRequiredLlmKeys(cwdEnv)) {
            return cwdEnv;
        }
        return Path.of(BUNDLED_ENV_FILE);
    }

    private static boolean hasRequiredLlmKeys(Path envFile) {
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
                entries.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
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
}