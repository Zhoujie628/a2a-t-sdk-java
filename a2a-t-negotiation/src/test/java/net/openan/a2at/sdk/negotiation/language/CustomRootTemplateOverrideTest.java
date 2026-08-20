package net.openan.a2at.sdk.negotiation.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs;
import net.openan.a2at.sdk.negotiation.golden.GoldenInputs.GoldenCase;
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
        MetadataContent builtinResult = GoldenCase.INFORMATION_PROPOSE.generate(orchestrator(GoldenInputs.ZH_CN, null));

        MetadataContent result = GoldenCase.INFORMATION_PROPOSE.generate(orchestrator);

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
                        .generate(orchestrator)
                        .promptText()
                        .contains(MARKER_LINE),
                "precondition: the override is in effect");

        Files.delete(customTemplate);

        assertEquals(
                readGoldenFixture(GoldenCase.INFORMATION_PROPOSE, GoldenInputs.ZH_CN),
                GoldenCase.INFORMATION_PROPOSE.generate(orchestrator).promptText(),
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

        MetadataContent result = goldenCase.generate(orchestrator);

        assertEquals(readGoldenFixture(goldenCase, GoldenInputs.ZH_CN), result.promptText());
        assertTrue(!result.promptText().contains(MARKER_LINE), "the marker must stay inside the overridden template");
    }

    @Test
    void templateQueriesStillListEveryTemplateWhileTheOverrideIsPresent() throws IOException {
        writeCustomizedInformationProposeTemplate();

        List<PromptTemplate> templates = orchestratorWithCustomRoot().getNegotiationPrompts();

        assertEquals(6, templates.size());
        PromptTemplate overridden = templates.stream()
                .filter(t -> t.uri().equals(GoldenCase.INFORMATION_PROPOSE.templateUri()))
                .findFirst()
                .orElseThrow();
        assertTrue(overridden.content().contains(MARKER_LINE));
    }

    private Path writeCustomizedInformationProposeTemplate() throws IOException {
        PromptTemplate builtinTemplate = new DefaultNegotiationTemplateLoader(GoldenInputs.ZH_CN, null)
                .load(new NegotiationReference(
                        NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, GoldenInputs.ZH_CN));
        Path customTemplate = customRoot
                .resolve("templates")
                .resolve("Negotiation-T")
                .resolve("v1")
                .resolve("information-negotiation")
                .resolve("propose")
                .resolve("zh-CN")
                .resolve("template.md");
        Files.createDirectories(customTemplate.getParent());
        Files.writeString(
                customTemplate,
                builtinTemplate.content() + "\n\n## " + MARKER_SECTION_TITLE + "\n" + MARKER_LINE + "\n");
        return customTemplate;
    }

    private NegotiationGenerationOrchestrator orchestratorWithCustomRoot() {
        return orchestrator(GoldenInputs.ZH_CN, customRoot.toString());
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language, String localRootDir) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .localRootDir(localRootDir)
                .build();
    }

    private static String readGoldenFixture(GoldenCase goldenCase, String language) {
        String resourcePath = goldenCase.goldenResourcePath(language);
        InputStream stream = CustomRootTemplateOverrideTest.class.getResourceAsStream(resourcePath);
        assertTrue(stream != null, "Golden fixture must exist on the test classpath: " + resourcePath);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new AssertionError("Failed to read golden fixture " + resourcePath, exception);
        }
    }
}
