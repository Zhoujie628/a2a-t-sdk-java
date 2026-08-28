package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import java.util.Map;

/** One end-to-end negotiation evaluation flow assembled from the labelled generation corpus. */
public record NegotiationEvaluationFlowCase(
        String id,
        String category,
        String decision,
        NegotiationEvaluationCase proposeCase,
        NegotiationEvaluationCase endingCase) {

    public Map<String, Object> expectedPropose() {
        return proposeCase.expected();
    }

    public Map<String, Object> expectedEnding() {
        return endingCase.expected();
    }
}
