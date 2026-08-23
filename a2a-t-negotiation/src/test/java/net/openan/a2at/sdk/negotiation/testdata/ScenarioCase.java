package net.openan.a2at.sdk.negotiation.testdata;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One scenario record expanded for exactly one language.
 *
 * <p>A scenario is a multi-step multi-API interaction over the negotiation content layer. Every step is a full case
 * record (carried as its {@link ScenarioStep#caseData()}); the scenario adds the step ordering, the acting role and
 * the flow-level expectation.
 *
 * @param id expanded scenario id such as {@code SC-INFO-02/zh-CN}
 * @param baseId scenario record id before the language expansion, such as {@code SC-INFO-02}
 * @param sourceFile corpus file path relative to the corpus root, such as {@code scenarios/information-flows.json}
 * @param language language this expansion runs in
 * @param summary business-facing one-line summary, or null when the record states none
 * @param roles role names in their first-appearance order, empty when the record states none
 * @param steps scenario steps numbered consecutively from 1
 * @param expectFlow flow-level expectation, or null when the scenario states none
 * @since 2026-08
 */
public record ScenarioCase(
        String id,
        String baseId,
        String sourceFile,
        String language,
        @Nullable String summary,
        List<String> roles,
        List<ScenarioStep> steps,
        @Nullable ExpectFlow expectFlow) {

    public ScenarioCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(baseId, "baseId");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(steps, "steps");
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("The scenario must carry at least one step.");
        }
        roles = List.copyOf(roles);
        steps = List.copyOf(steps);
    }

    /**
     * One step of a scenario.
     *
     * @param step step number, consecutive from 1
     * @param role acting role name, or null when the step states none
     * @param caseData the step as a full expanded case record
     */
    public record ScenarioStep(int step, @Nullable String role, NegotiationCase caseData) {

        public ScenarioStep {
            if (step < 1) {
                throw new IllegalArgumentException("The step number must be at least 1.");
            }
            Objects.requireNonNull(caseData, "caseData");
        }
    }

    /**
     * The flow-level expectation of a scenario.
     *
     * @param terminalCondition expected terminal condition: accept, reject, abort or exhausted
     * @param roundsUsed expected largest round value reached across the steps
     * @param distinctMessages true when the per-round prompt texts must be pairwise distinct
     */
    public record ExpectFlow(@Nullable String terminalCondition, @Nullable Integer roundsUsed, @Nullable Boolean distinctMessages) {}
}
