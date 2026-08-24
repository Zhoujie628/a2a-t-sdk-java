package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import org.junit.jupiter.api.Test;

class MetadataContentTest {

    private static final String TEMPLATE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri();

    private static final NegotiationContext CONTEXT =
            new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 1, 5);

    @Test
    void recordExposesAllComponents() {
        MetadataContent content = new MetadataContent(TEMPLATE_URI, "rendered message", "https://example/ext");

        assertEquals(TEMPLATE_URI, content.templateUri());
        assertEquals("rendered message", content.promptText());
        assertEquals("https://example/ext", content.extensionUri());
        assertEquals(null, content.negotiationContext());
    }

    @Test
    void recordsWithSameValuesAreEqual() {
        MetadataContent first = new MetadataContent("template-uri", "prompt-text", "extension-uri");
        MetadataContent second = new MetadataContent("template-uri", "prompt-text", "extension-uri");

        assertEquals(first, second);
        assertNotSame(first, second);
    }

    @Test
    void exposesNoStaticFactoryMethods() {
        assertThrows(NoSuchMethodException.class, () -> MetadataContent.class.getMethod("success"));
        assertThrows(NoSuchMethodException.class, () -> MetadataContent.class.getMethod("failure"));
    }

    @Test
    void buildMetadataContentReturnsExactlyTwoDeterministicEntriesWithoutContext() {
        MetadataContent content = new MetadataContent(TEMPLATE_URI, "rendered message", "https://example/ext");

        Map<String, Object> metadata = content.buildMetadataContent();

        assertEquals(2, metadata.size());
        assertEquals("rendered message", metadata.get(content.extensionUri()));
        assertEquals(TEMPLATE_URI, metadata.get(MetadataContent.TEMPLATE_URI_METADATA_KEY));
        assertEquals(metadata, content.buildMetadataContent());
        assertTrue(metadata.containsKey(content.extensionUri()));
    }

    @Test
    void buildMetadataContentKeepsFixedKeyOrder() {
        MetadataContent content = new MetadataContent(TEMPLATE_URI, "rendered message", "https://example/ext");

        Map<String, Object> metadata = content.buildMetadataContent();

        assertEquals(
                java.util.List.of(content.extensionUri(), MetadataContent.TEMPLATE_URI_METADATA_KEY),
                new java.util.ArrayList<>(metadata.keySet()));
    }

    @Test
    void buildMetadataContentNeverReturnsNullEvenWithNullFields() {
        MetadataContent content = new MetadataContent(null, null, "extension-uri");

        Map<String, Object> metadata = content.buildMetadataContent();

        assertEquals(2, metadata.size());
        assertTrue(metadata.containsKey(MetadataContent.TEMPLATE_URI_METADATA_KEY));
    }

    @Test
    void buildMetadataContentCarriesNegotiationContextAsThirdKey() {
        MetadataContent content =
                new MetadataContent(TEMPLATE_URI, "rendered message", "https://example/ext", CONTEXT);

        Map<String, Object> metadata = content.buildMetadataContent();

        assertEquals(3, metadata.size());
        assertEquals(
                java.util.List.of(
                        content.extensionUri(),
                        MetadataContent.TEMPLATE_URI_METADATA_KEY,
                        MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY),
                new java.util.ArrayList<>(metadata.keySet()));
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedContext =
                (Map<String, Object>) metadata.get(MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY);
        assertEquals(CONTEXT.id(), nestedContext.get("id"));
        assertEquals(CONTEXT.round(), nestedContext.get("round"));
        assertEquals(CONTEXT.maxRounds(), nestedContext.get("maxRounds"));
        assertEquals(metadata, content.buildMetadataContent());
    }
}
