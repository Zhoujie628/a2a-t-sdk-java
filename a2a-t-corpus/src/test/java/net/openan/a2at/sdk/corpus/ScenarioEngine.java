package net.openan.a2at.sdk.negotiation.testdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import org.jspecify.annotations.Nullable;

/**
 * Executes one expanded corpus scenario: a multi-step, multi-API interaction over the negotiation content layer and
 * the closed-loop task APIs.
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
 * the pairwise distinctness of the generated messages, and {@code missingParamsFilled} — the closed-loop causal chain
 * of Q21: the parameters a task-validation step found missing must carry values by the end of the flow;
 * <li>the step-level causal expectation {@code expect.paramsFromStep}: the parameters this validation step extracted
 * must fill the missing-parameter set an earlier task-validation step discovered — the {@code 缺参 → 补参} link;
 * <li>role semantics (Q23): every step records its acting role, the failure messages carry the role with its business
 * description, and a successful run prints one flow summary line with the role semantics.
 * </ul>
 *
 * @since 2026-08
 */
public final class ScenarioEngine {

    private final CaseEngine caseEngine = new CaseEngine();

    /**
     * Runs one expanded scenario step by step and asserts its flow-level expectation.
     *
     * @param scenario expanded corpus scenario
     * @throws AssertionError when a step fails (fail-fast: later steps do not run) or a flow expectation mismatches
     */
    public void runScenario(ScenarioCase scenario) {
        Map<Integer, String> stepPromptTexts = new LinkedHashMap<>();
        Map<Integer, Set<String>> stepMissingParams = new LinkedHashMap<>();
        Map<Integer, Map<String, Object>> stepFilledParams = new LinkedHashMap<>();
        Map<String, Integer> roleCallCounts = new LinkedHashMap<>();
        int maxRound = 0;
        int maxRoundsLimit = 0;
        String lastPromptText = null;
        for (ScenarioCase.ScenarioStep step : scenario.steps()) {
            NegotiationCase stepCase = step.caseData();
            String promptOverride = resolvePromptOverride(scenario, stepCase, stepPromptTexts);
            CaseEngine.CaseOutcome outcome = caseEngine.run(stepCase, promptOverride, true);
            MetadataContent message = outcome.message();
            if (message != null) {
                stepPromptTexts.put(step.step(), message.promptText());
                lastPromptText = message.promptText();
            }
            FilledParamData filled = outcome.filledParams();
            if (filled != null) {
                stepFilledParams.put(step.step(), filled.data());
                stepMissingParams.put(step.step(), missingParamsOf(filled));
            }
            roleCallCounts.merge(step.role() == null ? "(no role)" : step.role(), outcome.llmCalls(), Integer::sum);
            if (stepCase.context() != null) {
                maxRound = Math.max(maxRound, stepCase.context().round());
                maxRoundsLimit = Math.max(maxRoundsLimit, stepCase.context().maxRounds());
            }
            if (stepCase.expect().paramsFromStep() != null) {
                assertParamsFromStep(scenario, step, stepMissingParams, stepFilledParams);
            }
        }
        assertExpectFlow(scenario, stepPromptTexts, stepMissingParams, stepFilledParams, maxRound, maxRoundsLimit,
                lastPromptText);
        printSummary(scenario, stepMissingParams, stepFilledParams);
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

    // ------------------------------------------------------------------ 缺参 -> 补参 causal chain (Q21)

    /**
     * Returns the missing-parameter set of one validation outcome: the names whose value in the filled parameter data
     * is null — the semantic validator's explicit missing-slot signal downstream negotiation triggering relies on.
     */
    private static Set<String> missingParamsOf(FilledParamData filled) {
        Set<String> missing = new LinkedHashSet<>();
        filled.data().forEach((name, value) -> {
            if (value == null) {
                missing.add(name);
            }
        });
        return missing;
    }

    /**
     * Asserts the step-level causal expectation {@code expect.paramsFromStep N} of one step: the referenced step N
     * must have discovered a non-empty missing-parameter set, and every one of those parameters must carry a value in
     * this step's filled parameter data.
     */
    private static void assertParamsFromStep(
            ScenarioCase scenario,
            ScenarioCase.ScenarioStep step,
            Map<Integer, Set<String>> stepMissingParams,
            Map<Integer, Map<String, Object>> stepFilledParams) {
        int fromStep = step.caseData().expect().paramsFromStep();
        Set<String> missing = stepMissingParams.get(fromStep);
        if (missing == null) {
            throw new AssertionError(stepErrorPrefix(scenario, step) + " expect.paramsFromStep " + fromStep
                    + ": the referenced step produced no filled parameter data (unknown step number, a step that has"
                    + " not run yet, or a non-validation API)");
        }
        if (missing.isEmpty()) {
            throw new AssertionError(stepErrorPrefix(scenario, step) + " expect.paramsFromStep " + fromStep
                    + ": the referenced step found no missing parameters, so there is nothing this step could fill");
        }
        Map<String, Object> filled = stepFilledParams.get(step.step());
        List<String> unfilled = new ArrayList<>();
        for (String name : missing) {
            if (filled == null || filled.get(name) == null) {
                unfilled.add(name);
            }
        }
        if (!unfilled.isEmpty()) {
            throw new AssertionError(stepErrorPrefix(scenario, step) + " expect.paramsFromStep " + fromStep
                    + ": the parameters extracted by this step do not fill the missing parameters of step " + fromStep
                    + "; still missing: " + String.join(", ", unfilled));
        }
    }

    // ------------------------------------------------------------------ flow-level expectation

    private void assertExpectFlow(
            ScenarioCase scenario,
            Map<Integer, String> stepPromptTexts,
            Map<Integer, Set<String>> stepMissingParams,
            Map<Integer, Map<String, Object>> stepFilledParams,
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
        if (flow.missingParamsFilled() != null) {
            assertMissingParamsFilled(scenario, flow.missingParamsFilled(), stepMissingParams, stepFilledParams);
        }
    }

    /**
     * Asserts the flow-level causal expectation {@code expectFlow.missingParamsFilled N} (Q21): step N must have
     * discovered a non-empty missing-parameter set, and the union of the filled parameter data of the later steps —
     * later steps winning on key conflicts — must carry a value for every one of them.
     */
    private static void assertMissingParamsFilled(
            ScenarioCase scenario,
            int missingStep,
            Map<Integer, Set<String>> stepMissingParams,
            Map<Integer, Map<String, Object>> stepFilledParams) {
        Set<String> missing = stepMissingParams.get(missingStep);
        if (missing == null) {
            fail(
                    scenario,
                    "$.expectFlow.missingParamsFilled",
                    "a task-validation step " + missingStep + " carrying a missing-parameter set",
                    "step " + missingStep + " produced no filled parameter data");
        }
        if (missing.isEmpty()) {
            fail(
                    scenario,
                    "$.expectFlow.missingParamsFilled",
                    "a non-empty missing-parameter set at step " + missingStep,
                    "step " + missingStep + " found no missing parameters");
        }
        Map<String, Object> filledUnion = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<String, Object>> entry : new TreeMap<>(stepFilledParams).entrySet()) {
            if (entry.getKey() > missingStep) {
                filledUnion.putAll(entry.getValue());
            }
        }
        List<String> unfilled = new ArrayList<>();
        for (String name : missing) {
            if (filledUnion.get(name) == null) {
                unfilled.add(name);
            }
        }
        if (!unfilled.isEmpty()) {
            fail(
                    scenario,
                    "$.expectFlow.missingParamsFilled",
                    "the missing parameters of step " + missingStep + " (" + String.join(", ", missing)
                            + ") filled by the later steps",
                    "still missing after step " + missingStep + ": " + String.join(", ", unfilled));
        }
    }

    // ------------------------------------------------------------------ role-semantic run summary (Q23)

    /**
     * Prints the one-line flow summary of a successful scenario run, carrying the role semantics and — when the
     * scenario asserts the causal chain — the missing-to-filled parameter link.
     */
    private static void printSummary(
            ScenarioCase scenario,
            Map<Integer, Set<String>> stepMissingParams,
            Map<Integer, Map<String, Object>> stepFilledParams) {
        StringBuilder summary = new StringBuilder("[scenario] ")
                .append(scenario.id())
                .append(" completed ")
                .append(scenario.steps().size())
                .append(" step(s)");
        String roles = scenario.describeRoles();
        if (!roles.isEmpty()) {
            summary.append("; ").append(roles);
        }
        stepMissingParams.forEach((step, missing) -> {
            if (missing.isEmpty()) {
                return;
            }
            summary.append("; step-").append(step).append(" missing params: ").append(String.join(", ", missing));
            for (int later : new TreeMap<>(stepFilledParams).keySet()) {
                if (later <= step) {
                    continue;
                }
                List<String> filledHere = new ArrayList<>();
                for (String name : missing) {
                    Object value = stepFilledParams.get(later).get(name);
                    if (value != null) {
                        filledHere.add(name + "=" + value);
                    }
                }
                if (!filledHere.isEmpty()) {
                    summary.append(" -> filled at step-").append(later).append(": ")
                            .append(String.join(", ", filledHere));
                }
            }
        });
        System.out.println(summary);
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

    /**
     * Returns the failure-message prefix of one step, carrying the step number and the role semantics of the acting
     * role (Q23), such as {@code scenarios/x.json [SC-1/zh-CN#step-5 (B=OMC（server，执行/要数方）)]}.
     */
    private static String stepErrorPrefix(ScenarioCase scenario, ScenarioCase.ScenarioStep step) {
        return scenario.sourceFile() + " [" + scenario.id() + "#step-" + step.step() + " ("
                + scenario.describeRole(step.role()) + ")]";
    }

    private static AssertionError fail(ScenarioCase scenario, String jsonPath, String expected, String actual) {
        throw new AssertionError(
                errorPrefix(scenario) + " " + jsonPath + ": expected " + expected + " but was " + actual);
    }
}
