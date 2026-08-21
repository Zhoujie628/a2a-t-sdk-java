package net.openan.a2at.sdk.prompt.resources.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.A2ATError;

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
        if (scenarioCode.contains("/")) {
            templatePath = templatesRoot.resolve(scenarioCode).resolve(language).resolve("template.md");
            if (!Files.exists(templatePath)) {
                throw notFound(scenarioCode, language);
            }
        } else {
            try (var typePaths = Files.list(templatesRoot)) {
                Optional<Path> match = typePaths
                        .filter(Files::isDirectory)
                        .map(typeDir -> resolveBareCode(typeDir, scenarioCode, language, "template.md"))
                        .filter(Files::exists)
                        .findFirst();
                templatePath = match.orElse(null);
            } catch (IOException exception) {
                throw new A2ATError("Failed to scan prompt template resources: " + templatesRoot, exception);
            }
            if (templatePath == null) {
                throw notFound(scenarioCode, language);
            }
        }
        try {
            return Files.readString(templatePath);
        } catch (IOException exception) {
            throw new A2ATError("Failed to read template resource: " + templatePath, exception);
        }
    }

    /**
     * Resolves a bare scenario code under one template type directory, preferring the {@code network-layer} domain
     * layout over the plain layout.
     */
    private static Path resolveBareCode(Path typeDir, String scenarioCode, String language, String fileName) {
        Path networkLayer = typeDir.resolve("network-layer").resolve(scenarioCode).resolve("v1");
        if (Files.exists(networkLayer.resolve(language).resolve(fileName))) {
            return networkLayer.resolve(language).resolve(fileName);
        }
        return typeDir.resolve(scenarioCode).resolve("v1").resolve(language).resolve(fileName);
    }

    private ResourceNotFoundException notFound(String scenarioCode, String language) {
        String pathHint = scenarioCode.contains("/")
                ? promptRootDir.resolve("templates").resolve(scenarioCode).resolve(language).resolve("template.md").toString()
                : promptRootDir.resolve("templates").toString()
                        + "/*/network-layer/" + scenarioCode + "/v1/" + language + "/template.md (or the layout without the network-layer segment)";
        return new ResourceNotFoundException("Prompt resource file does not exist.", pathHint);
    }
}
