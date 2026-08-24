package net.openan.a2at.sdk.corpus;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Data-driven suite of the from-data family corpus: every case under {@code negotiation-cases/from-data/} becomes one
 * dynamic test executed by the {@link CaseEngine} against the production negotiation content wiring.
 *
 * <p>The seed batch carries {@code from-data/happy.json} (FD-HAPPY: deterministic accept generation with a zero LLM call
 * proof). The later batch (FD-PROG, the absorbed 22-row programming-error matrix) lands as a further file of the same
 * directory without any Java change.
 *
 * <p>Run a subset with {@code -Dcase.filter=FD-*} (glob against the case id, design document Q1).
 *
 * @since 2026-08
 */
class FromDataCorpusSuiteTest {

    /** Executes every expanded from-data case of the corpus. */
    @TestFactory
    Stream<DynamicTest> fromDataCorpusCases() {
        return CorpusSuites.caseTests("from-data");
    }
}
