package net.openan.a2at.sdk.prompt.analysis.impl;

import java.util.Map;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;

/**
 * Shared slot extractor contract used by both client and server prompt-analysis flows.
 *
 * @since 2026-06
 */
@FunctionalInterface
public interface PromptSlotValueExtractor {

    /**
     * Extracts structured slot values from one normalized input string.
     *
     * @param userInput normalized input text
     * @param scenarioCode resolved scenario code
     * @param language prompt language
     * @return structured extraction result
     */
    StructuredSlotExtractionResult extractSlots(Object userInput, String scenarioCode, String language);

    /**
     * Extracts structured slot values with an optional data schema for schema-guided extraction.
     *
     * @param userInput normalized input text
     * @param scenarioCode resolved scenario code
     * @param language prompt language
     * @param dataSchema optional data schema map (field name to description)
     * @return structured extraction result
     */
    default StructuredSlotExtractionResult extractSlots(
            Object userInput, String scenarioCode, String language, Map<String, Object> dataSchema) {
        return extractSlots(userInput, scenarioCode, language);
    }
}
