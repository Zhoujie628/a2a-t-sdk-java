package net.openan.a2at.sample.private_line_complaint.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.List;
import net.openan.a2at.sample.private_line_complaint.negotiation.evaluation.NegotiationEvaluationCase;
import net.openan.a2at.sample.private_line_complaint.negotiation.evaluation.NegotiationEvaluationCaseLoader;
import org.junit.jupiter.api.Test;

class NegotiationEvaluationCaseLoaderTest {

    @Test
    void loadsOneHundredManuallyLabelledCasesAcrossAllGenerationPhases() {
        var cases = NegotiationEvaluationCaseLoader.load();

        assertEquals(100, cases.size());
        assertEquals(34, cases.stream().filter(testCase -> testCase.phase().equals("propose")).count());
        assertEquals(33, cases.stream().filter(testCase -> testCase.phase().equals("accept")).count());
        assertEquals(33, cases.stream().filter(testCase -> testCase.phase().equals("reject")).count());
        assertTrue(cases.stream().allMatch(NegotiationEvaluationCaseLoaderTest::isComplete));
    }

    @Test
    void loadsFocusedReproductionCasesFromTheCanonicalCorpus() {
        var cases = NegotiationEvaluationCaseLoader.loadSelected(List.of("R21", "P01", "A28"));

        assertEquals(List.of("R21", "P01", "A28"), cases.stream().map(NegotiationEvaluationCase::id).toList());
    }

    @Test
    void loadsTwentyRepresentativeSmokeCasesFromTheCanonicalCorpus() {
        var cases = NegotiationEvaluationCaseLoader.loadSmoke();

        assertEquals(20, cases.size());
        assertEquals(NegotiationEvaluationCaseLoader.SMOKE_CASE_IDS,
                cases.stream().map(NegotiationEvaluationCase::id).toList());
        assertEquals(7, cases.stream().filter(testCase -> testCase.phase().equals("propose")).count());
        assertEquals(7, cases.stream().filter(testCase -> testCase.phase().equals("accept")).count());
        assertEquals(6, cases.stream().filter(testCase -> testCase.phase().equals("reject")).count());
    }

    @Test
    void assemblesEveryCorpusEntryIntoAnEndToEndFlow() {
        var flows = NegotiationEvaluationCaseLoader.loadFlows();

        assertEquals(100, flows.size());
        assertEquals(50, flows.stream().filter(flow -> flow.decision().equals("accept")).count());
        assertEquals(50, flows.stream().filter(flow -> flow.decision().equals("reject")).count());
        assertTrue(flows.stream().allMatch(flow -> flow.proposeCase().phase().equals("propose")));
        assertTrue(flows.stream().allMatch(flow -> flow.endingCase().phase().equals(flow.decision())));
        assertTrue(flows.stream().allMatch(flow -> flow.clientSupplement("context", 1, 3)
                .contains("## 客户端补充信息")));
    }

    @Test
    void assemblesTwentySmokeFlowsUsingTheExistingSelectorIds() {
        var flows = NegotiationEvaluationCaseLoader.loadSmokeFlows();

        assertEquals(20, flows.size());
        assertEquals(NegotiationEvaluationCaseLoader.SMOKE_CASE_IDS,
                flows.stream().map(flow -> flow.id()).toList());
        assertTrue(flows.stream().anyMatch(flow -> flow.decision().equals("accept")));
        assertTrue(flows.stream().anyMatch(flow -> flow.decision().equals("reject")));
    }

    @Test
    void rendersCompletedPromptsWithTheRuntimeNegotiationContext() {
        var cases = NegotiationEvaluationCaseLoader.load();

        for (NegotiationEvaluationCase testCase : cases) {
            String prompt = testCase.renderCompletedPrompt("case-context", 2, 4);
            assertTrue(prompt.contains("- id: case-context"), testCase.id());
            assertTrue(prompt.contains("- round: 2"), testCase.id());
            assertTrue(prompt.contains("- maxRounds: 4"), testCase.id());
            assertTrue(!prompt.contains("{{"), testCase.id());
            assertTrue(prompt.contains(testCase.phase().equals("propose")
                    ? "## 信息协商\n"
                    : "## 信息协商结果\n"), testCase.id());
        }
    }

    private static boolean isComplete(NegotiationEvaluationCase testCase) {
        return !testCase.id().isBlank()
                && !testCase.category().isBlank()
                && !testCase.text().isBlank()
                && testCase.completedPrompt() != null
                && !testCase.completedPrompt().isBlank()
                && testCase.completedPrompt().contains("{{id}}")
                && testCase.expected() != null
                && !testCase.expected().isEmpty()
                && expectedKeys(testCase.phase()).equals(testCase.expected().keySet());
    }

    private static java.util.Set<String> expectedKeys(String phase) {
        return phase.equals("reject")
                ? Map.of("rejection_reason", "").keySet()
                : Map.of("access_port_name", "", "complaint_category", "").keySet();
    }
}
