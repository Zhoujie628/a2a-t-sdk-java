package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.core.validation.StandardTemplates;
import net.openan.a2at.sdk.negotiation.content.InfoProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import org.junit.jupiter.api.Test;

/**
 * Verifies the relationship appended line and the language-specific list punctuation of information propose messages.
 *
 * <p>A non-null relationship is appended as one extra line after the numbered item list, using the language label from
 * the vocabulary; a null relationship adds no line. The numbered item lines join name and value with the list colon of
 * the message language: the full-width colon for zh-CN and a colon plus one space for en-US.
 */
class RelationshipLineRenderingTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri();

    @Test
    void chineseRelationshipIsAppendedWithTheChineseLabel() {
        String promptText = generate("zh-CN", "二选一");

        assertTrue(promptText.contains("缺失项之间的关系：二选一"));
        assertTrue(promptText.contains("1. 节能区域：松山湖"));
        assertFalse(promptText.contains("Relationship between missing items"));
    }

    @Test
    void chineseNullRelationshipAddsNoLine() {
        String promptText = generate("zh-CN", null);

        assertFalse(promptText.contains("缺失项之间的关系"));
        assertTrue(promptText.contains("1. 节能区域：松山湖"));
    }

    @Test
    void englishRelationshipIsAppendedWithTheEnglishLabel() {
        String promptText = generate("en-US", "either-or");

        assertTrue(promptText.contains("Relationship between missing items: either-or"));
        assertTrue(promptText.contains("1. area: Songshan Lake"));
        assertFalse(promptText.contains("缺失项之间的关系"));
    }

    @Test
    void englishNullRelationshipAddsNoLine() {
        String promptText = generate("en-US", null);

        assertFalse(promptText.contains("Relationship between missing items"));
        assertTrue(promptText.contains("1. area: Songshan Lake"));
    }

    @Test
    void chineseListUsesTheFullWidthColonAndEnglishUsesColonAndSpace() {
        String chinese = generate("zh-CN", null);
        String english = generate("en-US", null);

        assertTrue(chinese.contains("："));
        assertFalse(chinese.contains("节能区域: 松山湖"));
        assertTrue(english.contains("1. area: Songshan Lake"));
        assertFalse(english.contains("area：Songshan Lake"));
    }

    private static String generate(String language, String relationship) {
        boolean chinese = "zh-CN".equals(language);
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .build();
        return orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 2, 5),
                                new InfoProposeContent(
                                        List.of(new NegotiationItem(
                                                chinese ? "节能区域" : "area", chinese ? "松山湖" : "Songshan Lake")),
                                        relationship)),
                        INFORMATION_PROPOSE_URI)
                .promptText();
    }
}
