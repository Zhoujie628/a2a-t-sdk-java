package net.openan.a2at.sdk.negotiation.testdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.openan.a2at.sdk.core.model.MetadataContent;
import org.jspecify.annotations.Nullable;

/**
 * Executes one expanded corpus scenario: a multi-step, multi-API interaction over the negotiation content layer.
 *
 * <p>The scenario engine is deliberately thin (design document §3.2.1, Q11–Q13): every step is a full corpus case
 * executed by the {@link CaseEngine} with its own scripted LLM behavior and its exact per-step {@code llmCalls}
 * expectation. The scenario layer only adds what a step cannot express alone:
 *
 * <ul>
 * <li>{@code prompt.fromStep} resolution — the prompt text an earlier generation step produced becomes the prompt input
 * of a later validation step;
 * <li>fail-fast — the first step failure aborts the whole scenario, later steps never run;
 * <li>per-role LLM accounting — every step runs on its own scripted client, so the roles' counters stay independent by
 * construction and the totals are tracked per role;
 * <li>the flow-level expectation {@code expectFlow}: the terminal condition (the last generated message carries the
 * accept/reject/abort literal, or the round limit was reached for {@code exhausted}), the largest round value reached,
 * and the pairwise distinctness of the generated messages.
 * </li>
 *
 * @since 2026-08
 */
public final class ScenarioEngine {

    private final CaseEngine caseEngine = new CaseEngine();

    /**
     * Runs one expanded scenario step by step and asserts its flow-level expectation.
     *
     * @param scenario expanded corpus scenario
     * @throws AssertionError when a step fails (fail-fast: later steps do not run) or the flow expectation mismatches
     */
    public void runScenario(ScenarioCase scenario) {
        Map<Integer, String> stepPromptTexts = new LinkedHashMap<>();
        Map<String, Integer> roleCallCounts = new LinkedHashMap<>();
        int maxRound = 0;
        int maxRoundsLimit = 0;
        String lastPromptText = null;
        for (ScenarioCase.ScenarioStep step : scenario.steps()) {
            NegotiationCase stepCase = step.caseData();
            String promptOverride = resolvePromptOverride(scenario, stepCase, stepPromptTexts);
            CaseEngine.CaseOutcome outcome = caseEngine.run(stepCase, promptOverride);
            MetadataContent message = outcome.message();
            if (message != null) {
                stepPromptTexts.put(step.step(), message.promptText());
                lastPromptText = message.promptText();
            }
            roleCallCounts.merge(step.role() == null ? "(no role)" : step.role(), outcome.llmCalls(), Integer::sum);
            if (stepCase.context() != null) {
                maxRound = Math.max(maxRound, stepCase.context().round());
                maxRoundsLimit = Math.max(maxRoundsLimit, stepCase.context().maxRounds());
            }
        }
        assertExpectFlow(scenario, stepPromptTexts, maxRound, maxRoundsLimit, lastPromptText);
    }

    // ------------------------------------------------------------------ fromStep resolution

    private static String resolvePromptOverride(
            ScenarioCase scenario, NegotiationCase stepCase, Map<Integer, String> stepPromptTexts) {
        if (!(stepCase.prompt() instanceof PromptSource.FromStep fromStep)) {
            return null;
        }
        String promptText = stepPromptTexts.get(fromStep.step());
        if (promptText == null) {
            throw new AssertionError(
                    errorPrefix(scenario) + " prompt.fromStep " + fromStep.step()
                            + ": the referenced step produced no prompt text (unknown step number, a step that has not"
                            + " run yet, or a non-generation API)");
        }
        return promptText;
    }

    // ------------------------------------------------------------------ flow-level expectation

    private void assertExpectFlow(
            ScenarioCase scenario,
            Map<Integer, String> stepPromptTexts,
            int maxRound,
            int maxRoundsLimit,
            @Nullable String lastPromptText) {
        ScenarioCase.ExpectFlow flow = scenario.expectFlow();
        if (flow == null) {
            return;
        }
        if (flow.terminalCondition() != null) {
            switch (flow.terminalCondition()) {
                case "accept", "reject", "abort" -> {
                    String literal = terminalLiteral(flow.terminalCondition());
                    if (lastPromptText == null) {
                        fail(
                                scenario,
                                "$.expectFlow.terminalCondition",
                                "a generated message carrying the '" + literal + "' literal",
                                "no generation step succeeded");
                    }
                    if (!lastPromptText.contains(literal)) {
                        fail(
                                scenario,
                                "$.expectFlow.terminalCondition",
                                "a final message containing '" + literal + "'",
                                "<" + lastPromptText + ">");
                    }
                }
                case "exhausted" -> {
                    if (maxRound != maxRoundsLimit || maxRoundsLimit == 0) {
                        fail(
                                scenario,
                                "$.expectFlow.terminalCondition",
                                "the round limit reached (largest round equals maxRounds " + maxRoundsLimit + ")",
                                "largest round " + maxRound + ", maxRounds " + maxRoundsLimit);
                    }
                }
                default -> fail(
                        scenario,
                        "$.expectFlow.terminalCondition",
                        "accept, reject, abort or exhausted",
                        flow.terminalCondition());
            }
        }
        if (flow.roundsUsed() != null && flow.roundsUsed() != maxRound) {
            fail(
                    scenario,
                    "$.expectFlow.roundsUsed",
                    String.valueOf(flow.roundsUsed()),
                    String.valueOf(maxRound));
        }
        if (Boolean.TRUE.equals(flow.distinctMessages())) {
            List<String> messages = new ArrayList<>(stepPromptTexts.values());
            Set<String> distinct = new LinkedHashSet<>(messages);
            if (distinct.size() != messages.size()) {
                fail(
                        scenario,
                        "$.expectFlow.distinctMessages",
                        messages.size() + " pairwise distinct generated messages",
                        distinct.size() + " distinct message(s)");
            }
        }
    }

    private static String terminalLiteral(String terminalCondition) {
        return switch (terminalCondition) {
            case "accept" -> "Accept";
            case "reject" -> "Reject";
            case "abort" -> "Abort";
            default -> throw new IllegalArgumentException("Unknown terminal condition " + terminalCondition + ".");
        };
    }

    // ------------------------------------------------------------------ helpers

    private static String errorPrefix(ScenarioCase scenario) {
        return scenario.sourceFile() + " [" + scenario.id() + "]";
    }

    private static AssertionError fail(ScenarioCase scenario, String jsonPath, String expected, String actual) {
        throw new AssertionError(
                errorPrefix(scenario) + " " + jsonPath + ": expected " + expected + " but was " + actual);
    }
}
