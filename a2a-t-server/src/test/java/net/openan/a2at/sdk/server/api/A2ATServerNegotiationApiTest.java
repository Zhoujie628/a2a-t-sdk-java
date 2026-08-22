package net.openan.a2at.sdk.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.validation.StandardTemplates;
import net.openan.a2at.sdk.core.validation.TemplateUri;
import net.openan.a2at.sdk.server.A2ATServer;
import org.junit.jupiter.api.Test;

class A2ATServerNegotiationApiTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final String INFORMATION_PROPOSE_URI = INFORMATION_PROPOSE.uri();

    @Test
    void generatesInformationProposeFromDataWithBuiltinChineseTemplates() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv("zh-CN"));

        MetadataContent result = server.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5),
                        new InfoProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE);

        assertEquals(INFORMATION_PROPOSE_URI, result.templateUri());
        assertFalse(result.promptText().isBlank());
        assertTrue(result.promptText().contains("- id: " + UUID));
        assertTrue(result.promptText().contains("协商上下文"));
        Map<String, String> metadata = result.buildMetadataContent();
        assertEquals(2, metadata.size());
        assertEquals(result.promptText(), metadata.get(result.extensionUri()));
        assertEquals(result.templateUri(), metadata.get(MetadataContent.TEMPLATE_URI_METADATA_KEY));
    }

    @Test
    void generatesInformationProposeFromDataWithBuiltinEnglishTemplates() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv("en-US"));

        MetadataContent result = server.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5),
                        new InfoProposeContent(List.of(new NegotiationItem("Region", "Songshan Lake")), null)),
                INFORMATION_PROPOSE);

        assertEquals(INFORMATION_PROPOSE_URI, result.templateUri());
        assertFalse(result.promptText().isBlank());
        assertTrue(result.promptText().contains("Negotiation Context"));
        assertTrue(result.promptText().contains("Required Information Items"));
    }

    @Test
    void listsAllSixNegotiationTemplatesPerLanguage() throws IOException {
        assertEquals(
                6, new A2ATServer(writeEnv("zh-CN")).getNegotiationPrompts().size());
        assertEquals(
                6, new A2ATServer(writeEnv("en-US")).getNegotiationPrompts().size());
    }

    @Test
    void queriesSingleNegotiationTemplateWithoutThrowing() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv("zh-CN"));

        assertTrue(server.getNegotiationPrompt(INFORMATION_PROPOSE).isPresent());
        assertTrue(server
                .getNegotiationPrompt(StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT)
                .isPresent());
        assertFalse(server
                .getNegotiationPrompt(TemplateUri.of(
                        StandardTemplates.NEGOTIATION_EXTENSION_NAME,
                        TemplateUri.DEFAULT_TEMPLATE_VERSION,
                        "unknown-negotiation",
                        "propose"))
                .isPresent());
        PromptTemplate template = server.getNegotiationPrompt(INFORMATION_PROPOSE).orElseThrow();
        assertEquals(INFORMATION_PROPOSE_URI, template.uri());
        assertFalse(template.content().isBlank());
    }

    private static Path writeEnv(String language) throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-server-negotiation-env");
        Path envFile = tempDir.resolve("server.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=%s
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=openai
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                A2AT_PROMPT_COMPLIANCE_ENABLED=false
                """
                        .formatted(language));
        return envFile;
    }
}
