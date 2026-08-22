package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import java.util.Map;

/** One manually labelled natural-language evaluation case. */
public record NegotiationEvaluationCase(
        String id,
        String phase,
        String category,
        String text,
        String completedPrompt,
        Map<String, Object> expected) {
}
