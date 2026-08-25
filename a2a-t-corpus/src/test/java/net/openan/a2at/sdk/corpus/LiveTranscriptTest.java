package net.openan.a2at.sdk.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Direct unit tests of {@link LiveTranscript}: the run directory layout, the pretty-printed JSON array of case
 * results, and the aggregated {@link LiveRunSummary} with the M5 schema-adherence statistic.
 */
class LiveTranscriptTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<Map<String, String>> MESSAGES = List.of(Map.of("role", "user", "content", "投诉文本"));

    @TempDir
    Path tempDir;

    @Test
    void aRunCreatesATimestampedDirectoryUnderTheGivenRoot() {
        LiveTranscript.Run run = LiveTranscript.createRun(tempDir);

        String directoryName = run.directory().getFileName().toString();
        assertTrue(
                directoryName.matches("\\d{8}-\\d{6}(-\\d+)?"),
                "the run directory carries the yyyyMMdd-HHmmss timestamp, but was: " + directoryName);
        assertTrue(Files.isDirectory(run.directory()), "createRun creates the run directory eagerly");
    }

    @Test
    void twoRunsInTheSameSecondGetDistinctDirectories() {
        LiveTranscript.Run first = LiveTranscript.createRun(tempDir);
        LiveTranscript.Run second = LiveTranscript.createRun(tempDir);

        assertTrue(
                !first.directory().equals(second.directory()),
                "two runs of the same second must not share a directory: " + first.directory() + " vs "
                        + second.directory());
    }

    @Test
    void writeFlushesTheCaseArrayAndTheAggregateAsValidJson() throws IOException {
        LiveTranscript.Run run = LiveTranscript.createRun(tempDir);
        run.appendCase(new LiveCaseResult(
                "LIVE-GEN-01/zh-CN",
                LiveCaseResult.Outcome.PASS,
                "scenario + slot subset",
                "深圳访问广州的专线时延骤升，OSS侧事件流水号event-id-20260511-09013。",
                "private-line-complaint",
                Map.of("accessPort", "P533-01"),
                List.of(
                        new LiveLlmCall(
                                MESSAGES,
                                Map.of("type", "object"),
                                0.0,
                                null,
                                "{\"scenarioCode\":\"private-line-complaint\"}",
                                "qwen3-27b",
                                Map.of("prompt_tokens", 120, "completion_tokens", 30),
                                8,
                                null),
                        new LiveLlmCall(
                                MESSAGES,
                                null,
                                null,
                                null,
                                "<not a json object>",
                                "qwen3-27b",
                                Map.of("prompt_tokens", 200, "completion_tokens", 50),
                                5,
                                null)),
                1_234,
                null));
        run.appendCase(new LiveCaseResult(
                "LIVE-VAL-01/zh-CN",
                LiveCaseResult.Outcome.FAIL,
                "paramsAbsent",
                null,
                null,
                null,
                List.of(new LiveLlmCall(
                        MESSAGES,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        3,
                        "net.openan.a2at.sdk.llm.LLMRuntimeError: connection reset")),
                900,
                "faultTime was filled but expected absent: 2026-05-11"));
        run.appendCase(new LiveCaseResult(
                "LIVE-GEN-02/zh-CN",
                LiveCaseResult.Outcome.SKIP,
                "no live endpoint",
                null,
                null,
                null,
                List.of(),
                0,
                null));

        Path transcriptFile = run.write();

        assertEquals(run.directory().resolve("transcript.json"), transcriptFile);
        assertTrue(Files.isRegularFile(run.directory().resolve("summary.json")), "the aggregate is flushed too");

        JsonNode transcript = MAPPER.readTree(Files.readString(transcriptFile));
        assertTrue(transcript.isArray(), "the transcript is one JSON array of case results");
        assertEquals(3, transcript.size());
        assertEquals("LIVE-GEN-01/zh-CN", transcript.get(0).path("caseId").asText());
        assertEquals("PASS", transcript.get(0).path("outcome").asText());
        assertTrue(
                transcript.get(0).path("inputSummary").asText().contains("event-id-20260511-09013"),
                "the transcript carries the input summary of the case");
        assertEquals("private-line-complaint", transcript.get(0).path("scenarioCode").asText());
        assertEquals("P533-01", transcript.get(0).path("params").path("accessPort").asText());
        assertEquals(2, transcript.get(0).path("llmCalls").size(), "the recorded calls are embedded per case");
        assertEquals(
                "{\"scenarioCode\":\"private-line-complaint\"}",
                transcript.get(0).path("llmCalls").get(0).path("content").asText());
        assertEquals("FAIL", transcript.get(1).path("outcome").asText());
        assertTrue(transcript.get(1).path("failureDiff").asText().contains("faultTime"));
        assertEquals("SKIP", transcript.get(2).path("outcome").asText());
        assertTrue(
                Files.readString(transcriptFile).contains("\n  "), "the transcript is pretty-printed");
    }

    @Test
    void summaryAggregatesVerdictsTokensAndSchemaParseFailures() {
        LiveTranscript.Run run = LiveTranscript.createRun(tempDir);
        run.appendCase(new LiveCaseResult(
                "LIVE-GEN-01/zh-CN",
                LiveCaseResult.Outcome.PASS,
                "scenario",
                "深圳访问广州的专线时延骤升。",
                "s",
                Map.of(),
                List.of(
                        call("{\"a\":1}", Map.of("prompt_tokens", 10, "completion_tokens", 4)),
                        call("plain text, not json", Map.of("prompt_tokens", 100, "completion_tokens", 40))),
                10,
                null));
        run.appendCase(new LiveCaseResult(
                "LIVE-VAL-01/zh-CN",
                LiveCaseResult.Outcome.FAIL,
                "subset",
                null,
                null,
                null,
                List.of(call("[1,2]", Map.of("prompt_tokens", 1, "completion_tokens", 1))),
                10,
                "diff"));
        run.appendCase(new LiveCaseResult(
                "LIVE-VAL-02/zh-CN",
                LiveCaseResult.Outcome.ERROR,
                "engine blew up",
                null,
                null,
                null,
                List.of(new LiveLlmCall(MESSAGES, null, null, null, null, null, Map.of(), 2,
                        "net.openan.a2at.sdk.llm.LLMRuntimeError: timeout")),
                10,
                "boom"));
        run.appendCase(new LiveCaseResult(
                "LIVE-GEN-02/zh-CN", LiveCaseResult.Outcome.SKIP, "no endpoint", null, null, null, List.of(), 0, null));

        LiveRunSummary summary = run.summary();

        assertEquals(4, summary.totalCases());
        assertEquals(1, summary.passCount());
        assertEquals(1, summary.failCount());
        assertEquals(1, summary.skipCount());
        assertEquals(1, summary.errorCount());
        assertEquals(4, summary.totalLlmCalls());
        assertEquals(10 + 100 + 1, summary.totalPromptTokens());
        assertEquals(4 + 40 + 1, summary.totalCompletionTokens());
        assertEquals(2, summary.schemaParseFailureCount(), "plain text and a JSON array are schema parse failures;"
                + " the thrown call's null content is not");
    }

    @Test
    void anEmptyRunWritesAnEmptyArrayAndZeroCounts() throws IOException {
        LiveTranscript.Run run = LiveTranscript.createRun(tempDir);

        Path transcriptFile = run.write();

        JsonNode transcript = MAPPER.readTree(Files.readString(transcriptFile));
        assertTrue(transcript.isArray());
        assertEquals(0, transcript.size());
        JsonNode summary = MAPPER.readTree(Files.readString(run.directory().resolve("summary.json")));
        assertEquals(0, summary.path("totalCases").asInt());
        assertEquals(0, summary.path("totalLlmCalls").asInt());
        assertEquals(0, summary.path("schemaParseFailureCount").asInt());
    }

    private static LiveLlmCall call(String content, Map<String, Integer> usage) {
        return new LiveLlmCall(MESSAGES, Map.of("type", "object"), 0.0, 64, content, "qwen3-27b", usage, 1, null);
    }
}
