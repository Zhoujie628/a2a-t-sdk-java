package net.openan.a2at.sdk.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.resources.PromptTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class A2ATClientNegotiationApiTest {

    private static final String TEST_MOCK_PROVIDER = "test-negotiation-mock";

    @BeforeAll
    static void registerMockProvider() {
        if (!LLMClientFactory.availableProviders().contains(TEST_MOCK_PROVIDER)) {
            LLMClientFactory.register(TEST_MOCK_PROVIDER, RecordingClient.class);
        }
    }

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String INFORMATION_PROPOSE_URI = "Negotiation-T/v1/information-negotiation/propose";

    @Test
    void generatesInformationProposeFromDataWithBuiltinChineseTemplates() throws IOException {
        A2ATClient client = new A2ATClient(writeEnv("zh-CN"));

        MetadataContent result = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5),
                        new InfoProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);

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
        A2ATClient client = new A2ATClient(writeEnv("en-US"));

        MetadataContent result = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5),
                        new InfoProposeContent(List.of(new NegotiationItem("Region", "Songshan Lake")), null)),
                INFORMATION_PROPOSE_URI);

        assertEquals(INFORMATION_PROPOSE_URI, result.templateUri());
        assertFalse(result.promptText().isBlank());
        assertTrue(result.promptText().contains("Negotiation Context"));
        assertTrue(result.promptText().contains("Required Information Items"));
    }

    @Test
    void listsAllSixNegotiationTemplatesPerLanguage() throws IOException {
        assertEquals(
                6, new A2ATClient(writeEnv("zh-CN")).getNegotiationPrompts().size());
        assertEquals(
                6, new A2ATClient(writeEnv("en-US")).getNegotiationPrompts().size());
    }

    @Test
    void queriesSingleNegotiationTemplateWithoutThrowing() throws IOException {
        A2ATClient client = new A2ATClient(writeEnv("zh-CN"));

        assertTrue(client.getNegotiationPrompt(INFORMATION_PROPOSE_URI).isPresent());
        assertTrue(client.getNegotiationPrompt("Negotiation-T/v1/feasibility-negotiation/accept-reject")
                .isPresent());
        assertFalse(client.getNegotiationPrompt("not-a-template-uri").isPresent());
        PromptTemplate template =
                client.getNegotiationPrompt(INFORMATION_PROPOSE_URI).orElseThrow();
        assertEquals(INFORMATION_PROPOSE_URI, template.uri());
        assertFalse(template.content().isBlank());
    }

    private static Path writeEnv(String language) throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-client-negotiation-env");
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=%s
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=test-negotiation-mock
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(language));
        return envFile;
    }

    public static final class RecordingClient implements LLMClient {

        private final LLMClientConfig config;

        public RecordingClient(LLMClientConfig config) {
            this.config = config;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse("{}", config.model(), Map.of(), Map.of());
        }
    }
}
