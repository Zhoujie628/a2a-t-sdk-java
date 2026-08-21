package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;

/**
 * Loads shared scenario catalogs from packaged classpath prompt resources.
 *
 * @since 2026-06
 */
public final class ClasspathPromptScenarioCatalogLoader {

    private final ClasspathPromptResourceLoader resourceLoader;

    public ClasspathPromptScenarioCatalogLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<ScenarioDefinition> load(String language) {
        String payload =
                resourceLoader.loadText(new PromptResourceKey("scenarios", "catalog", language, "scenarios.json"));
        try {
            return PromptResourceJsonParser.parse(payload, ScenarioCatalog.class)
                    .scenarios();
        } catch (JsonProcessingException exception) {
            throw new A2ATError("Failed to parse scenario catalog for language: " + language, exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScenarioCatalog(@JsonProperty("scenarios") List<ScenarioDefinition> scenarios) {}
}
