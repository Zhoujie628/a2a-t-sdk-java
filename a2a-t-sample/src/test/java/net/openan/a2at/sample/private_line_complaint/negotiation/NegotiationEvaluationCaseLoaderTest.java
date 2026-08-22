package net.openan.a2at.sample.private_line_complaint.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
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

    private static boolean isComplete(NegotiationEvaluationCase testCase) {
        return !testCase.id().isBlank()
                && !testCase.category().isBlank()
                && !testCase.text().isBlank()
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
