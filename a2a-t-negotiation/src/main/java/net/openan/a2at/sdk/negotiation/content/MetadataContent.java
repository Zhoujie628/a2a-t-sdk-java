package net.openan.a2at.sdk.negotiation.content;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler;

/**
 * Successful result of a negotiation message generation call.
 *
 * @param templateUri URI of the template the message was generated from
 * @param promptText rendered negotiation message text
 * @param extensionUri TMF extension URI under which the message travels in A2A-T metadata
 * @since 2026-06
 */
public record MetadataContent(String templateUri, String promptText, String extensionUri) {

    /** Metadata key carrying the template URI alongside the message itself. */
    public static final String TEMPLATE_URI_METADATA_KEY = "template_uri";

    /**
     * Creates a metadata content result carrying the standard negotiation extension URI.
     *
     * @param templateUri URI of the template the message was generated from
     * @param promptText rendered negotiation message text
     */
    public MetadataContent(String templateUri, String promptText) {
        this(templateUri, promptText, NegotiationHandler.NEGOTIATION_T_URI);
    }

    /**
     * Builds the A2A-T metadata map for this generated message.
     *
     * <p>The returned map contains exactly two keys in a fixed order: the extension URI mapping to the rendered
     * message, and {@code template_uri} mapping to the template URI. Repeated calls return equal maps.
     *
     * @return newly built metadata map with exactly the extension URI and {@code template_uri} keys
     */
    public Map<String, String> buildMetadataContent() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(extensionUri, promptText);
        metadata.put(TEMPLATE_URI_METADATA_KEY, templateUri);
        return metadata;
    }
}
