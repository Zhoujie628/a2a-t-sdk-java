package net.openan.a2at.sdk.negotiation.testdata;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Data-driven suite of the validate family corpus: every case under {@code negotiation-cases/validate/} becomes one
 * dynamic test executed by the {@link CaseEngine} against the production negotiation content wiring.
 *
 * <p>The seed batch carries {@code validate/happy.json} (VAL-HAPPY: propose validation with the merged-params expectation
 * carrying the context keys). The later batches (VAL-PROG, VAL-RULE, VAL-SEM, VAL-RETRY, VAL-MERGE, VAL-MAP) land as
 * further files of the same directory without any Java change.
 *
 * <p>Run a subset with {@code -Dcase.filter=VAL-RULE-*} (glob against the case id, design document Q1).
 *
 * @since 2026-08
 */
class ValidateCorpusSuiteTest {

    /** Executes every expanded validate case of the corpus. */
    @TestFactory
    Stream<DynamicTest> validateCorpusCases() {
        return CorpusSuites.caseTests("validate");
    }
}
