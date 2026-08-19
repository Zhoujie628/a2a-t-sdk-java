package net.openan.a2at.sdk.prompt.resources.loader;

import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;

/**
 * Loads shared prompt templates from packaged classpath prompt resources.
 *
 * @since 2026-06
 */
public final class ClasspathPromptTemplateLoader implements PromptTemplateTextLoader {

    private static final List<String> TEMPLATE_TYPES = List.of("Task-T", "Notification-T", "Negotiation-T");

    private final ClasspathPromptResourceLoader resourceLoader;

    public ClasspathPromptTemplateLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String loadTemplate(String scenarioCode, String language) {
        for (String templateType : TEMPLATE_TYPES) {
            try {
                return resourceLoader.loadText(
                        PromptResourceKey.template(templateType, scenarioCode, language, "template.md"));
            } catch (ResourceNotFoundException ignored) {
                // try next template type
            }
        }
        throw new ResourceNotFoundException(
                "Prompt resource file does not exist.",
                "prompt_resources/templates/*/v1/" + scenarioCode + "/" + language + "/template.md");
    }
}
