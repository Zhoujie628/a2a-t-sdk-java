package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.validation.StandardTemplates;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs.GoldenCase;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies the template-URI entry matrix of the from-data generation.
 *
 * <p>The six built-in URIs address their templates and render outputs identical to the golden fixtures; a well-formed
 * URI that resolves to no template fails with the code {@code template_not_found} before any LLM call; a malformed URI
 * (wrong segment count, prefix, version, suffix, separator or enumeration value, including the underscore misspelling
 * of the type segment) fails as a programming error pointing at {@code templateUri}; and a template placed under a
 * local resource root overrides the built-in template addressed by the same URI.
 */
class TemplateUriEntryMatrixTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri();

    private static final String BUILTIN_INFORMATION_PROPOSE_TEMPLATE =
            "templates/Negotiation-T/v1/information-negotiation/propose/zh-CN/template.md";

    /**
     * Entry (a): every one of the six built-in URIs reaches its template and renders output byte-identical to the
     * golden fixtures locked by the comparison test.
     */
    @Test
    void everyBuiltInUriRendersItsGoldenFixture() {
        for (String language : GoldenInputs.LANGUAGES) {
            NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                    .language(language)
                    .build();
            Map<String, List<GoldenCase>> casesByUri = new LinkedHashMap<>();
            for (GoldenCase goldenCase : GoldenCase.values()) {
                casesByUri
                        .computeIfAbsent(goldenCase.templateUri(), uri -> new ArrayList<>())
                        .add(goldenCase);
            }
            assertEquals(6, casesByUri.size(), "the built-in template set has exactly six URIs");
            for (Map.Entry<String, List<GoldenCase>> entry : casesByUri.entrySet()) {
                for (GoldenCase goldenCase : entry.getValue()) {
                    MetadataContent result = goldenCase.generate(orchestrator);
                    assertEquals(entry.getKey(), result.templateUri());
                    assertEquals(readClasspathText(goldenCase.goldenResourcePath(language)), result.promptText());
                }
            }
        }
    }

    /**
     * Entry (b): a well-formed URI that resolves to no template in any resource root fails with the code
     * {@code template_not_found} before any LLM call. With the bundled resources every well-formed v1 URI resolves in
     * both bundled languages, so the both-roots-miss condition is realized by a loader whose every load misses, which
     * is the custom-root scenario of an environment without the built-in templates.
     */
    @Test
    void wellFormedUriMissingInBothRootsFailsWithTemplateNotFound() {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .templateLoader(new MissingTemplateLoader())
                .llmClient(llm)
                .build();

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromData(informationProposeData(), INFORMATION_PROPOSE_URI));

        assertEquals(A2ATErrorCodes.TEMPLATE_NOT_FOUND, failure.getCode());
        assertTrue(
                failure.getMessage() != null && !failure.getMessage().isBlank(),
                "the load failure message must be surfaced");
        assertEquals(0, llm.calls, "template loading happens before any LLM call");
    }

    /**
     * Entry (c): every malformed URI — wrong segment count, prefix, version, type suffix, separator (underscore
     * misspelling), unknown type and invalid phase segment — fails as a programming error with an
     * {@link IllegalArgumentException} pointing at the template URI, without any LLM call.
     */
    @ParameterizedTest(name = "malformed URI [{0}] is rejected as a templateUri programming error")
    @ValueSource(
            strings = {
                "foo",
                "foo/bar",
                "information-negotiation/propose",
                "Negotiation-T/v1/information-negotiation/propose/extra",
                "Task-T/v1/information-negotiation/propose",
                "Negotiation-T/v2/information-negotiation/propose",
                "Negotiation-T/v1/information/propose",
                "Negotiation-T/v1/information_negotiation/propose",
                "Negotiation-T/v1/unknown-negotiation/propose",
                "Negotiation-T/v1/information-negotiation/propose-x",
                "Negotiation-T/v1/information-negotiation/accept"
            })
    void malformedUriIsRejectedAsATemplateUriProgrammingError(String templateUri) {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.generateProposeFromData(informationProposeData(), templateUri));

        assertTrue(
                failure.getMessage().contains("Template URI is malformed or contradicts the expected phase PROPOSE"),
                "the failure must point at the template URI but was: " + failure.getMessage());
        assertEquals(0, llm.calls);
    }

    /**
     * Entry (e): a template placed under a local resource root overrides the built-in template addressed by the same
     * URI, while the same URI without the local root keeps rendering the built-in golden fixture.
     */
    @Test
    void localRootTemplateOverridesTheBuiltInTemplateOfTheSameUri(@TempDir Path tempDir) throws IOException {
        Path customTemplate = tempDir.resolve(BUILTIN_INFORMATION_PROPOSE_TEMPLATE);
        Files.createDirectories(customTemplate.getParent());
        String builtinTemplate = readClasspathText("/prompt_resources/" + BUILTIN_INFORMATION_PROPOSE_TEMPLATE);
        String markerSection = "\n\n## Custom Override Section\nCUSTOM-OVERRIDE-MARKER\n";
        Files.writeString(customTemplate, builtinTemplate + markerSection, StandardCharsets.UTF_8);

        NegotiationGenerationOrchestrator customRootOrchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .localRootDir(tempDir.toString())
                .build();
        String customText = customRootOrchestrator
                .generateProposeFromData(informationProposeData(), INFORMATION_PROPOSE_URI)
                .promptText();
        assertTrue(customText.contains("## Custom Override Section"));
        assertTrue(customText.contains("CUSTOM-OVERRIDE-MARKER"));

        NegotiationGenerationOrchestrator builtinOrchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .build();
        String builtinText =
                GoldenCase.INFORMATION_PROPOSE.generate(builtinOrchestrator).promptText();
        assertFalse(builtinText.contains("CUSTOM-OVERRIDE-MARKER"));
        assertEquals(
                readClasspathText(GoldenCase.INFORMATION_PROPOSE.goldenResourcePath("zh-CN")),
                builtinText,
                "without the local root the built-in golden output remains unchanged");
    }

    private static NegotiationProposeData informationProposeData() {
        return new NegotiationProposeData(
                new NegotiationContext(UUID, 2, 5),
                new InfoProposeContent(List.of(new NegotiationItem("区域", "松山湖")), null));
    }

    private static String readClasspathText(String resourcePath) {
        InputStream stream = TemplateUriEntryMatrixTest.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new AssertionError("Classpath resource must exist: " + resourcePath);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new AssertionError("Failed to read classpath resource " + resourcePath, exception);
        }
    }

    private static final class MissingTemplateLoader implements NegotiationTemplateLoader {

        @Override
        public PromptTemplate load(NegotiationReference reference) {
            throw new ResourceNotFoundException(
                    "Negotiation template does not exist in any resource root.", reference.uri());
        }

        @Override
        public List<PromptTemplate> loadAll() {
            return List.of();
        }
    }

    private static final class CountingClient implements LLMClient {

        private int calls;

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls++;
            throw new AssertionError("The entry matrix must fail before any LLM call");
        }
    }
}
