package net.openan.a2at.sdk.negotiation.testdata;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;

/**
 * Shared machinery of the four corpus suite classes (design document §3.4 item 4, Q1).
 *
 * <p>Every suite is a JUnit {@code @TestFactory} that loads the corpus from the test resources once, keeps the records of
 * its family directory, and turns each expanded case or scenario into one {@link DynamicTest} whose display name carries
 * the corpus id. A family directory that is absent or empty yields an empty stream (with a note on stdout), so the suites
 * stay green while the later corpus batches land file by file.
 *
 * <p>The {@code -Dcase.filter=<glob>} system property selects a subset at run time — for example
 * {@code mvn test -Dtest='FromTextCorpusSuiteTest' -Dcase.filter=FT-RETRY-*}. The glob matches either the expanded id
 * ({@code FT-RETRY-02/zh-CN}) or the base record id ({@code FT-RETRY-02}); {@code *} and {@code ?} are wildcards that
 * also match across the language suffix.
 *
 * @since 2026-08
 */
final class CorpusSuites {

    /** Classpath root of the negotiation test corpus. */
    static final String CORPUS_RESOURCE = "negotiation-cases";

    private static final String CASE_FILTER = System.getProperty("case.filter", "").trim();

    private CorpusSuites() {}

    // ------------------------------------------------------------------ corpus access

    static LoadedCorpus loadCorpus() {
        return NegotiationCaseLoader.loadFromClasspath(CORPUS_RESOURCE);
    }

    // ------------------------------------------------------------------ dynamic test assembly

    /**
     * Builds one dynamic test per expanded case of the given family directory.
     *
     * @param directory family directory under the corpus root, such as {@code from-text}
     * @return one dynamic test per selected case, empty when the directory carries no case
     */
    static Stream<DynamicTest> caseTests(String directory) {
        LoadedCorpus corpus = loadCorpus();
        List<DynamicTest> tests = new ArrayList<>();
        CaseEngine engine = new CaseEngine();
        for (NegotiationCase testCase : corpus.cases()) {
            if (!testCase.sourceFile().startsWith(directory + "/")) {
                continue;
            }
            if (!selected(testCase.id(), testCase.baseId())) {
                continue;
            }
            tests.add(DynamicTest.dynamicTest(
                    displayName(testCase.id(), testCase.summary()), () -> engine.run(testCase)));
        }
        noteEmpty(directory, tests.size());
        return tests.stream();
    }

    /**
     * Builds one dynamic test per expanded scenario of the given family directory.
     *
     * @param directory family directory under the corpus root, such as {@code scenarios}
     * @return one dynamic test per selected scenario, empty when the directory carries no scenario
     */
    static Stream<DynamicTest> scenarioTests(String directory) {
        LoadedCorpus corpus = loadCorpus();
        List<DynamicTest> tests = new ArrayList<>();
        ScenarioEngine engine = new ScenarioEngine();
        for (ScenarioCase scenario : corpus.scenarios()) {
            if (!scenario.sourceFile().startsWith(directory + "/")) {
                continue;
            }
            if (!selected(scenario.id(), scenario.baseId())) {
                continue;
            }
            tests.add(DynamicTest.dynamicTest(
                    displayName(scenario.id(), scenario.summary()), () -> engine.runScenario(scenario)));
        }
        noteEmpty(directory, tests.size());
        return tests.stream();
    }

    private static String displayName(String id, @Nullable String summary) {
        return summary == null ? id : id + " — " + summary;
    }

    private static void noteEmpty(String directory, int size) {
        if (size == 0) {
            System.out.println(
                    "[corpus] no record under '" + directory + "/' matched (directory missing, empty, or filtered by"
                            + " -Dcase.filter='" + CASE_FILTER + "'); the suite contributes no test.");
        }
    }

    // ------------------------------------------------------------------ case filter

    private static boolean selected(String id, String baseId) {
        if (CASE_FILTER.isEmpty()) {
            return true;
        }
        return globMatches(CASE_FILTER, id) || globMatches(CASE_FILTER, baseId);
    }

    private static boolean globMatches(String glob, String value) {
        return value.matches(globToRegex(glob));
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (char character : glob.toCharArray()) {
            switch (character) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return regex.toString();
    }
}
