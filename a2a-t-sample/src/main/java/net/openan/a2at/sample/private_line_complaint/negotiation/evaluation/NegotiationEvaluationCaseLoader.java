package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Loads the checked-in, manually labelled Qwen evaluation corpus. */
public final class NegotiationEvaluationCaseLoader {

    public static final String RESOURCE_PATH = "sample/private-line-complaint-negotiation/evaluation/cases.json";
    /**
     * A representative 20-case set for routine smoke validation. It includes every generation and
     * validation/filling phase, and spans common, short, contextual, mixed-language, noisy, and
     * business-oriented expressions.
     */
    public static final List<String> SMOKE_CASE_IDS = List.of(
            "P01", "P05", "P11", "P14", "P16", "P21", "P27",
            "A01", "A05", "A11", "A16", "A21", "A28", "A33",
            "R01", "R05", "R11", "R16", "R21", "R27");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private NegotiationEvaluationCaseLoader() {
    }

    public static List<NegotiationEvaluationCase> load() {
        try (InputStream input = NegotiationEvaluationCaseLoader.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Negotiation evaluation corpus not found: " + RESOURCE_PATH);
            }
            List<NegotiationEvaluationCase> cases = OBJECT_MAPPER.readValue(input, new TypeReference<>() {
            });
            if (cases.size() != 100) {
                throw new IllegalStateException("Negotiation evaluation corpus must contain exactly 100 cases");
            }
            return List.copyOf(cases);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read negotiation evaluation corpus", exception);
        }
    }

    /**
     * Loads a named subset from the checked-in corpus for focused problem reproduction.
     *
     * @param caseIds case identifiers, in desired execution order
     * @return selected cases
     */
    public static List<NegotiationEvaluationCase> loadSelected(List<String> caseIds) {
        if (caseIds.isEmpty()) {
            throw new IllegalArgumentException("At least one evaluation case ID is required");
        }
        Set<String> requested = new LinkedHashSet<>(caseIds);
        if (requested.size() != caseIds.size()) {
            throw new IllegalArgumentException("Duplicate negotiation evaluation case IDs are not allowed: " + caseIds);
        }
        var casesById = load().stream().collect(java.util.stream.Collectors.toMap(
                NegotiationEvaluationCase::id,
                testCase -> testCase));
        Set<String> unknown = new LinkedHashSet<>(requested);
        unknown.removeAll(casesById.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown negotiation evaluation case IDs: " + unknown);
        }
        return caseIds.stream().map(casesById::get).toList();
    }

    /** Loads the checked-in 20-case smoke set. */
    public static List<NegotiationEvaluationCase> loadSmoke() {
        return loadSelected(SMOKE_CASE_IDS);
    }
}
