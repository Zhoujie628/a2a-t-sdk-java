package net.openan.a2at.sdk.negotiation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler;
import org.junit.jupiter.api.Test;

class MetadataContentTest {

    private static final String TEMPLATE_URI = "Negotiation-T/v1/information-negotiation/propose";

    @Test
    void recordExposesAllThreeComponents() {
        MetadataContent content = new MetadataContent(TEMPLATE_URI, "rendered message", "https://example/ext");

        assertEquals(TEMPLATE_URI, content.templateUri());
        assertEquals("rendered message", content.promptText());
        assertEquals("https://example/ext", content.extensionUri());
    }

    @Test
    void convenienceConstructorAppliesNegotiationExtensionUri() {
        MetadataContent content = new MetadataContent(TEMPLATE_URI, "rendered message");

        assertEquals(
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1",
                content.extensionUri());
        assertEquals(NegotiationHandler.NEGOTIATION_T_URI, content.extensionUri());
    }

    @Test
    void buildMetadataContentReturnsExactlyTwoDeterministicEntries() {
        MetadataContent content = new MetadataContent(TEMPLATE_URI, "rendered message");

        Map<String, String> metadata = content.buildMetadataContent();

        assertEquals(2, metadata.size());
        assertEquals("rendered message", metadata.get(content.extensionUri()));
        assertEquals(TEMPLATE_URI, metadata.get("template_uri"));
        assertEquals(content.buildMetadataContent(), metadata);
        assertTrue(metadata.containsKey(NegotiationHandler.NEGOTIATION_T_URI));
    }
}
