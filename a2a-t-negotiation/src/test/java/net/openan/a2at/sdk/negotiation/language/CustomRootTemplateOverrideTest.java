package net.openan.a2at.sdk.negotiation.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGoldenCases;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGoldenCases.GoldenCase;
import net.openan.a2at.sdk.negotiation.resources.DefaultNegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Locks the dual-root fallback of the negotiation generation pipeline at the orchestrator level.
 *
 * <p>A template placed under a local resource root at the template URI's path wins over the built-in template; once the
 * local file is removed the same orchestrator falls back to the built-in template and reproduces the golden fixture
 * byte for byte; the override never leaks into the other templates of the language.
 */
class CustomRootTemplateOverrideTest {

    private static final String MARKER_SECTION_TITLE = "Custom Marker Section";

    private static final String MARKER_LINE = "CUSTOM_TEMPLATE_MARKER_7d31";

    @TempDir
    Path customRoot;

    @Test
    void customRootTemplateWinsWhilePresentAndTheRestOfTheOutputIsUntouched() throws IOException {
        writeCustomizedInformationProposeTemplate();
        NegotiationGenerationOrchestrator orchestrator = orchestratorWithCustomRoot();
        MetadataContent builtinResult =
                GoldenCase.INFORMATION_PROPOSE.generate(orchestrator(NegotiationGoldenCases.ZH_CN, null), NegotiationGoldenCases.ZH_CN);

        MetadataContent result = GoldenCase.INFORMATION_PROPOSE.generate(orchestrator, NegotiationGoldenCases.ZH_CN);

        assertEquals(
                builtinResult.promptText() + "\n\n## " + MARKER_SECTION_TITLE + "\n" + MARKER_LINE,
                result.promptText(),
                "the customized template must render the marker section appended to the otherwise unchanged message");
        assertEquals(GoldenCase.INFORMATION_PROPOSE.templateUri(), result.templateUri());
    }

    @Test
    void builtInGoldenOutputIsRestoredAfterTheOverrideIsRemoved() throws IOException {
        Path customTemplate = writeCustomizedInformationProposeTemplate();
        NegotiationGenerationOrchestrator orchestrator = orchestratorWithCustomRoot();
        assertTrue(
                GoldenCase.INFORMATION_PROPOSE
                        .generate(orchestrator, NegotiationGoldenCases.ZH_CN)
                        .promptText()
                        .contains(MARKER_LINE),
                "precondition: the override is in effect");

        Files.delete(customTemplate);

        assertEquals(
                GoldenCase.INFORMATION_PROPOSE.goldenText(NegotiationGoldenCases.ZH_CN),
                GoldenCase.INFORMATION_PROPOSE.generate(orchestrator, NegotiationGoldenCases.ZH_CN).promptText(),
                "after the removal the built-in template must reproduce the golden fixture byte for byte");
    }

    @ParameterizedTest(name = "the override leaves the other templates untouched [{0}]")
    @EnumSource(
            value = GoldenCase.class,
            names = {"INFORMATION_PROPOSE"},
            mode = EnumSource.Mode.EXCLUDE)
    void theOverrideNeverLeaksIntoTheOtherTemplates(GoldenCase goldenCase) throws IOException {
        writeCustomizedInformationProposeTemplate();
        NegotiationGenerationOrchestrator orchestrator = orchestratorWithCustomRoot();

        MetadataContent result = goldenCase.generate(orchestrator, NegotiationGoldenCases.ZH_CN);

        assertEquals(goldenCase.goldenText(NegotiationGoldenCases.ZH_CN), result.promptText());
        assertTrue(!result.promptText().contains(MARKER_LINE), "the marker must stay inside the overridden template");
    }

    @Test
    void templateQueriesStillListEveryTemplateWhileTheOverrideIsPresent() throws IOException {
        writeCustomizedInformationProposeTemplate();

        List<PromptTemplate> templates = orchestratorWithCustomRoot().getNegotiationPrompts();

        assertEquals(7, templates.size());
        PromptTemplate overridden = templates.stream()
                .filter(t -> t.templateUri().uri().equals(GoldenCase.INFORMATION_PROPOSE.templateUri()))
                .findFirst()
                .orElseThrow();
        assertTrue(overridden.content().contains(MARKER_LINE));
    }

    private Path writeCustomizedInformationProposeTemplate() throws IOException {
        PromptTemplate builtinTemplate = new DefaultNegotiationTemplateLoader(NegotiationGoldenCases.ZH_CN, null)
                .load(new NegotiationReference(
                        NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, NegotiationGoldenCases.ZH_CN));
        Path customTemplate = customRoot
                .resolve("templates")
                .resolve("Negotiation-T")
                .resolve("information-negotiation")
                .resolve("propose")
                .resolve("v1")
                .resolve("zh-CN")
                .resolve("template.md");
        Files.createDirectories(customTemplate.getParent());
        Files.writeString(
                customTemplate,
                builtinTemplate.content() + "\n\n## " + MARKER_SECTION_TITLE + "\n" + MARKER_LINE + "\n");
        return customTemplate;
    }

    private NegotiationGenerationOrchestrator orchestratorWithCustomRoot() {
        return orchestrator(NegotiationGoldenCases.ZH_CN, customRoot.toString());
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language, String localRootDir) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .localRootDir(localRootDir)
                .build();
    }

}
