package net.openan.a2at.sdk.client.model;

import java.util.Map;
import java.util.Objects;

/**
 * Metadata content used in A2A-T prompt generation.
 *
 * @param templateUri URI of the template
 * @param promptText rendered prompt text
 * @param extensionUri URI of the extension
 * @since 2026-08
 */
public record MetadataContent(String templateUri, String promptText, String extensionUri) {

    public MetadataContent {
        Objects.requireNonNull(templateUri, "templateUri");
        Objects.requireNonNull(promptText, "promptText");
        Objects.requireNonNull(extensionUri, "extensionUri");
    }

    /**
     * Builds a metadata map keyed by the extension URI and template URI.
     *
     * @return metadata map containing the prompt text under the extension URI key and the template URI under the
     *         {@code template_uri} key
     */
    public Map<String, String> buildMetadataContent() {
        return Map.of(extensionUri, promptText, "template_uri", templateUri);
    }
}