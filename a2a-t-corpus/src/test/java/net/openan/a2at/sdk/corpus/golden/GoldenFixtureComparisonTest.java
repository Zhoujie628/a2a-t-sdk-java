package net.openan.a2at.sdk.corpus.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.corpus.golden.GoldenInputs.GoldenCase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Locks the golden fixture set of the negotiation content layer against the real built-in resources.
 *
 * <p>Every golden fixture is rendered deterministically from the fixed {@link GoldenInputs} through an orchestrator
 * wired with the built-in templates and vocabulary — no LLM client is involved. The rendered text must match the
 * committed fixture file byte for byte, so any drift of the templates, the vocabulary or the rendering pipeline fails
 * this test and requires a reviewed fixture revision. This test also pins the MetadataContent contract, the determinism
 * of the from-data generation and the zero-LLM guarantee of the from-data variants.
 */
class GoldenFixtureComparisonTest {

    private static final String EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1";

    private static final String TEMPLATE_URI_METADATA_KEY = MetadataContent.TEMPLATE_URI_METADATA_KEY;

    private static final String NEGOTIATION_CONTEXT_METADATA_KEY = MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY;

    static Stream<Arguments> goldenCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String language : GoldenInputs.LANGUAGES) {
            for (GoldenCase goldenCase : GoldenCase.values()) {
                cases.add(Arguments.of(goldenCase, language));
            }
        }
        return cases.stream();
    }

    /** Renders one fixture and returns its metadata content, asserting the URI and extension contract up front. */
    private static MetadataContent render(GoldenCase goldenCase, String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);
        MetadataContent result = goldenCase.generate(orchestrator, language);
        assertEquals(goldenCase.templateUri(), result.templateUri());
        assertEquals(EXTENSION_URI, result.extensionUri());
        return result;
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .build();
    }

    private static String readGoldenFixture(GoldenCase goldenCase, String language) {
        String resourcePath = goldenCase.goldenResourcePath(language);
        InputStream stream = GoldenFixtureComparisonTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "Golden fixture must exist on the test classpath: " + resourcePath);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new AssertionError("Failed to read golden fixture " + resourcePath, exception);
        }
    }

    @ParameterizedTest(name = "{0} [{1}] matches golden fixture byte for byte")
    @MethodSource("goldenCases")
    void rendersByteIdenticalToTheGoldenFixture(GoldenCase goldenCase, String language) {
        MetadataContent result = render(goldenCase, language);

        assertEquals(readGoldenFixture(goldenCase, language), result.promptText());
    }

    @ParameterizedTest(name = "{0} [{1}] metadata contract")
    @MethodSource("goldenCases")
    void buildsTheMetadataMapWithExactlyThreeEntries(GoldenCase goldenCase, String language) {
        MetadataContent result = render(goldenCase, language);

        Map<String, Object> metadata = result.buildMetadataContent();
        assertEquals(3, metadata.size());
        assertEquals(result.promptText(), metadata.get(EXTENSION_URI));
        assertEquals(goldenCase.templateUri(), metadata.get(TEMPLATE_URI_METADATA_KEY));
        assertEquals(goldenCase.context(), result.negotiationContext());
        Iterator<String> keyOrder = metadata.keySet().iterator();
        assertEquals(EXTENSION_URI, keyOrder.next());
        assertEquals(TEMPLATE_URI_METADATA_KEY, keyOrder.next());
        assertEquals(NEGOTIATION_CONTEXT_METADATA_KEY, keyOrder.next());
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedContext = (Map<String, Object>) metadata.get(NEGOTIATION_CONTEXT_METADATA_KEY);
        assertEquals(goldenCase.context().id(), nestedContext.get("id"));
        assertEquals(goldenCase.context().round(), nestedContext.get("round"));
        assertEquals(goldenCase.context().maxRounds(), nestedContext.get("maxRounds"));
        assertEquals(metadata, result.buildMetadataContent());
    }

    @ParameterizedTest(name = "{0} [{1}] deterministic for the same input")
    @MethodSource("goldenCases")
    void rendersTheSameInputDeterministically(GoldenCase goldenCase, String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        MetadataContent first = goldenCase.generate(orchestrator, language);
        MetadataContent second = goldenCase.generate(orchestrator, language);

        assertEquals(first.promptText(), second.promptText());
        assertEquals(first, second);
    }

    @ParameterizedTest(name = "{0} [{1}] structural invariants")
    @MethodSource("goldenCases")
    void keepsTheStructuralInvariantsOfRenderedMessages(GoldenCase goldenCase, String language) {
        MetadataContent result = render(goldenCase, language);
        String promptText = result.promptText();

        String contextTitle = "zh-CN".equals(language) ? "协商上下文" : "Negotiation Context";
        assertFalse(promptText.contains(contextTitle), "the context section must not be rendered into the message");
        assertFalse(promptText.contains("- id: " + GoldenInputs.SESSION_ID), "context lines must not be rendered");
        assertEquals(goldenCase.context(), result.negotiationContext(), "the context travels in the metadata");
        assertFalse(promptText.endsWith("\n"), "message must not end with a newline");
        assertFalse(promptText.contains("\n\n\n"), "sections must be joined by exactly one blank line");
        assertFalse(promptText.contains("{{"), "no unreplaced slot placeholder may remain");
        assertFalse(promptText.contains("<!--"), "the leading template description comment must be dropped");
        if ("zh-CN".equals(language)) {
            assertFalse(promptText.contains("要求："), "template requirement lines must not enter the message");
        } else {
            assertFalse(promptText.contains("Requirements:"), "template requirement lines must not enter the message");
        }
        for (String block : promptText.split("\n\n")) {
            assertTrue(block.startsWith("## "), "every rendered block must be one titled section");
        }
    }

    /**
     * Proves that the from-data variants never touch the LLM: all ten type/phase combinations (including the common
     * abort fixture) of both languages run against a counting LLM client that would record every call.
     */
    @ParameterizedTest(name = "from-data generation never calls the LLM [{0}]")
    @ValueSource(strings = {GoldenInputs.ZH_CN, GoldenInputs.EN_US})
    void fromDataGenerationNeverCallsTheLlm(String language) {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .llmClient(llm)
                .build();

        for (GoldenCase goldenCase : GoldenCase.values()) {
            MetadataContent result = goldenCase.generate(orchestrator, language);
            assertFalse(result.promptText().isBlank());
        }

        assertEquals(0, llm.calls, "from-data generation must be deterministic and LLM-free");
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
            throw new AssertionError("The from-data generation must never call the LLM client");
        }
    }
}
