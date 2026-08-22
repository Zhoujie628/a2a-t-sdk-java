package net.openan.a2at.sample.private_line_complaint.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationMetadataReader;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleFlow;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.MetadataContent;
import org.junit.jupiter.api.Test;

class NegotiationMetadataReaderTest {

    @Test
    void readsCanonicalNegotiationMetadata() {
        Map<String, String> metadata = Map.of(
                ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI,
                "prompt",
                MetadataContent.TEMPLATE_URI_METADATA_KEY,
                NegotiationSampleFlow.PROPOSE_TEMPLATE_URI.uri());

        assertEquals(
                "prompt",
                NegotiationMetadataReader.readPrompt(metadata, NegotiationSampleFlow.PROPOSE_TEMPLATE_URI));
    }

    @Test
    void rejectsMissingPromptAndWrongTemplateOrExtension() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NegotiationMetadataReader.readPrompt(
                        Map.of(
                                MetadataContent.TEMPLATE_URI_METADATA_KEY,
                                NegotiationSampleFlow.PROPOSE_TEMPLATE_URI.uri()),
                        NegotiationSampleFlow.PROPOSE_TEMPLATE_URI));
        assertThrows(
                IllegalArgumentException.class,
                () -> NegotiationMetadataReader.readPrompt(
                        Map.of(
                                ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI,
                                "prompt",
                                MetadataContent.TEMPLATE_URI_METADATA_KEY,
                                NegotiationSampleFlow.ENDING_TEMPLATE_URI.uri()),
                        NegotiationSampleFlow.PROPOSE_TEMPLATE_URI));
        assertThrows(IllegalArgumentException.class, () -> NegotiationMetadataReader.requireExtension("Task-T/v1"));
    }
}
