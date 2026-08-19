package net.openan.a2at.sdk.prompt.resources.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;

/**
 * Loads shared prompt templates from one local prompt resource root.
 *
 * @since 2026-06
 */
public final class LocalFilePromptTemplateLoader implements PromptTemplateTextLoader {

    private final Path promptRootDir;

    public LocalFilePromptTemplateLoader(Path promptRootDir) {
        this.promptRootDir = promptRootDir;
    }

    @Override
    public String loadTemplate(String scenarioCode, String language) {
        Path templatesRoot = promptRootDir.resolve("templates");
        if (!Files.exists(templatesRoot)) {
            throw notFound(scenarioCode, language);
        }
        Path templatePath;
        try (var typePaths = Files.list(templatesRoot)) {
            Optional<Path> match = typePaths
                    .filter(Files::isDirectory)
                    .map(typeDir -> typeDir.resolve("v1").resolve(scenarioCode).resolve(language).resolve("template.md"))
                    .filter(Files::exists)
                    .findFirst();
            templatePath = match.orElse(null);
        } catch (IOException exception) {
            throw new SdkException("Failed to scan prompt template resources: " + templatesRoot, exception);
        }
        if (templatePath == null) {
            throw notFound(scenarioCode, language);
        }
        try {
            return Files.readString(templatePath);
        } catch (IOException exception) {
            throw new SdkException("Failed to read template resource: " + templatePath, exception);
        }
    }

    private ResourceNotFoundException notFound(String scenarioCode, String language) {
        return new ResourceNotFoundException(
                "Prompt resource file does not exist.",
                promptRootDir.resolve("templates").toString()
                        + "/*/v1/" + scenarioCode + "/" + language + "/template.md");
    }
}
