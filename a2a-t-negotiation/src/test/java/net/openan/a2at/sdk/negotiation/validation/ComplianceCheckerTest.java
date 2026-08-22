package net.openan.a2at.sdk.negotiation.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ComplianceCheckerTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private final DefaultNegotiationComplianceChecker checker = new DefaultNegotiationComplianceChecker();

    private final Vocabulary zhVocabulary = Vocabulary.forLanguage("zh-CN");

    private final Vocabulary enVocabulary = Vocabulary.forLanguage("en-US");

    static List<Object[]> violationCases() {
        return List.of(
                new Object[] {"non-uuid id", "not-a-uuid", null, null, List.of("id")},
                new Object[] {"blank id", "", null, null, List.of("id")},
                new Object[] {"round zero", null, "0", null, List.of("round")},
                new Object[] {"negative round", null, "-1", null, List.of("round")},
                new Object[] {"zero maxRounds", null, null, "0", List.of("maxRounds")},
                new Object[] {"round above maxRounds", null, "3", "2", List.of("round")},
                new Object[] {"non-integer round", null, "two", null, List.of("round")},
                new Object[] {"non-integer maxRounds", null, null, "many", List.of("maxRounds")},
                new Object[] {"missing id line", "__MISSING__", null, null, List.of("id")},
                new Object[] {"missing round line", null, "__MISSING__", null, List.of("round")},
                new Object[] {"missing maxRounds line", null, null, "__MISSING__", List.of("maxRounds")});
    }

    @Test
    void validZhMessagePassesAndContextIsExtracted() {
        String prompt = "前置说明，应被丢弃。\n\n"
                + "## 协商上下文\n"
                + "- id: " + SESSION_ID + "\n"
                + "- round: 2\n"
                + "- maxRounds: 5\n\n"
                + "## 所需信息项\n"
                + "1. 节能区域信息：请提供真实存在的区域\n";

        NegotiationRuleCheckResult result = checker.check(prompt, zhVocabulary);

        assertTrue(result.passed());
        assertTrue(result.isNegotiation());
        assertEquals(List.of(), result.errors());
        assertEquals(new NegotiationContext(SESSION_ID, 2, 5), result.context());
    }

    @Test
    void validEnMessagePassesAndContextIsExtracted() {
        String prompt = "Preamble that must be dropped.\n\n"
                + "## Negotiation Context\n"
                + "- id: " + SESSION_ID + "\n"
                + "- round: 1\n"
                + "- maxRounds: 5\n\n"
                + "## Required Information Items\n"
                + "1. energy saving region: provide a real region\n";

        NegotiationRuleCheckResult result = checker.check(prompt, enVocabulary);

        assertTrue(result.passed());
        assertTrue(result.isNegotiation());
        assertEquals(List.of(), result.errors());
        assertEquals(new NegotiationContext(SESSION_ID, 1, 5), result.context());
    }

    @ParameterizedTest
    @MethodSource("violationCases")
    void contextViolationsFailWithTheExpectedSlotNames(
            String caseName, String idValue, String roundValue, String maxRoundsValue, List<String> expectedSlots) {
        String prompt = zhMessage(idValue, roundValue, maxRoundsValue);

        NegotiationRuleCheckResult result = checker.check(prompt, zhVocabulary);

        assertFalse(result.passed(), caseName);
        assertTrue(result.isNegotiation(), caseName);
        assertNull(result.context(), caseName);
        assertEquals(expectedSlots.size(), result.errors().size(), caseName);
        for (int index = 0; index < expectedSlots.size(); index++) {
            assertEquals(expectedSlots.get(index), result.errors().get(index).slotName(), caseName);
        }
    }

    @Test
    void uuidFormatIsEnforcedBeyondLength() {
        String prompt = zhMessage(SESSION_ID.replace('b', 'z'), null, null);

        NegotiationRuleCheckResult result = checker.check(prompt, zhVocabulary);

        assertFalse(result.passed());
        assertEquals("id", result.errors().get(0).slotName());
        assertEquals("invalid_uuid", result.errors().get(0).code());
    }

    @Test
    void uppercaseHexUuidIsAccepted() {
        String prompt = zhMessage(SESSION_ID.toUpperCase(java.util.Locale.ROOT), null, null);

        NegotiationRuleCheckResult result = checker.check(prompt, zhVocabulary);

        assertTrue(result.passed());
        assertEquals(0, result.errors().size());
    }

    @Test
    void textWithoutContextSectionIsNotANegotiationMessage() {
        String prompt = "## 任务目标\n" + "诊断网络故障\n\n" + "## 任务类型\n" + "故障诊断\n";

        NegotiationRuleCheckResult result = checker.check(prompt, zhVocabulary);

        assertFalse(result.isNegotiation());
        assertFalse(result.passed());
        assertEquals(List.of(), result.errors());
        assertNull(result.context());
    }

    @Test
    void emptyTextIsNotANegotiationMessage() {
        NegotiationRuleCheckResult result = checker.check("", zhVocabulary);

        assertFalse(result.isNegotiation());
        assertEquals(List.of(), result.errors());
        assertNull(result.context());
    }

    @Test
    void nullTextIsNotANegotiationMessage() {
        NegotiationRuleCheckResult result = checker.check(null, zhVocabulary);

        assertFalse(result.isNegotiation());
        assertEquals(List.of(), result.errors());
    }

    @Test
    void preambleOnlyTextIsNotANegotiationMessage() {
        NegotiationRuleCheckResult result = checker.check("只是普通文本，没有任何板块标题。", zhVocabulary);

        assertFalse(result.isNegotiation());
        assertEquals(List.of(), result.errors());
    }

    @Test
    void englishCheckerDoesNotRecogniseZhContextSection() {
        String prompt = zhMessage(null, null, null);

        NegotiationRuleCheckResult result = checker.check(prompt, enVocabulary);

        assertFalse(result.isNegotiation());
        assertEquals(List.of(), result.errors());
    }

    @Test
    void checkerDoesNotValidateConclusionValues() {
        String prompt = "## 协商上下文\n"
                + "- id: " + SESSION_ID + "\n"
                + "- round: 1\n"
                + "- maxRounds: 5\n\n"
                + "## 信息协商结果\n"
                + "Abort\n";

        NegotiationRuleCheckResult result = checker.check(prompt, zhVocabulary);

        assertTrue(result.passed());
        assertEquals(List.of(), result.errors());
    }

    @Test
    void checkerDoesNotRequireEndingResultContentSection() {
        String prompt = "## 协商上下文\n"
                + "- id: " + SESSION_ID + "\n"
                + "- round: 1\n"
                + "- maxRounds: 5\n\n"
                + "## 信息协商结果\n"
                + "Accept\n";

        NegotiationRuleCheckResult result = checker.check(prompt, zhVocabulary);

        assertTrue(result.passed());
        assertEquals(List.of(), result.errors());
    }

    @Test
    void checkerDoesNotEnforceFeasibilitySectionExclusivity() {
        String prompt = "## 协商上下文\n"
                + "- id: " + SESSION_ID + "\n"
                + "- round: 1\n"
                + "- maxRounds: 5\n\n"
                + "## 待评估内容说明\n"
                + "1. 待评估内容\n\n"
                + "## 评估不可行时的详情和提案\n"
                + "1. 不可行详情\n";

        NegotiationRuleCheckResult result = checker.check(prompt, zhVocabulary);

        assertTrue(result.passed());
        assertEquals(List.of(), result.errors());
    }

    @Test
    void resultRecordHasExactlyTheFourPinnedComponents() {
        String[] componentNames = java.util.Arrays.stream(NegotiationRuleCheckResult.class.getRecordComponents())
                .map(component -> component.getName())
                .toArray(String[]::new);

        assertEquals(List.of("passed", "isNegotiation", "errors", "context"), List.of(componentNames));
    }

    @Test
    void ruleErrorsCarryStructuredDetails() {
        String prompt = zhMessage("short", null, null);

        NegotiationRuleCheckResult result = checker.check(prompt, zhVocabulary);

        SlotValidationError error = result.errors().get(0);
        assertEquals("id", error.slotName());
        assertEquals("invalid_uuid", error.code());
        assertTrue(error.message().contains("UUID"));
    }

    private static String zhMessage(String idValue, String roundValue, String maxRoundsValue) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 协商上下文\n");
        if (!"__MISSING__".equals(idValue)) {
            prompt.append("- id:")
                    .append(idValue == null ? " " + SESSION_ID : " " + idValue)
                    .append('\n');
        }
        if (!"__MISSING__".equals(roundValue)) {
            prompt.append("- round:")
                    .append(roundValue == null ? " 1" : " " + roundValue)
                    .append('\n');
        }
        if (!"__MISSING__".equals(maxRoundsValue)) {
            prompt.append("- maxRounds:")
                    .append(maxRoundsValue == null ? " 5" : " " + maxRoundsValue)
                    .append('\n');
        }
        prompt.append("\n## 所需信息项\n1. 信息项\n");
        return prompt.toString();
    }
}
