package net.openan.a2at.sdk.negotiation.resources;

import java.util.Locale;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.negotiation.content.NegotiationContentException;
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
public record NegotiationReference(NegotiationType type, NegotiationPhase phase, String language) {

    private static final String URI_PREFIX = "Negotiation-T";

    private static final String URI_VERSION_SEGMENT = "v1";

    private static final String TYPE_SEGMENT_SUFFIX = "-negotiation";

    /**
     * Validates the reference fields.
     *
     * @throws NegotiationContentException if the type or phase is null or the language is not a simple path segment
     */
    public NegotiationReference {
        if (type == null) {
            throw new NegotiationContentException("Negotiation reference type must not be null.", "type");
        }
        if (phase == null) {
            throw new NegotiationContentException("Negotiation reference phase must not be null.", "phase");
        }
        if (!PathSegments.isSimpleSegment(language)) {
            throw new NegotiationContentException(
                    "Negotiation reference language must be a non-blank simple path segment but was " + language + ".",
                    "language");
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

    /**
     * Parses a template URI into a reference, checking it against the expected phase.
     *
     * <p>The URI layer cannot distinguish accept from reject because both share the {@code accept-reject} segment; the
     * expected phase disambiguates the parsed result, which therefore always carries the expected phase.
     *
     * @param templateUri template URI to parse, such as {@code Negotiation-T/v1/target-negotiation/accept-reject}
     * @param expectedPhase API-level phase the caller is operating on; the parsed reference carries this phase
     * @param language locale identifier for the parsed reference
     * @return reference addressed by the URI, carrying the expected phase
     * @throws NegotiationContentException with field {@code templateUri} if the URI is null, blank or malformed
     */
    public static NegotiationReference parse(String templateUri, NegotiationPhase expectedPhase, String language) {
        if (expectedPhase == null) {
            throw new NegotiationContentException("Expected negotiation phase must not be null.", "phase");
        }
        if (templateUri == null || templateUri.isBlank()) {
            throw new NegotiationContentException("Template URI must not be blank.", "templateUri");
        }
        String[] segments = templateUri.strip().split("/");
        if (segments.length != 4) {
            throw new NegotiationContentException(
                    "Template URI must contain exactly 4 slash-separated segments but has " + segments.length + ": "
                            + templateUri + ".",
                    "templateUri");
        }
        if (!URI_PREFIX.equals(segments[0])) {
            throw new NegotiationContentException(
                    "Template URI first segment must be " + URI_PREFIX + " but was " + segments[0] + ": " + templateUri
                            + ".",
                    "templateUri");
        }
        if (!URI_VERSION_SEGMENT.equals(segments[1])) {
            throw new NegotiationContentException(
                    "Template URI version segment must be " + URI_VERSION_SEGMENT + " but was " + segments[1] + ": "
                            + templateUri + ".",
                    "templateUri");
        }
        NegotiationType parsedType = parseTypeSegment(segments[2], templateUri);
        parsePhaseSegment(segments[3], expectedPhase, templateUri);
        return new NegotiationReference(parsedType, expectedPhase, language);
    }

    private static NegotiationType parseTypeSegment(String typeSegment, String templateUri) {
        if (!typeSegment.endsWith(TYPE_SEGMENT_SUFFIX)) {
            throw new NegotiationContentException(
                    "Template URI type segment must end with " + TYPE_SEGMENT_SUFFIX + " using hyphens, not"
                            + " underscores, but was " + typeSegment + ": " + templateUri + ".",
                    "templateUri");
        }
        String typeName = typeSegment.substring(0, typeSegment.length() - TYPE_SEGMENT_SUFFIX.length());
        for (NegotiationType candidate : NegotiationType.values()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(typeName)) {
                return candidate;
            }
        }
        throw new NegotiationContentException(
                "Template URI has an unknown negotiation type " + typeName + " in segment " + typeSegment + ": "
                        + templateUri + ".",
                "templateUri");
    }

    private static void parsePhaseSegment(String phaseSegment, NegotiationPhase expectedPhase, String templateUri) {
        if (!"propose".equals(phaseSegment) && !"accept-reject".equals(phaseSegment)) {
            throw new NegotiationContentException(
                    "Template URI phase segment must be propose or accept-reject but was " + phaseSegment + ": "
                            + templateUri + ".",
                    "templateUri");
        }
        if (!phaseSegment.equals(expectedPhase.uriSegment())) {
            throw new NegotiationContentException(
                    "Template URI phase segment " + phaseSegment + " does not match the expected phase " + expectedPhase
                            + " (" + expectedPhase.uriSegment() + "): " + templateUri + ".",
                    "templateUri");
        }
    }
}
