package net.openan.a2at.sdk.server.assembly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.prompt.resources.loader.LocalFilePromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.LocalFilePromptTemplateLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.server.model.PromptTemplateDefinition;
import net.openan.a2at.sdk.server.model.PromptTemplateSlotDefinition;

/**
 * Loads server-side prompt template definitions from one local prompt resource root.
 *
 * @since 2026-06
 */
public final class LocalFileServerPromptTemplateLoader {

    private final PromptTemplateTextLoader templateLoader;

    private final PromptSlotSchemaLoader slotSchemaLoader;

    private final Path promptRootDir;

    /**
     * Creates one local template loader.
     *
     * @param promptRootDir prompt resource root directory
     */
    public LocalFileServerPromptTemplateLoader(Path promptRootDir) {
        this.promptRootDir = promptRootDir;
        this.templateLoader = new LocalFilePromptTemplateLoader(promptRootDir);
        this.slotSchemaLoader = new LocalFilePromptSlotSchemaLoader(promptRootDir);
    }

    /**
     * Loads one prompt template definition for one scenario and language.
     *
     * @param scenarioCode scenario code
     * @param language language
     * @return prompt template definition
     */
    public PromptTemplateDefinition load(String scenarioCode, String language) {
        String templateText = templateLoader.loadTemplate(scenarioCode, language);
        PromptSlotSchema slotSchema = slotSchemaLoader.loadSlotSchema(scenarioCode, language);
        return new PromptTemplateDefinition(
                scenarioCode, language, templateText, toSlotDefinitions(slotSchema));
    }

    /**
     * Loads all prompt template definitions available for one language.
     *
     * @param language language
     * @return all discovered prompt template definitions
     */
    public List<PromptTemplateDefinition> loadAll(String language) {
        Path templatesRoot = promptRootDir.resolve("templates");
        if (!Files.exists(templatesRoot)) {
            throw new ResourceNotFoundException("Prompt resource file does not exist.", templatesRoot.toString());
        }

        List<String> scenarioCodes = new ArrayList<>();
        try (var typePaths = Files.list(templatesRoot)) {
            for (Path typeDir : (Iterable<Path>) typePaths.filter(Files::isDirectory)::iterator) {
                Path versionDir = typeDir.resolve("v1");
                if (!Files.isDirectory(versionDir)) {
                    continue;
                }
                try (Stream<Path> scenarioPaths = Files.list(versionDir)) {
                    scenarioPaths.filter(Files::isDirectory)
                            .map(path -> path.getFileName().toString())
                            .forEach(scenarioCodes::add);
                }
            }
        } catch (IOException exception) {
            throw new net.openan.a2at.sdk.core.exception.SdkException(
                    "Failed to scan prompt template resources: " + templatesRoot, exception);
        }

        return scenarioCodes.stream()
                .map(scenarioCode -> load(scenarioCode, language))
                .toList();
    }

    private static List<PromptTemplateSlotDefinition> toSlotDefinitions(PromptSlotSchema slotSchema) {
        return slotSchema.slotDefinitions().stream()
                .map(def -> new PromptTemplateSlotDefinition(def.name(), def.required()))
                .toList();
    }
}
