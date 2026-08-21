package net.openan.a2at.sdk.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.resources.PathSegments;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Structured, always-valid identifier of a content template.
 *
 * <p>A template URI is composed of an extension name ({@code Task-T}, {@code Notification-T}, {@code Negotiation-T},
 * {@code Authorization-T}), at least one path segment (a scenario code, optionally prefixed with the
 * {@code network-layer} domain segment, or the type and phase segments of a negotiation template) and a trailing
 * template version. Constructing a {@code TemplateUri} validates every segment, so a value of this type can never
 * carry a malformed URI — the structural invariants that validators previously re-derived by splitting raw strings
 * are encoded in the type itself. The URI mirrors the resource directory layout one-to-one:
 * {@code templates/<extensionName>/<pathSegments>/<templateVersion>/<language>/template.md}.
 *
 * <p>The language is deliberately not part of the URI: it is global runtime context resolved from the prompt
 * configuration by whichever component needs it, never an addressability dimension of the template.
 *
 * @param extensionName first URI segment identifying the template family, such as {@code Task-T}
 * @param pathSegments middle URI segments, such as {@code [network-layer, energy-saving]} or
 *     {@code [information-negotiation, propose]}
 * @param templateVersion trailing URI segment, such as {@code v1}
 * @since 2026-08
 */
public record TemplateUri(
        @NonNull String extensionName, @NonNull List<String> pathSegments, @NonNull String templateVersion) {

    /** Default template version segment used by all built-in templates. */
    public static final String DEFAULT_TEMPLATE_VERSION = "v1";

    /**
     * Validates the components and defensively copies the path segment list.
     *
     * @throws NullPointerException if the extension name, path segment list or template version is null
     * @throws IllegalArgumentException if any component is not a simple path segment or the path segment list is empty
     */
    public TemplateUri {
        validateSegment(extensionName, "Extension name");
        Objects.requireNonNull(pathSegments, "Template URI path segments must not be null.");
        if (pathSegments.isEmpty()) {
            throw new IllegalArgumentException("Template URI must have at least one path segment.");
        }
        for (String segment : pathSegments) {
            validateSegment(segment, "Template URI path segment");
        }
        validateSegment(templateVersion, "Template version");
        pathSegments = List.copyOf(pathSegments);
    }

    /**
     * Creates a template URI from its components.
     *
     * @param extensionName first URI segment identifying the template family, such as {@code Task-T}
     * @param templateVersion trailing URI segment, such as {@code v1}
     * @param pathSegments middle URI segments, such as {@code network-layer, energy-saving}
     * @return validated template URI
     * @throws NullPointerException if any component is null
     * @throws IllegalArgumentException if any component is not a simple path segment or no path segment is given
     */
    public static @NonNull TemplateUri of(
            @NonNull String extensionName, @NonNull String templateVersion, @NonNull String... pathSegments) {
        return new TemplateUri(extensionName, List.of(pathSegments), templateVersion);
    }

    /**
     * Tries to parse a raw template URI into its components.
     *
     * @param templateUri template URI such as {@code Task-T/network-layer/energy-saving/v1}
     * @return parsed template URI, or an empty result when the input is null, blank, has fewer than three segments or
     *     contains a segment that is not a simple path segment
     */
    public static Optional<TemplateUri> parse(@Nullable String templateUri) {
        if (templateUri == null || templateUri.isBlank()) {
            return Optional.empty();
        }
        String[] parts = templateUri.strip().split("/");
        if (parts.length < 3) {
            return Optional.empty();
        }
        for (String part : parts) {
            if (!PathSegments.isSimpleSegment(part)) {
                return Optional.empty();
            }
        }
        return Optional.of(new TemplateUri(
                parts[0],
                List.of(parts).subList(1, parts.length - 1),
                parts[parts.length - 1]));
    }

    /**
     * Returns the raw template URI.
     *
     * @return template URI such as {@code Negotiation-T/information-negotiation/propose/v1}
     */
    public @NonNull String uri() {
        return Stream.concat(
                        Stream.concat(Stream.of(extensionName), pathSegments.stream()),
                        Stream.of(templateVersion))
                .collect(Collectors.joining("/"));
    }

    private static void validateSegment(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null.");
        if (!PathSegments.isSimpleSegment(value)) {
            throw new IllegalArgumentException(
                    label + " must be a non-blank simple path segment but was " + value + ".");
        }
    }
}
