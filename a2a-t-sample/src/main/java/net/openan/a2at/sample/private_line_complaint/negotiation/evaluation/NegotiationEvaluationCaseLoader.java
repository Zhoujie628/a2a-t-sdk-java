package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads the checked-in, manually labelled Qwen evaluation corpus. */
public final class NegotiationEvaluationCaseLoader {

    public static final String RESOURCE_PATH = "sample/private-line-complaint-negotiation/evaluation/cases.json";

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
}
