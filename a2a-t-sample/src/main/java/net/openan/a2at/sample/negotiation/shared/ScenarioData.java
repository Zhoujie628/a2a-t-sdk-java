package net.openan.a2at.sample.negotiation.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;

/**
 * Scenario configuration for the negotiation demo, loaded from the bundled {@code sample/negotiation/scenario.json}.
 *
 * <p>The Java code of the sample contains only generic assembly rules (how to iterate slots, how to merge metadata);
 * every scenario-specific value lives in the JSON file: the Task-T slot schema, the missing/filled parameter maps
 * driving the 4-message flow, the negotiation phrasing templates ({@code {slot}} / {@code {description}} /
 * {@code {params}} placeholders) and the 3x3 API-sample case data. Changing the scenario means editing the JSON — no
 * Java recompilation — and the whole flow, from slot extraction to the diagnosis text, adapts to the new inputs.
 *
 * @since 2026-08
 */
public final class ScenarioData {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SCENARIO_RESOURCE = "sample/negotiation/scenario.json";

    private ScenarioData() {}

    /** Slot schema describing the Task-T parameters (passed to validateTaskPromptAndDataFilling). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> taskSchema() {
        return (Map<String, Object>) scenarioMap().get("task_schema");
    }

    /** Scenario data with a missing required param, triggering the server-side information negotiation. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> missingParams() {
        return (Map<String, Object>) scenarioMap().get("missing_params");
    }

    /** Scenario data with all params filled in, completing the negotiation so the server can run the diagnosis. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> filledParams() {
        return (Map<String, Object>) scenarioMap().get("filled_params");
    }

    /** Negotiation phrasing templates: {@code missing_item_hint}, {@code propose_relationship}, from-text prefixes. */
    @SuppressWarnings("unchecked")
    public static Map<String, String> negotiationPhrasing() {
        return (Map<String, String>) scenarioMap().get("negotiation_phrasing");
    }

    /** Diagnosis result templates: {@code result_line}, {@code detail_line}, {@code advice_line}. */
    @SuppressWarnings("unchecked")
    public static Map<String, String> diagnosisTemplates() {
        return (Map<String, String>) scenarioMap().get("diagnosis");
    }

    /** The 3x3 API-sample case data keyed by negotiation type (information/target/feasibility). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> negotiationCases() {
        return (Map<String, Object>) scenarioMap().get("negotiation_cases");
    }

    /** The fromText natural-language case texts keyed by negotiation type. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromTextCases() {
        Object cases = negotiationCases().get("from_text_cases");
        return cases instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /** The fromData case section of one negotiation type. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> caseSection(String negotiationType) {
        Object section = negotiationCases().get(negotiationType);
        return section instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /**
     * Reads one item list ({@code name}/{@code value} entries) from a case section; {@code {filled:<slot>}} values are
     * substituted with the filled scenario parameter of that slot.
     */
    public static List<NegotiationItem> items(Map<String, Object> section, String key) {
        Object raw = section.get(key);
        if (!(raw instanceof List<?> entries)) {
            return List.of();
        }
        return entries.stream()
                .filter(Map.class::isInstance)
                .map(entry -> {
                    Map<?, ?> item = (Map<?, ?>) entry;
                    return new NegotiationItem(
                            String.valueOf(item.get("name")), resolveValue(String.valueOf(item.get("value"))));
                })
                .toList();
    }

    /** Reads one string field from a case section, applying {@code {filled:<slot>}} substitution. */
    public static String text(Map<String, Object> section, String key) {
        Object value = section.get(key);
        return value == null ? null : resolveValue(String.valueOf(value));
    }

    /** Reads one nested map field from a case section. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> section, String key) {
        Object value = section.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /** Substitutes {@code {filled:<slot>}} markers with the filled scenario parameter of that slot. */
    public static String resolveValue(String template) {
        if (template == null) {
            return null;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : filledParams().entrySet()) {
            String marker = "{filled:" + entry.getKey() + "}";
            if (result.contains(marker)) {
                result = result.replace(marker, String.valueOf(entry.getValue()));
            }
        }
        return result;
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
