package net.openan.a2at.sdk.corpus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Transcript writer of the live corpus (design document §5): every run of {@code LiveCorpusSuiteTest} leaves its full
 * evidence under {@code target/live-corpus/<yyyyMMdd-HHmmss>/} of the corpus module's build directory, so a live run
 * can be judged — and its prompts recalibrated — after the fact.
 *
 * <p>A run accumulates its {@link LiveCaseResult}s in memory; {@link Run#write()} flushes once at the end into two
 * pretty-printed files: {@code transcript.json} (the JSON array of case results) and {@code summary.json} (the
 * aggregated {@link LiveRunSummary}, including the M5 schema-adherence statistic). The run directory is created by
 * {@link #createRun()} — evaluated when the suite's {@code @TestFactory} is evaluated, per the design — with a
 * second-precision timestamp and a uniquifying suffix when two runs start within the same second.
 *
 * <p>The default root resolves like the other corpus tests resolve the repo layout: from the working directory up to
 * the {@code a2a-t-corpus} module directory; when nothing matches (an IDE run with an unrelated working directory)
 * the fallback is {@code a2a-t-corpus/target/live-corpus} under the working directory. The whole tree lives under
 * {@code target/} and is therefore never committed.
 *
 * @since 2026-08
 */
public final class LiveTranscript {

    /** Directory name pattern of one run, second-precision as the design specifies. */
    private static final DateTimeFormatter RUN_DIRECTORY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private LiveTranscript() {}

    /**
     * Creates a run directory under the default {@code target/live-corpus} root of the corpus module.
     *
     * @return handle of the new run, with its timestamped directory already created
     */
    public static Run createRun() {
        return createRun(defaultRoot());
    }

    /**
     * Creates a run directory under an explicit root.
     *
     * @param rootDir root directory the timestamped run directory is created under
     * @return handle of the new run, with its timestamped directory already created
     */
    public static Run createRun(Path rootDir) {
        Objects.requireNonNull(rootDir, "rootDir");
        try {
            String stamp = RUN_DIRECTORY_FORMAT.format(LocalDateTime.now());
            Path directory = rootDir.resolve(stamp);
            int uniquifier = 1;
            while (Files.exists(directory)) {
                directory = rootDir.resolve(stamp + "-" + uniquifier++);
            }
            Files.createDirectories(directory);
            return new Run(directory);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to create the live transcript run directory under " + rootDir, exception);
        }
    }

    /**
     * Resolves the default {@code target/live-corpus} root: the corpus module's build directory when the working
     * directory is (or lives under) the repository, and a working-directory fallback otherwise.
     *
     * @return default live transcript root
     */
    private static Path defaultRoot() {
        Path directory = Path.of(".").toAbsolutePath().normalize();
        while (directory != null) {
            Path module = directory.resolve("a2a-t-corpus");
            if (Files.isDirectory(module)) {
                return module.resolve("target").resolve("live-corpus");
            }
            if (isCorpusModuleDirectory(directory)) {
                return directory.resolve("target").resolve("live-corpus");
            }
            directory = directory.getParent();
        }
        return Path.of(".").toAbsolutePath().normalize().resolve("a2a-t-corpus").resolve("target").resolve("live-corpus");
    }

    private static boolean isCorpusModuleDirectory(Path directory) {
        return directory.getFileName() != null && "a2a-t-corpus".equals(directory.getFileName().toString());
    }

    /**
     * Handle of one live transcript run: it accumulates the case results of one suite execution and flushes them once.
     *
     * @since 2026-08
     */
    public static final class Run {

        private static final String TRANSCRIPT_FILE = "transcript.json";

        private static final String SUMMARY_FILE = "summary.json";

        private final Path directory;

        private final List<LiveCaseResult> cases = new ArrayList<>();

        private Run(Path directory) {
            this.directory = directory;
        }

        /**
         * Returns the run directory the transcript files are written into.
         *
         * @return timestamped run directory, already created
         */
        public Path directory() {
            return directory;
        }

        /**
         * Appends one case result to the run.
         *
         * @param result verdict of one executed live case
         */
        public synchronized void appendCase(LiveCaseResult result) {
            cases.add(Objects.requireNonNull(result, "result"));
        }

        /**
         * Returns the aggregate of the cases appended so far.
         *
         * @return run summary computed over the appended cases
         */
        public synchronized LiveRunSummary summary() {
            return LiveTranscript.summarize(cases);
        }

        /**
         * Flushes the run into its directory: the pretty-printed JSON array of case results as {@code transcript.json}
         * and the aggregate as {@code summary.json}.
         *
         * @return path of the written {@code transcript.json}
         */
        public synchronized Path write() {
            try {
                Path transcriptFile = directory.resolve(TRANSCRIPT_FILE);
                MAPPER.writeValue(transcriptFile.toFile(), List.copyOf(cases));
                MAPPER.writeValue(directory.resolve(SUMMARY_FILE).toFile(), summary());
                return transcriptFile;
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to write the live transcript of " + directory, exception);
            }
        }
    }

    /** Aggregates the case results into the run summary. */
    private static LiveRunSummary summarize(List<LiveCaseResult> cases) {
        int passCount = 0;
        int failCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        int totalLlmCalls = 0;
        long totalPromptTokens = 0;
        long totalCompletionTokens = 0;
        int schemaParseFailureCount = 0;
        for (LiveCaseResult result : cases) {
            switch (result.outcome()) {
                case PASS -> passCount++;
                case FAIL -> failCount++;
                case SKIP -> skipCount++;
                case ERROR -> errorCount++;
            }
            for (LiveLlmCall call : result.llmCalls()) {
                totalLlmCalls++;
                Integer promptTokens = call.usage().get("prompt_tokens");
                Integer completionTokens = call.usage().get("completion_tokens");
                totalPromptTokens += promptTokens == null ? 0 : promptTokens;
                totalCompletionTokens += completionTokens == null ? 0 : completionTokens;
                if (!call.failed() && !contentIsJsonObject(call.content())) {
                    schemaParseFailureCount++;
                }
            }
        }
        return new LiveRunSummary(
                cases.size(),
                passCount,
                failCount,
                skipCount,
                errorCount,
                totalLlmCalls,
                totalPromptTokens,
                totalCompletionTokens,
                schemaParseFailureCount);
    }

    /**
     * Returns whether an answered content is a JSON object; the null content of a failed call does not count, only a
     * real non-object answer does (the M5 schema-adherence statistic).
     */
    private static boolean contentIsJsonObject(String content) {
        try {
            return MAPPER.readTree(content).isObject();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return false;
        }
    }
}
