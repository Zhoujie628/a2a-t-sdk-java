package net.openan.a2at.sdk.client.prompt.recognition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import org.junit.jupiter.api.Test;

class LocalFileClientScenarioCatalogLoaderTest {

    @Test
    void loadDeserializesScenarioCatalogFromLocalFile() {
        LocalFileClientScenarioCatalogLoader loader = new LocalFileClientScenarioCatalogLoader(
                Path.of("..", "a2a-t-resources", "src", "main", "resources", "prompt_resources"));

        List<ScenarioDefinition> scenarios = loader.load("zh-CN");

        assertEquals(3, scenarios.size());
        assertEquals("subscribe_incident", scenarios.get(0).scenarioCode());
        assertEquals("energy_saving", scenarios.get(1).scenarioCode());
    }
}
