package net.openan.a2at.sdk.prompt.resources.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.core.model.TemplateUri;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Directory-driven catalog over the prompt template tree of every A2A-T extension.
 *
 * <p>The catalog enumerates the {@code templates/} tree of the bundled prompt resources by walking its directories,
 * so every extension directory that appears under {@code prompt_resources/templates/} — Task-T, Notification-T,
 * Authorization-T, Negotiation-T and any extension added later — is picked up without a hardcoded extension list.
 * A template file lives at {@code templates/<extensionName>/<pathSegments>/<templateVersion>/<language>/template.md}
 * and is addressed by the URI formed from the segments before the language, for example
 * {@code Negotiation-T/information-negotiation/propose/v1} or {@code Task-T/network-layer/energy-saving/v1}.
 *
 * <p>A local file under the configured local resource root overrides the built-in classpath template of the same
 * path, and the classpath is the fallback otherwise. Both query methods never throw: a template or a root that cannot
 * be loaded is skipped or answered with an empty result and a warning log.
 *
 * @since 2026-08
 */
public final class PromptTemplateCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptTemplateCatalog.class);

    private static final String CLASSPATH_ROOT = "prompt_resources/";

    private static final String CLASSPATH_TEMPLATE_ROOT = CLASSPATH_ROOT + "templates/";

    private static final String TEMPLATE_DIRECTORY = "templates";

    private static final String TEMPLATE_FILE_NAME = "template.md";

    private static final int MINIMUM_URI_SEGMENTS = 3;

    private final String language;

    private final Path localRootDir;

    /**
     * Creates a catalog for one language with an optional local template root.
     *
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @param localRootDir local prompt resource root containing the {@code templates/} tree; null or blank disables
     *     local template overrides
     * @throws IllegalArgumentException if the language is not a simple path segment
     */
    public PromptTemplateCatalog(@NonNull String language, @Nullable String localRootDir) {
        if (!PathSegments.isSimpleSegment(language)) {
            throw new IllegalArgumentException(
                    "Prompt template catalog language must be a non-blank simple path segment but was " + language
                            + ".");
        }
        this.language = language;
        this.localRootDir = localRootDir == null || localRootDir.isBlank() ? null : Path.of(localRootDir);
    }

    /**
     * Lists every loadable template of the configured language across all extensions.
     *
     * <p>The extension directories are discovered from the resource tree itself, so the result grows automatically
     * when a new extension directory appears. Local templates override built-in templates of the same path and
     * local-only templates are included. The result is sorted by template URI, which orders by extension first.
     *
     * @return loadable templates of the configured language sorted by URI; empty when none can be loaded
     */
    public @NonNull List<PromptTemplate> loadAll() {
        Map<String, String> contentsByPath = new LinkedHashMap<>();
        try {
            contentsByPath.putAll(classpathTemplates());
        } catch (IOException | URISyntaxException exception) {
            LOGGER.atWarn()
                    .log("prompt_template_catalog_unavailable root=classpath reason={}", exception.getMessage());
        }
        contentsByPath.putAll(localTemplates());
        List<PromptTemplate> templates = new ArrayList<>();
        String languageSuffix = "/" + language + "/" + TEMPLATE_FILE_NAME;
        for (Map.Entry<String, String> entry : contentsByPath.entrySet()) {
            String path = entry.getKey();
            if (!path.endsWith(languageSuffix)) {
                continue;
            }
            String uri = path.substring(
                    CLASSPATH_TEMPLATE_ROOT.length(), path.length() - languageSuffix.length());
            if (!isCatalogableUri(uri)) {
                LOGGER.atDebug().log("prompt_template_skipped path={} reason=not_a_template_uri", path);
                continue;
            }
            String content = entry.getValue();
            templates.add(new PromptTemplate(uri, TemplateDescriptions.extract(content), content));
        }
        templates.sort(Comparator.comparing(PromptTemplate::uri));
        LOGGER.atDebug().log("prompt_templates_listed count={} language={}", templates.size(), language);
        return List.copyOf(templates);
    }

    /**
     * Loads one template of the configured language by its URI, regardless of the extension.
     *
     * @param templateUri template URI such as {@code Negotiation-T/information-negotiation/propose/v1} or
     *     {@code Task-T/network-layer/energy-saving/v1}
     * @return the addressed template, or an empty optional when no template exists for it in the configured language
     * @throws NullPointerException if the template URI is null
     */
    public Optional<PromptTemplate> load(@NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        String uri = templateUri.uri();
        String relativePath = String.join("/", TEMPLATE_DIRECTORY, uri, language, TEMPLATE_FILE_NAME);
        try {
            String content = readTemplate(relativePath);
            return Optional.of(new PromptTemplate(uri, TemplateDescriptions.extract(content), content));
        } catch (A2ATError exception) {
            return Optional.empty();
        }
    }

    private Map<String, String> classpathTemplates() throws IOException, URISyntaxException {
        Map<String, String> templates = new LinkedHashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = PromptTemplateCatalog.class.getClassLoader();
        }
        Enumeration<URL> roots = classLoader.getResources(CLASSPATH_TEMPLATE_ROOT);
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if ("file".equals(root.getProtocol())) {
                collectFilesystemTemplates(Path.of(root.toURI()), templates);
            } else if ("jar".equals(root.getProtocol())) {
                collectJarTemplates((JarURLConnection) root.openConnection(), templates);
            } else {
                LOGGER.atDebug().log("prompt_template_root_skipped url={} reason=unsupported_protocol", root);
            }
        }
        return templates;
    }

    private static void collectFilesystemTemplates(Path rootDirectory, Map<String, String> templates)
            throws IOException {
        try (Stream<Path> paths = Files.walk(rootDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(TEMPLATE_FILE_NAME))
                    .forEach(path -> collectTemplate(
                            templates,
                            CLASSPATH_TEMPLATE_ROOT
                                    + rootDirectory.relativize(path).toString().replace('\\', '/'),
                            path));
        }
    }

    private static void collectJarTemplates(JarURLConnection connection, Map<String, String> templates)
            throws IOException {
        try (java.util.jar.JarFile jarFile = connection.getJarFile()) {
            jarFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith(CLASSPATH_TEMPLATE_ROOT))
                    .filter(entry -> entry.getName().endsWith(TEMPLATE_FILE_NAME))
                    .forEach(entry -> collectTemplate(templates, entry.getName(), null));
        }
    }

    private static void collectTemplate(Map<String, String> templates, String classpathPath, Path filesystemPath) {
        try {
            String content;
            if (filesystemPath != null) {
                content = Files.readString(filesystemPath, StandardCharsets.UTF_8);
            } else {
                InputStream stream = ClasspathResourceStreams.open(classpathPath);
                if (stream == null) {
                    LOGGER.atDebug()
                            .log("prompt_template_skipped path={} reason=classpath_entry_unreadable", classpathPath);
                    return;
                }
                try (stream) {
                    content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            templates.put(classpathPath, content);
        } catch (IOException exception) {
            LOGGER.atDebug()
                    .log("prompt_template_skipped path={} reason=read_failed", classpathPath);
        }
    }

    private Map<String, String> localTemplates() {
        Map<String, String> templates = new LinkedHashMap<>();
        if (localRootDir == null) {
            return templates;
        }
        Path templateRoot = localRootDir.resolve(TEMPLATE_DIRECTORY);
        if (!Files.isDirectory(templateRoot)) {
            return templates;
        }
        try (Stream<Path> paths = Files.walk(templateRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(TEMPLATE_FILE_NAME))
                    .forEach(path -> collectTemplate(
                            templates,
                            CLASSPATH_TEMPLATE_ROOT
                                    + templateRoot.relativize(path).toString().replace('\\', '/'),
                            path));
        } catch (IOException exception) {
            LOGGER.atWarn()
                    .log("prompt_template_catalog_unavailable root={} reason={}", templateRoot, exception.getMessage());
        }
        return templates;
    }

    private String readTemplate(String relativePath) {
        if (localRootDir != null) {
            Path localPath = localRootDir.resolve(relativePath);
            if (Files.exists(localPath)) {
                try {
                    return Files.readString(localPath, StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    throw new A2ATError("Failed to read prompt template: " + localPath, exception);
                }
            }
        }
        String classpathPath = CLASSPATH_ROOT + relativePath;
        InputStream stream = ClasspathResourceStreams.open(classpathPath);
        if (stream == null) {
            throw new ResourceNotFoundException(
                    "Prompt template does not exist for the configured language; set the language to one with"
                            + " bundled templates or provide the template under the local resource root.",
                    classpathPath);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new A2ATError("Failed to read prompt template: " + classpathPath, exception);
        }
    }

    private static boolean isCatalogableUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String[] segments = uri.split("/");
        if (segments.length < MINIMUM_URI_SEGMENTS) {
            return false;
        }
        for (String segment : segments) {
            if (!PathSegments.isSimpleSegment(segment)) {
                return false;
            }
        }
        return true;
    }
}
