package net.openan.a2at.sdk.prompt.resources.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotJsonSchema;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;

/**
 * Loads shared slot schemas from one local prompt resource root.
 *
 * @since 2026-06
 */
public final class LocalFilePromptSlotSchemaLoader implements PromptSlotSchemaLoader {

    private final Path promptRootDir;

    public LocalFilePromptSlotSchemaLoader(Path promptRootDir) {
        this.promptRootDir = promptRootDir;
    }

    @Override
    public PromptSlotSchema loadSlotSchema(String scenarioCode, String language) {
        Path slotsRoot = promptRootDir.resolve("slots");
        if (!Files.exists(slotsRoot)) {
            throw notFound(scenarioCode, language);
        }
        Path schemaPath;
        if (scenarioCode.contains("/")) {
            schemaPath = slotsRoot.resolve(scenarioCode).resolve(language).resolve("slot.json");
            if (!Files.exists(schemaPath)) {
                throw notFound(scenarioCode, language);
            }
        } else {
            try (var typePaths = Files.list(slotsRoot)) {
                Optional<Path> match = typePaths
                        .filter(Files::isDirectory)
                        .map(typeDir -> typeDir.resolve("v1").resolve(scenarioCode).resolve(language).resolve("slot.json"))
                        .filter(Files::exists)
                        .findFirst();
                schemaPath = match.orElse(null);
            } catch (IOException exception) {
                throw new A2ATError("Failed to scan slot schema resources: " + slotsRoot, exception);
            }
            if (schemaPath == null) {
                throw notFound(scenarioCode, language);
            }
        }
        try {
            return PromptResourceJsonParser.parse(Files.readString(schemaPath), PromptSlotJsonSchema.class)
                    .toPromptSlotSchema(scenarioCode);
        } catch (IOException exception) {
            throw new A2ATError("Failed to read slot schema resource: " + schemaPath, exception);
        }
    }

    private ResourceNotFoundException notFound(String scenarioCode, String language) {
        String pathHint = scenarioCode.contains("/")
                ? promptRootDir.resolve("slots").resolve(scenarioCode).resolve(language).resolve("slot.json").toString()
                : promptRootDir.resolve("slots").toString()
                        + "/*/v1/" + scenarioCode + "/" + language + "/slot.json";
        return new ResourceNotFoundException("Prompt resource file does not exist.", pathHint);
    }
}
