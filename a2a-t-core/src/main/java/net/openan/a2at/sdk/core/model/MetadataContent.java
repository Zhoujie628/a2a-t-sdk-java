package net.openan.a2at.sdk.core.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Successful result of an A2A-T prompt generation call.
 *
 * @param templateUri URI of the template the message was generated from
 * @param promptText rendered prompt text
 * @param extensionUri TMF extension URI under which the message travels in A2A-T metadata
 * @param negotiationContext negotiation session context; {@code null} for non-negotiation messages
 * @since 2026-08
 */
public record MetadataContent(
        @Nullable String templateUri,
        @Nullable String promptText,
        @NonNull String extensionUri,
        @Nullable NegotiationContext negotiationContext) {

    /** Metadata key carrying the template URI alongside the message itself. */
    public static final String TEMPLATE_URI_METADATA_KEY = "templateUri";

    /** Metadata key carrying the negotiation session context alongside the message itself. */
    public static final String NEGOTIATION_CONTEXT_METADATA_KEY = "negotiationContext";

    /**
     * Builds a metadata content without a negotiation context.
     *
     * @param templateUri URI of the template the message was generated from
     * @param promptText rendered prompt text
     * @param extensionUri TMF extension URI under which the message travels in A2A-T metadata
     */
    public MetadataContent(@Nullable String templateUri, @Nullable String promptText, @NonNull String extensionUri) {
        this(templateUri, promptText, extensionUri, null);
    }

    /**
     * Builds the A2A-T metadata map for this generated message.
     *
     * <p>The returned map always contains the extension URI mapping to the rendered message and {@code templateUri}
     * mapping to the template URI, in that order. When a negotiation context is present it additionally carries
     * {@code negotiationContext} mapping to a nested map with the {@code id}, {@code round}, and {@code maxRounds}
     * fields, in that order; when the context carries a performative the nested map gains a fourth key
     * {@code performative} holding the upper-case performative name. Non-negotiation messages omit the
     * {@code negotiationContext} key entirely. Repeated calls return equal maps.
     *
     * @return newly built metadata map with the extension URI, {@code templateUri}, and, for negotiation messages,
     *     {@code negotiationContext} keys
     */
    public @NonNull Map<String, Object> buildMetadataContent() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(extensionUri, promptText);
        metadata.put(TEMPLATE_URI_METADATA_KEY, templateUri);
        if (negotiationContext != null) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("id", negotiationContext.id());
            context.put("round", negotiationContext.round());
            context.put("maxRounds", negotiationContext.maxRounds());
            if (negotiationContext.performative() != null) {
                context.put("performative", negotiationContext.performative().name());
            }
            metadata.put(NEGOTIATION_CONTEXT_METADATA_KEY, context);
        }
        return metadata;
    }
}
