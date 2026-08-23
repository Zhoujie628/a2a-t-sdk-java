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
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
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
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the template-URI entry matrix of the from-data generation.
 *
 * <p>The seven built-in URIs (six typed negotiation templates plus the common abort template) address their templates
 * and render outputs identical to the golden fixtures; a well-formed
 * URI that resolves to no template fails with the code {@code template_not_found} before any LLM call; a typed URI
 * that does not address a negotiation template of the expected phase (wrong extension name, version, type segment,
 * phase segment or separator, including the underscore misspelling of the type segment) fails as a programming error
 * pointing at {@code templateUri}, while structural malformation is impossible by construction of
 * {@link TemplateUri}; and a template placed under a local resource root overrides the built-in template addressed
 * by the same URI.
 */
class TemplateUriEntryMatrixTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final String BUILTIN_INFORMATION_PROPOSE_TEMPLATE =
            "templates/Negotiation-T/information-negotiation/propose/v1/zh-CN/template.md";

    /**
     * Entry (a): every one of the seven built-in URIs (six typed templates plus the common abort template) reaches its
     * template and renders output byte-identical to the golden fixtures locked by the comparison test.
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
            assertEquals(7, casesByUri.size(), "the built-in template set has exactly seven URIs");
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
     * Entry (c): every typed URI that does not address a negotiation template of the expected phase — wrong extension
     * name, version, type segment, phase segment and separator (underscore misspelling), plus unknown types and
     * unknown phase segments — fails as a programming error with an {@link IllegalArgumentException} pointing at the
     * template URI, without any LLM call. Structurally malformed URIs cannot exist as {@link TemplateUri} values.
     */
    @ParameterizedTest(name = "non-addressing URI [{0}] is rejected as a templateUri programming error")
    @MethodSource("nonAddressingTemplateUris")
    void nonAddressingUriIsRejectedAsATemplateUriProgrammingError(TemplateUri templateUri) {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.generateProposeFromData(informationProposeData(), templateUri));

        assertTrue(
                failure.getMessage().contains(
                        "Template URI does not address a negotiation template of the expected phase PROPOSE"),
                "the failure must point at the template URI but was: " + failure.getMessage());
        assertEquals(0, llm.calls);
    }

    static List<TemplateUri> nonAddressingTemplateUris() {
        return List.of(
                TemplateUri.of("Task-T", "information-negotiation", "propose"),
                TemplateUri.of("Negotiation-T", List.of("information-negotiation", "propose"), "v2"),
                TemplateUri.of("Negotiation-T", "information", "propose"),
                TemplateUri.of("Negotiation-T", "information_negotiation", "propose"),
                TemplateUri.of("Negotiation-T", "unknown-negotiation", "propose"),
                TemplateUri.of("Negotiation-T", "information-negotiation", "propose-x"),
                TemplateUri.of("Negotiation-T", "information-negotiation", "accept"));
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
                new InformationProposeContent(List.of(new NegotiationItem("区域", "松山湖")), null));
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
