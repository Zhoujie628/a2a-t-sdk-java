package net.openan.a2at.sdk.prompt.resources.loader;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.resources.ClasspathResourceDirectories;
import net.openan.a2at.sdk.core.validation.StandardTemplates;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;

/**
 * Loads shared prompt templates from packaged classpath prompt resources.
 *
 * <p>The template types are probed in a fixed order first and then in the order the extension directories appear
 * under {@code prompt_resources/templates/} on the classpath, so extensions bundled later — such as Authorization-T —
 * are loadable without extending a hardcoded list.
 *
 * @since 2026-06
 */
public final class ClasspathPromptTemplateLoader implements PromptTemplateTextLoader {

    private static final List<String> KNOWN_TEMPLATE_TYPES = List.of(
            StandardTemplates.TASK_EXTENSION_NAME, StandardTemplates.NOTIFICATION_EXTENSION_NAME, StandardTemplates.NEGOTIATION_EXTENSION_NAME);

    private static final List<String> TEMPLATE_TYPES = discoverTemplateTypes();

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

    private static List<String> discoverTemplateTypes() {
        Set<String> types = new LinkedHashSet<>(KNOWN_TEMPLATE_TYPES);
        try {
            types.addAll(ClasspathResourceDirectories.list("prompt_resources/templates/"));
        } catch (Exception ignored) {
            // classpath enumeration is unavailable; fall back to the known types only
        }
        return List.copyOf(types);
    }
}
