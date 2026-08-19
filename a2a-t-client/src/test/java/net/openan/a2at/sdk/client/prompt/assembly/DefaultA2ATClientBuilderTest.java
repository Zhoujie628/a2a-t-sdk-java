package net.openan.a2at.sdk.client.prompt.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import java.util.Map;
import net.openan.a2at.sdk.client.prompt.extractor.ClientSlotValueExtractor;
import org.junit.jupiter.api.Test;

class DefaultA2ATClientBuilderTest {

    @Test
    void structuredInputAwareExtractSlotsWithSchemaDelegatesToLlmExtractorForMapInput() throws Exception {
        ClientSlotValueExtractor structuredExtractor =
                (userInput, scenarioCode, language, templateText) -> Map.of("key", "structured");
        ClientSlotValueExtractor llmExtractor =
                (userInput, scenarioCode, language, templateText) -> Map.of("key", "llm");

        Class<?> innerClass = Class.forName(
                "net.openan.a2at.sdk.client.prompt.assembly.DefaultA2ATClientBuilder$StructuredInputAwareSlotValueExtractor");
        Constructor<?> constructor = innerClass.getDeclaredConstructor(
                ClientSlotValueExtractor.class, ClientSlotValueExtractor.class);
        constructor.setAccessible(true);
        ClientSlotValueExtractor extractor =
                (ClientSlotValueExtractor) constructor.newInstance(structuredExtractor, llmExtractor);

        Map<String, String> result = extractor.extractSlotsWithSchema(
                Map.of("key", "value"), "scenario", "en", "template", Map.of("field", "description"));

        assertEquals(Map.of("key", "llm"), result);
    }

    @Test
    void structuredInputAwareExtractSlotsStillDispatchesByType() throws Exception {
        ClientSlotValueExtractor structuredExtractor =
                (userInput, scenarioCode, language, templateText) -> Map.of("key", "structured");
        ClientSlotValueExtractor llmExtractor =
                (userInput, scenarioCode, language, templateText) -> Map.of("key", "llm");

        Class<?> innerClass = Class.forName(
                "net.openan.a2at.sdk.client.prompt.assembly.DefaultA2ATClientBuilder$StructuredInputAwareSlotValueExtractor");
        Constructor<?> constructor = innerClass.getDeclaredConstructor(
                ClientSlotValueExtractor.class, ClientSlotValueExtractor.class);
        constructor.setAccessible(true);
        ClientSlotValueExtractor extractor =
                (ClientSlotValueExtractor) constructor.newInstance(structuredExtractor, llmExtractor);

        Map<String, String> mapResult = extractor.extractSlots(
                Map.of("key", "value"), "scenario", "en", "template");
        assertEquals(Map.of("key", "structured"), mapResult);

        Map<String, String> stringResult = extractor.extractSlots(
                "plain text", "scenario", "en", "template");
        assertEquals(Map.of("key", "llm"), stringResult);
    }
}