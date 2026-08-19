package net.openan.a2at.sdk.negotiation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class VocabularyTest {

    private static final Set<String> CANONICAL_KEYS = Set.of(
            "section.context",
            "section.info_items",
            "section.info_static",
            "section.info_conclusion",
            "section.info_result_content",
            "section.target",
            "section.target_intent",
            "section.target_alignment",
            "section.target_clarification",
            "section.target_conclusion",
            "section.target_result_content",
            "section.feasibility",
            "section.feasibility_evaluate",
            "section.feasibility_infeasible",
            "section.feasibility_conclusion",
            "section.feasibility_confirm",
            "slot.feasibility",
            "slot.target",
            "slot.feasibility_confirm",
            "label.relationship",
            "punct.list_colon");

    @Test
    void bothLanguagesExposeExactlyTheCanonicalKeys() {
        Vocabulary zhCn = Vocabulary.forLanguage("zh-CN");
        Vocabulary enUs = Vocabulary.forLanguage("en-US");

        assertEquals(CANONICAL_KEYS, zhCn.canonicalKeys());
        assertEquals(CANONICAL_KEYS, enUs.canonicalKeys());
        assertEquals(new TreeSet<>(zhCn.canonicalKeys()), new TreeSet<>(enUs.canonicalKeys()));
        assertEquals(21, zhCn.canonicalKeys().size());
    }

    @Test
    void slotExceptionValuesMatchTemplatePlaceholders() {
        Vocabulary zhCn = Vocabulary.forLanguage("zh-CN");
        Vocabulary enUs = Vocabulary.forLanguage("en-US");

        assertEquals("可行性协商概述", zhCn.get("slot.feasibility"));
        assertEquals("Feasibility Negotiation Summary", enUs.get("slot.feasibility"));
        assertEquals("目标协商概述", zhCn.get("slot.target"));
        assertEquals("Target Negotiation Summary", enUs.get("slot.target"));
        assertEquals("评估结果确认", zhCn.get("slot.feasibility_confirm"));
        assertEquals("Feasibility Result", enUs.get("slot.feasibility_confirm"));
    }

    @Test
    void sectionValuesMatchTemplateSectionTitles() {
        Vocabulary zhCn = Vocabulary.forLanguage("zh-CN");
        Vocabulary enUs = Vocabulary.forLanguage("en-US");

        assertEquals("协商上下文", zhCn.get("section.context"));
        assertEquals("Negotiation Context", enUs.get("section.context"));
        assertEquals("目标协商结果内容", zhCn.get("section.target_result_content"));
        assertEquals("Target Negotiation Result Content", enUs.get("section.target_result_content"));
        assertEquals("可行性评估结果确认", zhCn.get("section.feasibility_confirm"));
        assertEquals("Feasibility Result Confirmation", enUs.get("section.feasibility_confirm"));
    }

    @Test
    void englishRelationshipLabelCarriesOneTrailingSpace() {
        String englishLabel = Vocabulary.forLanguage("en-US").get("label.relationship");
        String chineseLabel = Vocabulary.forLanguage("zh-CN").get("label.relationship");

        assertEquals("Relationship between missing items: ", englishLabel);
        assertTrue(englishLabel.endsWith(" "));
        assertEquals("缺失项之间的关系：", chineseLabel);
    }

    @Test
    void listColonPunctuationDiffersPerLanguage() {
        assertEquals("：", Vocabulary.forLanguage("zh-CN").get("punct.list_colon"));
        assertEquals(": ", Vocabulary.forLanguage("en-US").get("punct.list_colon"));
    }

    @Test
    void unsupportedLanguageThrows() {
        NegotiationContentException exception =
                assertThrows(NegotiationContentException.class, () -> Vocabulary.forLanguage("fr-FR"));

        assertTrue(exception.getMessage().contains("fr-FR"));
        assertTrue(exception.getMessage().contains("A2AT_LANGUAGE"));
    }

    @Test
    void unknownKeyThrows() {
        Vocabulary vocabulary = Vocabulary.forLanguage("zh-CN");

        NegotiationContentException exception =
                assertThrows(NegotiationContentException.class, () -> vocabulary.get("section.unknown"));

        assertTrue(exception.getMessage().contains("section.unknown"));
    }
}
