package net.openan.a2at.sdk.negotiation.resources;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.core.validation.TemplateReference;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;

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
public record NegotiationReference(NegotiationType type, NegotiationPhase phase, String language)
        implements TemplateReference {

    private static final String URI_PREFIX = "Negotiation-T";

    private static final String URI_VERSION_SEGMENT = "v1";

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
     * @return template URI such as {@code Negotiation-T/v1/information-negotiation/propose}
     */
    public String uri() {
        return String.join("/", URI_PREFIX, URI_VERSION_SEGMENT, typeSegment(), phase.uriSegment());
    }

    @Override
    public String extensionName() {
        return URI_PREFIX;
    }

    /**
     * Tries to parse a template URI into a reference, checking it against the expected phase.
     *
     * <p>The URI layer cannot distinguish accept from reject because both share the {@code accept-reject} segment; the
     * expected phase disambiguates the parsed result, which therefore always carries the expected phase.
     *
     * @param templateUri template URI to parse, such as {@code Negotiation-T/v1/target-negotiation/accept-reject}
     * @param expectedPhase API-level phase the caller is operating on; the parsed reference carries this phase
     * @param language locale identifier for the parsed reference
     * @return reference addressed by the URI carrying the expected phase, or an empty result when the URI is null,
     *     blank or malformed (wrong segment count, prefix, version or type segment) or its phase segment does not match
     *     the expected phase
     * @throws NullPointerException if the expected phase is null
     */
    public static Optional<NegotiationReference> tryParse(
            String templateUri, NegotiationPhase expectedPhase, String language) {
        Objects.requireNonNull(expectedPhase, "Expected negotiation phase must not be null.");
        if (templateUri == null || templateUri.isBlank()) {
            return Optional.empty();
        }
        String[] segments = templateUri.strip().split("/");
        if (segments.length != 4) {
            return Optional.empty();
        }
        if (!URI_PREFIX.equals(segments[0])) {
            return Optional.empty();
        }
        if (!URI_VERSION_SEGMENT.equals(segments[1])) {
            return Optional.empty();
        }
        NegotiationType parsedType = parseTypeSegment(segments[2]);
        if (parsedType == null) {
            return Optional.empty();
        }
        if (!phaseSegmentMatches(segments[3], expectedPhase)) {
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
