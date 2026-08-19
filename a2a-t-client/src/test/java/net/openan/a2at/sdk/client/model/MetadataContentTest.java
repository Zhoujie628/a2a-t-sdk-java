package net.openan.a2at.sdk.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataContentTest {

    @Test
    void exposesAllFieldsViaAccessorMethods() {
        MetadataContent content = new MetadataContent("template-uri", "prompt-text", "extension-uri");

        assertEquals("template-uri", content.templateUri());
        assertEquals("prompt-text", content.promptText());
        assertEquals("extension-uri", content.extensionUri());
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
    void buildMetadataContentReturnsMapWithExtensionUriAndTemplateUri() {
        MetadataContent content = new MetadataContent(
                "energy_saving", "Site: Site A",
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1");

        Map<String, String> result = content.buildMetadataContent();

        assertEquals(2, result.size());
        assertEquals("Site: Site A", result.get(
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1"));
        assertEquals("energy_saving", result.get("template_uri"));
    }

    @Test
    void buildMetadataContentReturnsNullWhenAnyFieldIsNull() {
        MetadataContent content = new MetadataContent(null, null, "extension-uri");
        assertEquals(null, content.buildMetadataContent());
    }
}
