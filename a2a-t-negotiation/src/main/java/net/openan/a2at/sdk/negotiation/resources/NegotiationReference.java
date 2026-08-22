package net.openan.a2at.sdk.negotiation.resources;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Addressing key for one negotiation template: negotiation type, API-level phase and language.
 *
 * <p>The reference composes the template URI from the type and phase segments, so the URI spelling has a single source.
 * The language is query context rather than part of the resource identity and is therefore not part of the URI.
 *
 * @param type negotiation type addressed by the reference
 * @param phase API-level phase addressed by the reference; accept and reject share the same template
 * @param language locale identifier such as {@code zh-CN} or {@code en-US}
 * @since 2026-06
 */
public record NegotiationReference(NegotiationType type, NegotiationPhase phase, String language) {

    private static final String URI_PREFIX = StandardTemplates.NEGOTIATION_EXTENSION_NAME;

    private static final String URI_VERSION_SEGMENT = TemplateUri.DEFAULT_TEMPLATE_VERSION;

    private static final String TYPE_SEGMENT_SUFFIX = "-negotiation";

    /**
     * Validates the reference fields.
     *
     * @throws NullPointerException if the type or phase is null
     * @throws IllegalArgumentException if the language is not a simple path segment
     */
    public NegotiationReference {
        Objects.requireNonNull(type, "Negotiation reference type must not be null.");
        Objects.requireNonNull(phase, "Negotiation reference phase must not be null.");
        if (!PathSegments.isSimpleSegment(language)) {
            throw new IllegalArgumentException(
                    "Negotiation reference language must be a non-blank simple path segment but was " + language + ".");
        }
    }

    /**
     * Returns the hyphenated URI segment of the referenced negotiation type.
     *
     * @return URI segment such as {@code information-negotiation}
     */
    public String typeSegment() {
        return type.typeSegment();
    }

    /**
     * Returns the template URI of the referenced template.
     *
     * @return template URI such as {@code Negotiation-T/information-negotiation/propose/v1}
     */
    public String uri() {
        return String.join("/", URI_PREFIX, typeSegment(), phase.uriSegment(), URI_VERSION_SEGMENT);
    }

    /**
     * Returns the typed template URI of the referenced template.
     *
     * <p>The exact inverse of {@link #fromTemplateUri(TemplateUri, NegotiationPhase, String)}: the URI is composed from
     * the same extension-name constant and default version segment the parser accepts, so
     * {@code fromTemplateUri(reference.templateUri(), reference.phase(), ...)} always addresses the same template.
     *
     * @return typed template URI such as {@code Negotiation-T/information-negotiation/propose/v1}
     */
    public @NonNull TemplateUri templateUri() {
        return TemplateUri.of(URI_PREFIX, List.of(typeSegment(), phase.uriSegment()), URI_VERSION_SEGMENT);
    }

    /**
     * Tries to parse a template URI into a reference, checking it against the expected phase.
     *
     * <p>The URI layer cannot distinguish accept from reject because both share the {@code accept-reject} segment; the
     * expected phase disambiguates the parsed result, which therefore always carries the expected phase.
     *
     * @param templateUri template URI to parse, such as {@code Negotiation-T/target-negotiation/accept-reject/v1}
     * @param expectedPhase API-level phase the caller is operating on; the parsed reference carries this phase
     * @param language locale identifier for the parsed reference
     * @return reference addressed by the URI carrying the expected phase, or an empty result when the URI is null,
     *     blank or malformed (wrong segment count, prefix, type segment or trailing version segment) or its phase
     *     segment does not match the expected phase
     * @throws NullPointerException if the expected phase is null
     */
    public static Optional<NegotiationReference> tryParse(
            @Nullable String templateUri, @NonNull NegotiationPhase expectedPhase, String language) {
        Objects.requireNonNull(expectedPhase, "Expected negotiation phase must not be null.");
        return TemplateUri.parse(templateUri).flatMap(uri -> fromTemplateUri(uri, expectedPhase, language));
    }

    /**
     * Derives a reference from a typed template URI, checking it against the expected phase.
     *
     * <p>The typed variant of {@link #tryParse(String, NegotiationPhase, String)}: because a {@link TemplateUri} is
     * always structurally well formed, the checks operate on the URI components directly instead of splitting a raw
     * string. The URI layer cannot distinguish accept from reject because both share the {@code accept-reject}
     * segment; the expected phase disambiguates the result, which therefore always carries the expected phase.
     *
     * @param templateUri typed template URI such as {@code Negotiation-T/target-negotiation/accept-reject/v1}
     * @param expectedPhase API-level phase the caller is operating on; the derived reference carries this phase
     * @param language locale identifier for the derived reference
     * @return reference addressed by the URI carrying the expected phase, or an empty result when the URI does not
     *     address a negotiation template of the expected phase (wrong extension name, path segment count, type
     *     segment, phase segment or template version)
     * @throws NullPointerException if the template URI or the expected phase is null
     */
    public static Optional<NegotiationReference> fromTemplateUri(
            @NonNull TemplateUri templateUri, @NonNull NegotiationPhase expectedPhase, String language) {
        Objects.requireNonNull(templateUri, "Template URI must not be null.");
        Objects.requireNonNull(expectedPhase, "Expected negotiation phase must not be null.");
        if (!URI_PREFIX.equals(templateUri.extensionName())) {
            return Optional.empty();
        }
        List<String> segments = templateUri.pathSegments();
        if (segments.size() != 2) {
            return Optional.empty();
        }
        NegotiationType parsedType = parseTypeSegment(segments.get(0));
        if (parsedType == null) {
            return Optional.empty();
        }
        if (!phaseSegmentMatches(segments.get(1), expectedPhase)) {
            return Optional.empty();
        }
        if (!URI_VERSION_SEGMENT.equals(templateUri.templateVersion())) {
            return Optional.empty();
        }
        return Optional.of(new NegotiationReference(parsedType, expectedPhase, language));
    }

    private static NegotiationType parseTypeSegment(String typeSegment) {
        if (!typeSegment.endsWith(TYPE_SEGMENT_SUFFIX)) {
            return null;
        }
        String typeName = typeSegment.substring(0, typeSegment.length() - TYPE_SEGMENT_SUFFIX.length());
        for (NegotiationType candidate : NegotiationType.values()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(typeName)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean phaseSegmentMatches(String phaseSegment, NegotiationPhase expectedPhase) {
        if (!"propose".equals(phaseSegment) && !"accept-reject".equals(phaseSegment)) {
            return false;
        }
        return phaseSegment.equals(expectedPhase.uriSegment());
    }
}
