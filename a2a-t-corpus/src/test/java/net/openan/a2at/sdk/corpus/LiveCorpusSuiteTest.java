package net.openan.a2at.sdk.corpus;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Data-driven suite of the live-LLM family corpus (live design document §2): every case under {@code
 * negotiation-cases/live/} becomes one dynamic test executed by the {@link LiveCaseEngine} against the real
 * OpenAI-compatible endpoint of the dedicated test configuration.
 *
 * <p>The family is opt-in exactly like its configuration (Q1): the factory passes through the adjudicated skip gate
 * {@link LiveLlmConfig#assumeConfigured()} — an unconfigured environment skips the whole family through an assumption
 * with the configuration hint, and a resolved but invalid configuration is a red failure, not a skip — so {@code mvn
 * test} and CI stay offline-deterministic. A configured run creates its transcript directory at factory-evaluation
 * time (§5 [R-C3]) and flushes the transcript and summary after the last case.
 *
 * <p>Run a subset with {@code -Dcase.filter=LIVE-GEN-*} (the same glob rule as the offline families).
 *
 * @since 2026-08
 */
class LiveCorpusSuiteTest {

    /** The run handle of the configured run; null while the family is skipped, set at factory evaluation. */
    private static LiveTranscript.@Nullable Run run;

    /** Executes every expanded live case of the corpus against the real endpoint. */
    @TestFactory
    Stream<DynamicTest> liveCorpusCases() {
        LiveLlmConfig.assumeConfigured();
        LiveLlmConfig config = LiveLlmConfig.fromCurrentProcess();
        run = LiveTranscript.createRun();
        LiveCaseEngine engine = new LiveCaseEngine(config, run);
        List<DynamicTest> tests = new ArrayList<>();
        for (LiveCase liveCase : CorpusSuites.loadCorpus().liveCases()) {
            if (!CorpusSuites.selected(liveCase.id(), liveCase.baseId())) {
                continue;
            }
            tests.add(DynamicTest.dynamicTest(
                    CorpusSuites.displayName(liveCase.id(), liveCase.summary()), () -> engine.run(liveCase)));
        }
        CorpusSuites.noteEmpty("live", tests.size());
        return tests.stream();
    }

    /**
     * Flushes the transcript and summary of the configured run once every live case has been executed. A failed write
     * is a warning, not a verdict: the case outcomes have already surfaced through their dynamic tests, so a locked or
     * read-only {@code target/} must not turn a green run red.
     */
    @AfterAll
    static void writeTranscript() {
        if (run == null) {
            return;
        }
        try {
            Path transcript = run.write();
            System.out.println("[corpus] live transcript written to " + transcript + ": " + run.summary());
        } catch (UncheckedIOException exception) {
            System.out.println(
                    "[corpus] WARNING: failed to write the live transcript to " + run.directory() + " ("
                            + exception.getMessage() + "); the case verdicts above stay authoritative");
        }
    }
}
