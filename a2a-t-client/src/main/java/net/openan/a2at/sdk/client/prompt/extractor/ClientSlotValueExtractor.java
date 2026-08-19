package net.openan.a2at.sdk.client.prompt.extractor;

import java.util.Map;

/**
 * Extracts slot values for one scenario and language.
 *
 * @since 2026-06
 */
@FunctionalInterface
public interface ClientSlotValueExtractor {

    /**
     * Extracts normalized slot values for the supplied template context.
     *
     * @param userInput user-provided task description or structured input object
     * @param scenarioCode scenario code currently being rendered
     * @param language locale identifier of the backing resources
     * @param templateText resolved task template text
     * @return normalized slot values keyed by slot name
     */
    Map<String, String> extractSlots(Object userInput, String scenarioCode, String language, String templateText);

    /**
     * Extracts normalized slot values with an optional data schema for schema-guided extraction.
     *
     * @param userInput user-provided task description or structured input object
     * @param scenarioCode scenario code currently being rendered
     * @param language locale identifier of the backing resources
     * @param templateText resolved task template text
     * @param dataSchema optional data schema map (field name to description)
     * @return normalized slot values keyed by slot name
     */
    default Map<String, String> extractSlotsWithSchema(
            Object userInput, String scenarioCode, String language, String templateText,
            Map<String, Object> dataSchema) {
        return extractSlots(userInput, scenarioCode, language, templateText);
    }
}
