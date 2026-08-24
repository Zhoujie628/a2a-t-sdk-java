package net.openan.a2at.sdk.corpus;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Data-driven suite of the E2E scenario corpus: every scenario under {@code negotiation-cases/scenarios/} becomes one
 * dynamic test executed by the {@link ScenarioEngine} — every step is a full corpus case run by the {@link CaseEngine},
 * plus the {@code prompt.fromStep} resolution and the flow-level expectation.
 *
 * <p>The seed batch carries {@code scenarios/information-flows.json} (SC-INFO: the single-round happy accept loop, both
 * languages). The later batches (SC-TGT, SC-FSB, SC-EXH, SC-ERR, boundary flows) land as further files of the same
 * directory without any Java change.
 *
 * <p>Run a subset with {@code -Dcase.filter=SC-INFO-*} (glob against the scenario id, design document Q1).
 *
 * @since 2026-08
 */
class ScenarioCorpusSuiteTest {

    /** Executes every expanded scenario of the corpus. */
    @TestFactory
    Stream<DynamicTest> scenarioCorpusCases() {
        return CorpusSuites.scenarioTests("scenarios");
    }
}
