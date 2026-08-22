package net.openan.a2at.sample.private_line_complaint.negotiation.shared;

import java.util.Map;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.TemplateUri;

/** Reads and validates Negotiation-T metadata received over A2A. */
public final class NegotiationMetadataReader {

    private NegotiationMetadataReader() {
    }

    public static String readPrompt(Map<String, ?> metadata, TemplateUri expectedTemplateUri) {
        if (metadata == null) {
            throw new IllegalArgumentException("Negotiation metadata is required");
        }
        String templateUri = stringValue(metadata.get(MetadataContent.TEMPLATE_URI_METADATA_KEY));
        if (!expectedTemplateUri.uri().equals(templateUri)) {
            throw new IllegalArgumentException(
                    "Unexpected negotiation template URI: expected=" + expectedTemplateUri.uri() + ", actual=" + templateUri);
        }
        String prompt = stringValue(metadata.get(ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI));
        if (prompt.isBlank()) {
            prompt = stringValue(metadata.get(ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI_NL));
        }
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("Negotiation-T prompt metadata is required");
        }
        return prompt;
    }

    public static void requireExtension(String extensionHeader) {
        if (!ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI.equals(extensionHeader)) {
            throw new IllegalArgumentException("A2A-Extensions must contain the Negotiation-T extension URI");
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
