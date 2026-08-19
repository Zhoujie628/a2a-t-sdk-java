package net.openan.a2at.sdk.client.prompt.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import org.junit.jupiter.api.Test;

class LocalFileClientSlotSchemaLoaderTest {

    @Test
    void loadSlotSchemaDeserializesJsonSchemaModelFromLocalFile() {
        LocalFileClientSlotSchemaLoader loader = new LocalFileClientSlotSchemaLoader(
                Path.of("..", "a2a-t-resources", "src", "main", "resources", "prompt_resources"));

        PromptSlotSchema schema = loader.loadSlotSchema("energy-saving", "zh-CN");

        assertEquals("energy-saving", schema.scenarioCode());
        assertEquals(6, schema.slotDefinitions().size());

        PromptSlotDefinition taskObject = schema.slotDefinitions().get(2);
        assertFalse(taskObject.name().isBlank());
        assertEquals("string", taskObject.jsonType());
        assertTrue(taskObject.description().length() > 10);

        PromptSlotDefinition taskContext = schema.slotDefinitions().get(4);
        assertFalse(taskContext.name().isBlank());
        assertTrue(taskContext.description().length() > 10);
    }
}
