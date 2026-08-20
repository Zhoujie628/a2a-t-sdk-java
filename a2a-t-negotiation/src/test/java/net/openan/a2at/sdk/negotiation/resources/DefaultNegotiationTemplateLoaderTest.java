package net.openan.a2at.sdk.negotiation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.negotiation.content.NegotiationPhase;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultNegotiationTemplateLoaderTest {

    private static final List<String> EXPECTED_LOAD_ALL_URIS = List.of(
            "Negotiation-T/v1/information-negotiation/propose",
            "Negotiation-T/v1/information-negotiation/accept-reject",
            "Negotiation-T/v1/target-negotiation/propose",
            "Negotiation-T/v1/target-negotiation/accept-reject",
            "Negotiation-T/v1/feasibility-negotiation/propose",
            "Negotiation-T/v1/feasibility-negotiation/accept-reject");

    @TempDir
    Path customRootDir;

    @Test
    void loadAllReturnsAllSixBuiltinTemplatesPerLanguageInFixedOrder() {
        DefaultNegotiationTemplateLoader zhCnLoader = new DefaultNegotiationTemplateLoader("zh-CN", null);
        DefaultNegotiationTemplateLoader enUsLoader = new DefaultNegotiationTemplateLoader("en-US", null);

        List<PromptTemplate> zhCnTemplates = zhCnLoader.loadAll();
        List<PromptTemplate> enUsTemplates = enUsLoader.loadAll();

        assertEquals(EXPECTED_LOAD_ALL_URIS, urisOf(zhCnTemplates));
        assertEquals(EXPECTED_LOAD_ALL_URIS, urisOf(enUsTemplates));
        assertEquals(6, zhCnTemplates.size());
        assertEquals(6, enUsTemplates.size());
        assertEquals(12, zhCnTemplates.size() + enUsTemplates.size());
    }

    @Test
    void loadReturnsFullTemplateContentFromTheClasspath() {
        DefaultNegotiationTemplateLoader loader = new DefaultNegotiationTemplateLoader("en-US", null);

        PromptTemplate template =
                loader.load(new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "en-US"));

        assertEquals("Negotiation-T/v1/information-negotiation/propose", template.uri());
        assertTrue(template.content().startsWith("## Negotiation Context"));
    }

    @Test
    void builtinTemplatesCarryNoDescriptionCommentForEitherLanguage() {
        DefaultNegotiationTemplateLoader zhCnLoader = new DefaultNegotiationTemplateLoader("zh-CN", null);
        DefaultNegotiationTemplateLoader enUsLoader = new DefaultNegotiationTemplateLoader("en-US", null);

        PromptTemplate englishTemplate = enUsLoader.load(
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "en-US"));
        PromptTemplate chineseTemplate = zhCnLoader.load(
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "zh-CN"));

        assertEquals("", englishTemplate.description());
        assertEquals("", chineseTemplate.description());
    }

    @Test
    void customRootTemplateWinsWhilePresentAndBuiltinIsUsedAfterRemoval() throws IOException {
        Path customTemplatePath = customRootDir
                .resolve("templates")
                .resolve("Negotiation-T")
                .resolve("v1")
                .resolve("information-negotiation")
                .resolve("propose")
                .resolve("zh-CN")
                .resolve("template.md");
        writeTemplate(customTemplatePath, "<!-- custom template -->\n\n## 协商上下文\n自定义标记内容\n");
        DefaultNegotiationTemplateLoader loader =
                new DefaultNegotiationTemplateLoader("zh-CN", customRootDir.toString());

        PromptTemplate overridden =
                loader.load(new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "zh-CN"));
        assertTrue(overridden.content().contains("自定义标记内容"));
        assertEquals("custom template", overridden.description());

        PromptTemplate untouched =
                loader.load(new NegotiationReference(NegotiationType.TARGET, NegotiationPhase.PROPOSE, "zh-CN"));
        assertTrue(untouched.content().contains("目标协商概述"));
        assertTrue(!untouched.content().contains("自定义标记内容"));

        Files.delete(customTemplatePath);

        PromptTemplate fallback =
                loader.load(new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "zh-CN"));
        assertTrue(fallback.content().contains("## 协商上下文"));
        assertTrue(!fallback.content().contains("自定义标记内容"));
        assertEquals("", fallback.description());
    }

    @Test
    void loadAllWithCustomRootStillListsEveryTemplate() throws IOException {
        Path customTemplatePath = customRootDir
                .resolve("templates")
                .resolve("Negotiation-T")
                .resolve("v1")
                .resolve("target-negotiation")
                .resolve("propose")
                .resolve("zh-CN")
                .resolve("template.md");
        writeTemplate(customTemplatePath, "<!-- custom target -->\n\n## 目标协商\n自定义目标内容\n");
        DefaultNegotiationTemplateLoader loader =
                new DefaultNegotiationTemplateLoader("zh-CN", customRootDir.toString());

        List<PromptTemplate> templates = loader.loadAll();

        assertEquals(EXPECTED_LOAD_ALL_URIS, urisOf(templates));
        PromptTemplate overriddenTemplate = templates.get(2);
        assertEquals("Negotiation-T/v1/target-negotiation/propose", overriddenTemplate.uri());
        assertTrue(overriddenTemplate.content().contains("自定义目标内容"));
    }

    @Test
    void missingLanguageThrowsOnLoadAndIsSkippedByLoadAll() {
        DefaultNegotiationTemplateLoader loader = new DefaultNegotiationTemplateLoader("fr-FR", null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> loader.load(
                        new NegotiationReference(NegotiationType.INFORMATION, NegotiationPhase.PROPOSE, "fr-FR")));

        assertTrue(exception.getMessage().contains("A2AT_LANGUAGE"));
        assertEquals(List.of(), loader.loadAll());
    }

    private static List<String> urisOf(List<PromptTemplate> templates) {
        return templates.stream().map(PromptTemplate::uri).toList();
    }

    private static void writeTemplate(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
