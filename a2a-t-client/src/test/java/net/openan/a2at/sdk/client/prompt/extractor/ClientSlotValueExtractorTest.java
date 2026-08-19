package net.openan.a2at.sdk.client.prompt.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientSlotValueExtractorTest {

    @Test
    void extractSlotsWithSchemaDelegatesToExtractSlotsAndIgnoresSchema() {
        ClientSlotValueExtractor extractor = (userInput, scenarioCode, language, templateText) ->
                Map.of("scenario", scenarioCode, "language", language);

        Map<String, String> result = extractor.extractSlotsWithSchema(
                "test input", "scenario-1", "en-US", "template text", Map.of("field", "desc"));

        assertEquals(Map.of("scenario", "scenario-1", "language", "en-US"), result);
    }
}