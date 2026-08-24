package net.openan.a2at.sdk.negotiation.testdata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One scenario record expanded for exactly one language.
 *
 * <p>A scenario is a multi-step multi-API interaction over the content layer. Every step is a full case record
 * (carried as its {@link ScenarioStep#caseData()}); the scenario adds the step ordering, the acting role, the role
 * descriptions of the closed loop (Q23: {@code A=工作台 client} / {@code B=OMC server}) and the flow-level expectation.
 *
 * @param id expanded scenario id such as {@code SC-INFO-02/zh-CN}
 * @param baseId scenario record id before the language expansion, such as {@code SC-INFO-02}
 * @param sourceFile corpus file path relative to the corpus root, such as {@code scenarios/information-flows.json}
 * @param language language this expansion runs in
 * @param summary business-facing one-line summary, or null when the record states none
 * @param roles role names in their first-appearance order, empty when the record states none
 * @param steps scenario steps numbered consecutively from 1
 * @param expectFlow flow-level expectation, or null when the scenario states none
 * @param rolesDesc business meaning of each role, keyed by role name, empty when the record states none
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
        @Nullable ExpectFlow expectFlow,
        Map<String, String> rolesDesc) {

    /**
     * Creates a scenario without role descriptions.
     *
     * @param id expanded scenario id such as {@code SC-INFO-02/zh-CN}
     * @param baseId scenario record id before the language expansion
     * @param sourceFile corpus file path relative to the corpus root
     * @param language language this expansion runs in
     * @param summary business-facing one-line summary, or null
     * @param roles role names in their first-appearance order
     * @param steps scenario steps numbered consecutively from 1
     * @param expectFlow flow-level expectation, or null
     */
    public ScenarioCase(
            String id,
            String baseId,
            String sourceFile,
            String language,
            @Nullable String summary,
            List<String> roles,
            List<ScenarioStep> steps,
            @Nullable ExpectFlow expectFlow) {
        this(id, baseId, sourceFile, language, summary, roles, steps, expectFlow, Map.of());
    }

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
        rolesDesc = Map.copyOf(rolesDesc);
    }

    /**
     * Returns the role semantics rendering of one role for failure messages and the run summary, such as
     * {@code B=OMC（server，执行/要数方，协商发起方）}.
     *
     * @param role role name, or null for a step without one
     * @return role with its description when the scenario declares one, the bare role otherwise
     */
    public String describeRole(@Nullable String role) {
        if (role == null) {
            return "(no role)";
        }
        String description = rolesDesc.get(role);
        return description == null ? role : role + "=" + description;
    }

    /**
     * Returns the role semantics line of the whole scenario, such as
     * {@code roles: A=工作台（client，任务发起/补数方）, B=OMC（server，执行/要数方，协商发起方）}.
     *
     * @return role semantics of every declared role, or an empty string when the scenario declares none
     */
    public String describeRoles() {
        if (roles.isEmpty() && rolesDesc.isEmpty()) {
            return "";
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String role : roles) {
            ordered.put(role, rolesDesc.getOrDefault(role, ""));
        }
        rolesDesc.forEach((role, description) -> ordered.putIfAbsent(role, ""));
        StringBuilder rendered = new StringBuilder("roles: ");
        boolean first = true;
        for (Map.Entry<String, String> entry : ordered.entrySet()) {
            if (!first) {
                rendered.append(", ");
            }
            first = false;
            rendered.append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                rendered.append('=').append(entry.getValue());
            }
        }
        return rendered.toString();
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
     * @param missingParamsFilled number of the task-validation step whose missing parameters must be filled with
     *     values by the later steps of the flow (Q21: the {@code 缺参 → 补参} causal chain), or null when not asserted
     */
    public record ExpectFlow(
            @Nullable String terminalCondition,
            @Nullable Integer roundsUsed,
            @Nullable Boolean distinctMessages,
            @Nullable Integer missingParamsFilled) {

        /**
         * Creates a flow expectation without the missing-parameter causal assertion.
         *
         * @param terminalCondition expected terminal condition: accept, reject, abort or exhausted
         * @param roundsUsed expected largest round value reached across the steps
         * @param distinctMessages true when the per-round prompt texts must be pairwise distinct
         */
        public ExpectFlow(
                @Nullable String terminalCondition, @Nullable Integer roundsUsed, @Nullable Boolean distinctMessages) {
            this(terminalCondition, roundsUsed, distinctMessages, null);
        }
    }
}
