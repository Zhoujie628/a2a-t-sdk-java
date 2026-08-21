package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.resources.ClasspathResourceDirectories;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotJsonSchema;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;

/**
 * Loads shared slot schemas from packaged classpath prompt resources.
 *
 * <p>The slot types are probed in a fixed order first and then in the order the extension directories appear under
 * {@code prompt_resources/slots/} on the classpath, so extensions bundled later are loadable without extending a
 * hardcoded list.
 *
 * @since 2026-06
 */
public final class ClasspathPromptSlotSchemaLoader implements PromptSlotSchemaLoader {

    private static final List<String> KNOWN_SLOT_TYPES = List.of("Task-T", "Notification-T");

    private static final List<String> SLOT_TYPES = discoverSlotTypes();

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
                throw new A2ATError("Failed to parse slot schema: " + scenarioCode, exception);
            }
        }
        throw new ResourceNotFoundException(
                "Prompt resource file does not exist.",
                "prompt_resources/slots/*/v1/" + scenarioCode + "/" + language + "/slot.json");
    }

    private static List<String> discoverSlotTypes() {
        Set<String> types = new LinkedHashSet<>(KNOWN_SLOT_TYPES);
        try {
            types.addAll(ClasspathResourceDirectories.list("prompt_resources/slots/"));
        } catch (Exception ignored) {
            // classpath enumeration is unavailable; fall back to the known types only
        }
        return List.copyOf(types);
    }
}
