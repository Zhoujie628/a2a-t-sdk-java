package net.openan.a2at.sdk.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.TemplateUri;
import org.junit.jupiter.api.Test;

class ClasspathPromptResourceLoaderTest {

    private final ClasspathPromptResourceLoader loader = new ClasspathPromptResourceLoader();

    @Test
    void loadsTextResourceUsingPromptResourceKey() {
        PromptResourceKey key = PromptResourceKey.prompt("slot_extraction", "en-US", "system.md");

        String text = loader.loadText(key);

        assertEquals("system prompt", text.trim());
    }

    @Test
    void rejectsResourceTraversalSegments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PromptResourceKey("prompts", List.of("../escape"), "en-US", "system.md"));
    }

    @Test
    void raisesTypedErrorWhenResourceIsMissing() {
        PromptResourceKey key = PromptResourceKey.template(
                TemplateUri.of("Task-T", "v1", "network-layer", "missing_scenario"), "en-US", "template.md");

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class, () -> loader.loadText(key));

        assertEquals(
                "prompt_resources/templates/Task-T/network-layer/missing_scenario/v1/en-US/template.md",
                error.resourcePath().replace('\\', '/'));
    }

    @Test
    void loadsPackagedScenarioCatalogForZhCn() {
        String text = loader.loadText(PromptResourceKey.scenario("zh-CN", "scenarios.json"));

        assertTrue(text.contains("subscribe-incident"));
        assertTrue(text.contains("energy-saving"));
    }

    @Test
    void loadsPackagedSubscribeIncidentSlotSchemaWithSemanticHint() {
        PromptResourceKey key =
                PromptResourceKey.slotSchema(
                        TemplateUri.of("Notification-T", "v1", "network-layer", "subscribe-incident"),
                        "zh-CN",
                        "slot.json");

        String text = loader.loadText(key);

        assertTrue(text.contains("\"required\": []"));
        assertTrue(text.contains("x-a2at-value-constraint"));
        assertTrue(text.contains("critical"));
        assertTrue(text.contains("high"));
        assertTrue(text.contains("medium"));
        assertTrue(text.contains("low"));
    }
}
