package net.openan.a2at.sample.negotiation.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Scenario data for the negotiation demo, loaded from the bundled {@code sample/negotiation/scenario.json}.
 *
 * <p>The scenario file carries the Task-T slot schema and the two parameter maps driving the 4-message flow: one with a
 * missing required param (triggering the server-side information negotiation) and one with the params filled in
 * (completing the negotiation). Keeping the data in a resource file — the same pattern as the subscribe-incident
 * sample's {@code scenario.json} — means the demo scenario changes without touching Java code: edit the JSON (or point
 * the loader at another file with the same shape) and the whole flow, from slot extraction to the diagnosis text,
 * adapts to the new inputs.
 *
 * <p>The slot schema mirrors {@code slots/Task-T/network-layer/private-line-complaint/.../slot.json}; when the bundled
 * schema changes, copy the updated schema into this scenario file.
 *
 * @since 2026-08
 */
public final class ScenarioData {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SCENARIO_RESOURCE = "sample/negotiation/scenario.json";

    private ScenarioData() {}

    /** Slot schema describing the Task-T parameters (passed to validateAndFillingTaskData). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> taskSchema() {
        return (Map<String, Object>) scenarioMap().get("task_schema");
    }

    /**
     * Scenario data with a missing required param: the {@code 任务对象} slot is empty, which triggers the server-side
     * information negotiation.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> missingParams() {
        return (Map<String, Object>) scenarioMap().get("missing_params");
    }

    /** Scenario data with all params filled in, completing the negotiation so the server can run the diagnosis. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> filledParams() {
        return (Map<String, Object>) scenarioMap().get("filled_params");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> scenarioMap() {
        try (InputStream stream = ScenarioData.class.getClassLoader().getResourceAsStream(SCENARIO_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Scenario resource not found: " + SCENARIO_RESOURCE);
            }
            Map<String, Object> scenario = MAPPER.readValue(stream, new TypeReference<Map<String, Object>>() {});
            return scenario == null ? Map.of() : scenario;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read scenario resource: " + SCENARIO_RESOURCE, exception);
        }
    }
}
