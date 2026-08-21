package net.openan.a2at.sdk.negotiation.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.prompt.resources.catalog.TemplateDescriptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default negotiation template loader with dual-root fallback.
 *
 * <p>Templates are resolved by trying a configurable local resource root first and falling back to the built-in
 * classpath resources bundled with the SDK: a template file that exists under the local root wins, otherwise the
 * built-in template of the same URI is used. This fallback is deliberately independent of the prompt source-type
 * configuration so that the built-in templates always remain available as a safety net.
 *
 * <p>The local root directory follows the prompt resource root layout: it is the directory that contains the
 * {@code templates/} tree. Only templates are taken from the local root; LLM prompt resources always come from the
 * classpath.
 *
 * @since 2026-06
 */
public final class DefaultNegotiationTemplateLoader implements NegotiationTemplateLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNegotiationTemplateLoader.class);

    private static final String CLASSPATH_ROOT = "prompt_resources/";

    private static final String TEMPLATE_FILE_NAME = "template.md";

    private static final List<NegotiationType> LOAD_ALL_TYPE_ORDER =
            List.of(NegotiationType.INFORMATION, NegotiationType.TARGET, NegotiationType.FEASIBILITY);

    private static final List<NegotiationPhase> LOAD_ALL_PHASE_ORDER =
            List.of(NegotiationPhase.PROPOSE, NegotiationPhase.ACCEPT);

    private final String language;

    private final Path localRootDir;

    /**
     * Creates a loader for one language with an optional local template root.
     *
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @param localRootDir local prompt resource root containing the {@code templates/} tree; null or blank disables
     *     local template overrides
     * @throws IllegalArgumentException if the language is not a simple path segment
     */
    public DefaultNegotiationTemplateLoader(String language, String localRootDir) {
        if (!PathSegments.isSimpleSegment(language)) {
            throw new IllegalArgumentException(
                    "Negotiation template loader language must be a non-blank simple path segment but was " + language
                            + ".");
        }
        this.language = language;
        this.localRootDir = localRootDir == null || localRootDir.isBlank() ? null : Path.of(localRootDir);
    }

    /**
     * Loads one negotiation template, preferring a local override over the built-in classpath template.
     *
     * @param reference template addressing key, including the language to load
     * @return loaded template with its URI, description and full content
     * @throws ResourceNotFoundException if the template exists neither under the local root nor on the classpath
     */
    @Override
    public PromptTemplate load(NegotiationReference reference) {
        String relativePath = templateRelativePath(reference);
        String content = readTemplate(relativePath);
        PromptTemplate template = new PromptTemplate(reference.uri(), TemplateDescriptions.extract(content), content);
        LOGGER.atDebug().log("negotiation_template_loaded uri={} language={}", reference.uri(), reference.language());
        return template;
    }

    /**
     * Loads every loadable negotiation template of the loader's language.
     *
     * <p>The fixed iteration order is the negotiation type order information, target, feasibility crossed with the
     * phase order propose, accept-reject. Templates that exist nowhere are skipped.
     *
     * @return templates of the loader's language that could be loaded, in a fixed order
     */
    @Override
    public List<PromptTemplate> loadAll() {
        List<PromptTemplate> templates = new ArrayList<>();
        for (NegotiationType type : LOAD_ALL_TYPE_ORDER) {
            for (NegotiationPhase phase : LOAD_ALL_PHASE_ORDER) {
                try {
                    templates.add(load(new NegotiationReference(type, phase, language)));
                } catch (ResourceNotFoundException exception) {
                    // A template that exists nowhere for this language is simply not listed.
                }
            }
        }
        return List.copyOf(templates);
    }

    private static String templateRelativePath(NegotiationReference reference) {
        return String.join(
                "/",
                "templates",
                "Negotiation-T",
                "v1",
                reference.typeSegment(),
                reference.phase().uriSegment(),
                reference.language(),
                TEMPLATE_FILE_NAME);
    }

    private String readTemplate(String relativePath) {
        if (localRootDir != null) {
            Path localPath = localRootDir.resolve(relativePath);
            if (Files.exists(localPath)) {
                try {
                    return Files.readString(localPath, StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    throw new A2ATError("Failed to read negotiation template: " + localPath, exception);
                }
            }
        }
        String classpathPath = CLASSPATH_ROOT + relativePath;
        InputStream stream = ClasspathResourceStreams.open(classpathPath);
        if (stream == null) {
            throw new ResourceNotFoundException(
                    "Negotiation template does not exist for the configured language; set A2AT_LANGUAGE to a"
                            + " language with bundled templates (zh-CN or en-US) or provide the template under the"
                            + " local resource root.",
                    classpathPath);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new A2ATError("Failed to read negotiation template: " + classpathPath, exception);
        }
    }

}


