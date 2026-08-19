package net.openan.a2at.sdk.client.prompt.extractor;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.client.prompt.loader.ClientSlotSchemaLoader;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.analysis.impl.DefaultStructuredPromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;

/**
 * LLM-backed default slot extractor that requests structured slot payloads.
 *
 * @since 2026-06
 */
public final class DefaultStructuredClientSlotValueExtractor implements ClientSlotValueExtractor {

    private final DefaultStructuredPromptSlotValueExtractor delegate;

    /**
     * Creates an LLM-backed slot extractor.
     *
     * @param llmClient LLM client used for slot extraction
     * @param slotSchemaLoader slot schema loader
     * @param systemPrompt system prompt for extraction
     * @param userPrompt user prompt for extraction
     */
    public DefaultStructuredClientSlotValueExtractor(
            LLMClient llmClient, ClientSlotSchemaLoader slotSchemaLoader, String systemPrompt, String userPrompt) {
        this.delegate = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> slotSchemaLoader.loadSlotSchema(scenarioCode, language),
                systemPrompt,
                userPrompt);
    }

    @Override
    public Map<String, String> extractSlots(
            Object userInput, String scenarioCode, String language, String templateText) {
        StructuredSlotExtractionResult result = delegate.extractSlots(userInput, scenarioCode, language);
        return new LinkedHashMap<>(result.slots());
    }

    @Override
    public Map<String, String> extractSlotsWithSchema(
            Object userInput, String scenarioCode, String language, String templateText,
            Map<String, Object> dataSchema) {
        StructuredSlotExtractionResult result = delegate.extractSlots(userInput, scenarioCode, language, dataSchema);
        return new LinkedHashMap<>(result.slots());
    }
}
