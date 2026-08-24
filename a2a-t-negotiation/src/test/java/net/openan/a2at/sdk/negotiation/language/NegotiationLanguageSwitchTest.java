package net.openan.a2at.sdk.negotiation.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs.GoldenCase;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Locks the language switching of the negotiation content layer.
 *
 * <p>The same typed input is rendered through the built-in resources of each bundled language: the zh-CN pipeline and
 * the en-US pipeline must each reproduce that language's golden fixture byte for byte, and the two results must differ
 * because the section titles, labels and punctuation come from the per-language vocabulary. The template query methods
 * must answer with the templates of the configured language only.
 */
class NegotiationLanguageSwitchTest {

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    static Stream<GoldenCase> goldenCases() {
        return Stream.of(GoldenCase.values());
    }

    @ParameterizedTest(name = "the same input renders each language golden fixture [{0}]")
    @MethodSource("goldenCases")
    void sameInputRendersTheRespectiveGoldenFixturePerLanguage(GoldenCase goldenCase) {
        NegotiationGenerationOrchestrator zhCnOrchestrator = orchestrator(GoldenInputs.ZH_CN);
        NegotiationGenerationOrchestrator enUsOrchestrator = orchestrator(GoldenInputs.EN_US);

        MetadataContent zhCnResult = goldenCase.generate(zhCnOrchestrator, GoldenInputs.ZH_CN);
        MetadataContent enUsResult = goldenCase.generate(enUsOrchestrator, GoldenInputs.EN_US);

        assertEquals(readGoldenFixture(goldenCase, GoldenInputs.ZH_CN), zhCnResult.promptText());
        assertEquals(readGoldenFixture(goldenCase, GoldenInputs.EN_US), enUsResult.promptText());
        assertNotEquals(zhCnResult.promptText(), enUsResult.promptText());
        assertEquals(goldenCase.templateUri(), zhCnResult.templateUri());
        assertEquals(goldenCase.templateUri(), enUsResult.templateUri());
    }

    @ParameterizedTest(name = "the query methods list the templates of one language only [{0}]")
    @ValueSource(strings = {GoldenInputs.ZH_CN, GoldenInputs.EN_US})
    void templateQueriesReturnTheTemplatesOfTheConfiguredLanguage(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);
        String requirementLabel = GoldenInputs.ZH_CN.equals(language) ? "要求：" : "Requirement:";
        String otherLanguageLabel = GoldenInputs.ZH_CN.equals(language) ? "Requirement:" : "要求：";

        List<PromptTemplate> templates = orchestrator.getNegotiationPrompts();

        assertEquals(7, templates.size());
        for (PromptTemplate template : templates) {
            assertTrue(
                    template.content().contains(requirementLabel),
                    template.templateUri().uri() + " must be a " + language + " template");
            assertTrue(
                    !template.content().contains(otherLanguageLabel),
                    template.templateUri().uri() + " must not leak the other language");
        }

        PromptTemplate queriedTemplate =
                orchestrator.getNegotiationPrompt(INFORMATION_PROPOSE_URI).orElseThrow();
        assertTrue(queriedTemplate.content().contains(requirementLabel));
    }

    @Test
    void bothLanguagesAnswerQueriesWithDifferentTemplatesForTheSameUri() {
        PromptTemplate zhCnTemplate = orchestrator(GoldenInputs.ZH_CN)
                .getNegotiationPrompt(INFORMATION_PROPOSE_URI)
                .orElseThrow();
        PromptTemplate enUsTemplate = orchestrator(GoldenInputs.EN_US)
                .getNegotiationPrompt(INFORMATION_PROPOSE_URI)
                .orElseThrow();

        assertEquals(INFORMATION_PROPOSE_URI, zhCnTemplate.templateUri());
        assertEquals(INFORMATION_PROPOSE_URI, enUsTemplate.templateUri());
        assertNotEquals(zhCnTemplate.content(), enUsTemplate.content());
        assertEquals(
                orchestrator(GoldenInputs.ZH_CN).getNegotiationPrompts().stream()
                        .map(PromptTemplate::templateUri)
                        .toList(),
                orchestrator(GoldenInputs.EN_US).getNegotiationPrompts().stream()
                        .map(PromptTemplate::templateUri)
                        .toList());
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .build();
    }

    private static String readGoldenFixture(GoldenCase goldenCase, String language) {
        String resourcePath = goldenCase.goldenResourcePath(language);
        InputStream stream = NegotiationLanguageSwitchTest.class.getResourceAsStream(resourcePath);
        assertTrue(stream != null, "Golden fixture must exist on the test classpath: " + resourcePath);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new AssertionError("Failed to read golden fixture " + resourcePath, exception);
        }
    }
}
