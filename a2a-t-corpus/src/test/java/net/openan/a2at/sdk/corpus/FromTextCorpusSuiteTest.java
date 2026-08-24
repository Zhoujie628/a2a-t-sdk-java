package net.openan.a2at.sdk.corpus;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Data-driven suite of the from-text family corpus: every case under {@code negotiation-cases/from-text/} becomes one
 * dynamic test executed by the {@link CaseEngine} against the production negotiation content wiring.
 *
 * <p>The seed batch carries {@code from-text/happy.json} (FT-HAPPY: propose with the differential double run, accept with
 * the conclusion-literal contract) and {@code from-text/template-resolution.json} (FT-TPL: the injected
 * template-not-found matrix). The later batches (FT-PROG, FT-EXTRACT, FT-RETRY) land as further files of the same
 * directory without any Java change.
 *
 * <p>Run a subset with {@code -Dcase.filter=FT-RETRY-*} (glob against the case id, design document Q1).
 *
 * @since 2026-08
 */
class FromTextCorpusSuiteTest {

    /** Executes every expanded from-text case of the corpus. */
    @TestFactory
    Stream<DynamicTest> fromTextCorpusCases() {
        return CorpusSuites.caseTests("from-text");
    }
}
