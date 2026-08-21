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
 * @since 2026-08
 */
public record MetadataContent(@Nullable String templateUri, @Nullable String promptText, @NonNull String extensionUri) {

    /** Metadata key carrying the template URI alongside the message itself. */
    public static final String TEMPLATE_URI_METADATA_KEY = "templateUri";

    /**
     * Builds the A2A-T metadata map for this generated message.
     *
     * <p>The returned map contains exactly two keys in a fixed order: the extension URI mapping to the rendered
     * message, and {@code templateUri} mapping to the template URI. Repeated calls return equal maps.
     *
     * @return newly built metadata map with exactly the extension URI and {@code templateUri} keys
     */
    public @NonNull Map<String, String> buildMetadataContent() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(extensionUri, promptText);
        metadata.put(TEMPLATE_URI_METADATA_KEY, templateUri);
        return metadata;
    }
}
