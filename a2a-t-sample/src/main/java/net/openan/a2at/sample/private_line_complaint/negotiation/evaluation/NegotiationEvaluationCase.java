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

    public String renderCompletedPrompt(String contextId, int round, int maxRounds) {
        return completedPrompt
                .replace("{{id}}", contextId)
                .replace("{{round}}", Integer.toString(round))
                .replace("{{maxRounds}}", Integer.toString(maxRounds));
    }
}
