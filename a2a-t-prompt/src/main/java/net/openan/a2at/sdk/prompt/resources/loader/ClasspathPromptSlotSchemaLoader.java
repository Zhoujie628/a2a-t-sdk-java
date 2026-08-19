package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotJsonSchema;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;

/**
 * Loads shared slot schemas from packaged classpath prompt resources.
 *
 * @since 2026-06
 */
public final class ClasspathPromptSlotSchemaLoader implements PromptSlotSchemaLoader {

    private static final List<String> SLOT_TYPES = List.of("Task-T", "Notification-T");

    private final ClasspathPromptResourceLoader resourceLoader;

    public ClasspathPromptSlotSchemaLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public PromptSlotSchema loadSlotSchema(String scenarioCode, String language) {
        for (String slotType : SLOT_TYPES) {
            try {
                String payload = resourceLoader.loadText(
                        new PromptResourceKey("slots", slotType, scenarioCode, language, "slot.json"));
                return PromptResourceJsonParser.parse(payload, PromptSlotJsonSchema.class)
                        .toPromptSlotSchema(scenarioCode);
            } catch (ResourceNotFoundException ignored) {
                // try next slot type
            } catch (JsonProcessingException exception) {
                throw new SdkException("Failed to parse slot schema: " + scenarioCode, exception);
            }
        }
        throw new ResourceNotFoundException(
                "Prompt resource file does not exist.",
                "prompt_resources/slots/*/v1/" + scenarioCode + "/" + language + "/slot.json");
    }
}
