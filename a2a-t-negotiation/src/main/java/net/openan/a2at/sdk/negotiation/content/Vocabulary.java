package net.openan.a2at.sdk.negotiation.content;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import net.openan.a2at.sdk.core.resources.PathSegments;
import org.jspecify.annotations.Nullable;

/**
 * Language-specific text constants for negotiation templates.
 *
 * <p>A vocabulary is the single source of the section titles, slot names, appended line labels and list punctuation
 * used when rendering negotiation messages and when recognising sections inside received messages. The canonical keys
 * are language-neutral; every supported language exposes exactly the same key set, while the values match the bundled
 * template bytes of that language verbatim.
 *
 * <p>The values are file-driven: each language resolves {@code negotiation-vocabulary/{language}/vocabulary.json}
 * from a dual-root fallback — a file under the optional local resource root wins, otherwise the built-in classpath
 * resource is used. Both origins are validated against the pinned {@link #CANONICAL_KEYS} set, and a vocabulary that
 * exists nowhere, cannot be read, is malformed (including duplicate JSON keys or blank values) or drifts from the
 * canonical key set fails fast instead of silently degrading.
 *
 * <p>Caching mirrors the template-override regime: the built-in classpath vocabulary is resolved once per JVM and
 * context classloader (jar bytes are immutable), while a local resource root is re-resolved on every call so that
 * overriding, editing or removing a local vocabulary file takes effect immediately.
 *
 * @since 2026-06
 */
public final class Vocabulary {

    private static final String VOCABULARY_DIRECTORY = "negotiation-vocabulary";

    private static final String VOCABULARY_FILE_NAME = "vocabulary.json";

    private static final String CLASSPATH_ROOT = "prompt_resources/" + VOCABULARY_DIRECTORY + "/";

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .build();

    private static final ConcurrentHashMap<CacheKey, Vocabulary> CACHE = new ConcurrentHashMap<>();

    /** The language-neutral canonical key set every vocabulary file must define exactly, in a fixed order. */
    public static final List<String> CANONICAL_KEYS = List.of(
            "section.context",
            "section.info_items",
            "section.info_static",
            "section.info_conclusion",
            "section.info_result_content",
            "section.target",
            "section.target_intent",
            "section.target_alignment",
            "section.target_clarification",
            "section.target_conclusion",
            "section.target_result_content",
            "section.feasibility",
            "section.feasibility_evaluate",
            "section.feasibility_infeasible",
            "section.feasibility_conclusion",
            "section.feasibility_confirm",
            "slot.context",
            "slot.info_items",
            "slot.info_conclusion",
            "slot.info_result_content",
            "slot.target",
            "slot.target_intent",
            "slot.target_alignment",
            "slot.target_clarification",
            "slot.target_conclusion",
            "slot.target_result_content",
            "slot.feasibility",
            "slot.feasibility_evaluate",
            "slot.feasibility_infeasible",
            "slot.feasibility_conclusion",
            "slot.feasibility_confirm",
            "label.relationship",
            "punct.list_colon");

    private static final Set<String> CANONICAL_KEY_SET = Set.copyOf(CANONICAL_KEYS);

    private final String language;

    private final Map<String, String> entries;

    private Vocabulary(String language, Map<String, String> entries) {
        this.language = language;
        this.entries = entries;
    }

    /**
     * Returns the vocabulary for one language, resolved from the built-in classpath resources.
     *
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @return vocabulary holding the text constants of that language
     * @throws IllegalArgumentException if the language has no bundled vocabulary
     */
    public static Vocabulary forLanguage(String language) {
        return forLanguage(language, null);
    }

    /**
     * Returns the vocabulary for one language, resolved with the dual-root fallback.
     *
     * <p>A vocabulary file that exists under {@code {localRootDir}/negotiation-vocabulary/{language}/vocabulary.json}
     * wins; otherwise the built-in classpath resource {@code prompt_resources/negotiation-vocabulary/{language}/
     * vocabulary.json} is used. A null or otherwise absent local root simply means the classpath is consulted. Both
     * origins must define exactly the canonical key set.
     *
     * <p>Caching mirrors the template-override regime: the classpath vocabulary is resolved once per JVM and context
     * classloader (jar bytes are immutable), while a non-null local root is re-resolved on every call so overriding,
     * editing or removing the local file takes effect immediately.
     *
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @param localRootDir local prompt resource root containing the {@code negotiation-vocabulary/} tree; null
     *     disables local vocabulary overrides
     * @return vocabulary holding the text constants of that language
     * @throws IllegalArgumentException if the language has no vocabulary in any root, or the resolved file is
     *     unreadable, malformed or does not define exactly the canonical key set
     */
    public static Vocabulary forLanguage(String language, @Nullable Path localRootDir) {
        if (!PathSegments.isSimpleSegment(language)) {
            throw new IllegalArgumentException(
                    "Negotiation vocabulary language must be a non-blank simple path segment but was " + language
                            + ".");
        }
        Path normalizedRoot = normalizeRoot(localRootDir);
        if (normalizedRoot != null) {
            return load(language, normalizedRoot);
        }
        CacheKey cacheKey = new CacheKey(language, Thread.currentThread().getContextClassLoader());
        return CACHE.computeIfAbsent(cacheKey, key -> load(key.language, null));
    }

    /**
     * Returns the text constant registered under one canonical key.
     *
     * @param canonicalKey canonical vocabulary key such as {@code section.context} or {@code punct.list_colon}
     * @return language-specific text constant
     * @throws IllegalArgumentException if the key is not part of the vocabulary
     */
    public String get(String canonicalKey) {
        String value = entries.get(canonicalKey);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Unknown negotiation vocabulary key " + canonicalKey + " for language " + language + ".");
        }
        return value;
    }

    /**
     * Returns all canonical keys exposed by this vocabulary.
     *
     * @return immutable set of canonical keys, identical for every supported language
     */
    public Set<String> canonicalKeys() {
        return entries.keySet();
    }

    /**
     * Returns the language this vocabulary is bound to.
     *
     * @return locale identifier such as {@code zh-CN}
     */
    public String language() {
        return language;
    }

    private static @Nullable Path normalizeRoot(@Nullable Path localRootDir) {
        return localRootDir == null ? null : localRootDir.toAbsolutePath().normalize();
    }

    private static Vocabulary load(String language, @Nullable Path localRootDir) {
        if (localRootDir != null) {
            Path localFile = localRootDir
                    .resolve(VOCABULARY_DIRECTORY)
                    .resolve(language)
                    .resolve(VOCABULARY_FILE_NAME);
            if (Files.exists(localFile)) {
                return parse(language, readLocalFile(localFile), localFile.toString());
            }
        }
        String classpathPath = CLASSPATH_ROOT + language + "/" + VOCABULARY_FILE_NAME;
        InputStream stream = ClasspathResourceStreams.open(classpathPath);
        if (stream == null) {
            throw new IllegalArgumentException(
                    "Negotiation vocabulary does not exist for the configured language " + language
                            + "; supported languages are zh-CN and en-US, configure A2AT_LANGUAGE accordingly or"
                            + " provide " + classpathPath + " under the local resource root.");
        }
        try (stream) {
            return parse(language, new String(stream.readAllBytes(), StandardCharsets.UTF_8), classpathPath);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Failed to read the negotiation vocabulary " + classpathPath + " for language " + language
                            + "; supported languages are zh-CN and en-US, configure A2AT_LANGUAGE accordingly.",
                    exception);
        }
    }

    private static String readLocalFile(Path localFile) {
        try {
            return Files.readString(localFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Failed to read the negotiation vocabulary " + localFile + "; supported languages are zh-CN and"
                            + " en-US, configure A2AT_LANGUAGE accordingly.",
                    exception);
        }
    }

    private static Vocabulary parse(String language, String payload, String origin) {
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(payload);
        } catch (JsonProcessingException exception) {
            String detail = exception.getMessage() != null && exception.getMessage().contains("Duplicate field")
                    ? "it contains duplicate keys"
                    : "it is not valid JSON";
            throw malformed(language, origin, detail, exception);
        }
        if (root == null || !root.isObject()) {
            throw malformed(language, origin, "it is not a flat JSON object of string values", null);
        }
        Map<String, String> entries = new LinkedHashMap<>();
        root.fields().forEachRemaining(field -> {
            JsonNode value = field.getValue();
            if (!value.isTextual()) {
                throw malformed(language, origin, "the value of key '" + field.getKey() + "' is not a string", null);
            }
            String text = value.textValue();
            if (text.isBlank()) {
                throw malformed(language, origin, "the value of key '" + field.getKey() + "' is blank", null);
            }
            entries.put(field.getKey(), text);
        });
        validateCanonicalKeys(language, entries, origin);
        return new Vocabulary(language, Collections.unmodifiableMap(entries));
    }

    private static void validateCanonicalKeys(String language, Map<String, String> entries, String origin) {
        Set<String> missing = new LinkedHashSet<>(CANONICAL_KEYS);
        missing.removeAll(entries.keySet());
        Set<String> unexpected = new LinkedHashSet<>(entries.keySet());
        unexpected.removeAll(CANONICAL_KEY_SET);
        if (missing.isEmpty() && unexpected.isEmpty()) {
            return;
        }
        throw new IllegalArgumentException(
                "Negotiation vocabulary " + origin + " for language " + language
                        + " must define exactly the canonical vocabulary keys; missing keys: " + missing
                        + ", unexpected keys: " + unexpected + ".");
    }

    private static IllegalArgumentException malformed(
            String language, String origin, String detail, @Nullable Throwable cause) {
        IllegalArgumentException exception = new IllegalArgumentException(
                "Negotiation vocabulary " + origin + " for language " + language + " is malformed: " + detail
                        + "; supported languages are zh-CN and en-US, configure A2AT_LANGUAGE accordingly.");
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private record CacheKey(String language, @Nullable ClassLoader contextClassLoader) {}
}
