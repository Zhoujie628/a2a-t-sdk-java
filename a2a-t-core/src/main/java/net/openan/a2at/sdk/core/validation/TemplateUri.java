package net.openan.a2at.sdk.core.validation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.resources.PathSegments;

/**
 * Structured, always-valid identifier of a content template.
 *
 * <p>A template URI is composed of an extension name ({@code Task-T}, {@code Notification-T}, {@code Negotiation-T},
 * {@code Authorization-T}), a version segment and at least one trailing segment (a scenario code, or the type and
 * phase segments of a negotiation template). Constructing a {@code TemplateUri} validates every segment, so a value
 * of this type can never carry a malformed URI — the structural invariants that validators previously re-derived by
 * splitting raw strings are encoded in the type itself.
 *
 * <p>The language is deliberately not part of the URI: it is global runtime context resolved from the prompt
 * configuration by whichever component needs it, never an addressability dimension of the template.
 *
 * @param extensionName first URI segment identifying the template family, such as {@code Task-T}
 * @param version second URI segment, such as {@code v1}
 * @param segments trailing URI segments, such as {@code [energy-saving]} or {@code [information-negotiation, propose]}
 * @since 2026-08
 */
public record TemplateUri(String extensionName, String version, List<String> segments) {

    /**
     * Validates the components and defensively copies the segment list.
     *
     * @throws NullPointerException if the extension name, version or segment list is null
     * @throws IllegalArgumentException if any component is not a simple path segment or the segment list is empty
     */
    public TemplateUri {
        validateSegment(extensionName, "Extension name");
        validateSegment(version, "Version");
        Objects.requireNonNull(segments, "Template URI segments must not be null.");
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Template URI must have at least one trailing segment.");
        }
        for (String segment : segments) {
            validateSegment(segment, "Template URI segment");
        }
        segments = List.copyOf(segments);
    }

    /**
     * Creates a template URI from its components.
     *
     * @param extensionName first URI segment identifying the template family, such as {@code Task-T}
     * @param version second URI segment, such as {@code v1}
     * @param segments trailing URI segments, such as {@code energy-saving}
     * @return validated template URI
     * @throws NullPointerException if any component is null
     * @throws IllegalArgumentException if any component is not a simple path segment or no trailing segment is given
     */
    public static TemplateUri of(String extensionName, String version, String... segments) {
        return new TemplateUri(extensionName, version, List.of(segments));
    }

    /**
     * Tries to parse a raw template URI into its components.
     *
     * @param templateUri template URI such as {@code Task-T/v1/energy-saving}
     * @return parsed template URI, or an empty result when the input is null, blank, has fewer than three segments or
     *     contains a segment that is not a simple path segment
     */
    public static Optional<TemplateUri> parse(String templateUri) {
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
        return Optional.of(new TemplateUri(parts[0], parts[1], List.of(parts).subList(2, parts.length)));
    }

    /**
     * Returns the raw template URI.
     *
     * @return template URI such as {@code Negotiation-T/v1/feasibility-negotiation/propose}
     */
    public String uri() {
        return Stream.concat(Stream.of(extensionName, version), segments.stream())
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
