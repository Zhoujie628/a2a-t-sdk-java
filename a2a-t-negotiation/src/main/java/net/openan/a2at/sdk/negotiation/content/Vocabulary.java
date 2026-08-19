package net.openan.a2at.sdk.negotiation.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Language-specific text constants for negotiation templates.
 *
 * <p>A vocabulary is the single source of the section titles, slot names, appended line labels and list punctuation
 * used when rendering negotiation messages and when recognising sections inside received messages. The canonical keys
 * are language-neutral; every supported language exposes exactly the same key set, while the values match the bundled
 * template bytes of that language verbatim.
 *
 * @since 2026-06
 */
public final class Vocabulary {

    private static final String ZH_CN = "zh-CN";

    private static final String EN_US = "en-US";

    private static final Vocabulary ZH_CN_VOCABULARY = new Vocabulary(ZH_CN, buildZhCnEntries());

    private static final Vocabulary EN_US_VOCABULARY = new Vocabulary(EN_US, buildEnUsEntries());

    private final String language;

    private final Map<String, String> entries;

    private Vocabulary(String language, Map<String, String> entries) {
        this.language = language;
        this.entries = entries;
    }

    /**
     * Returns the vocabulary for one language.
     *
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @return vocabulary holding the text constants of that language
     * @throws NegotiationContentException if the language has no bundled vocabulary
     */
    public static Vocabulary forLanguage(String language) {
        if (ZH_CN.equals(language)) {
            return ZH_CN_VOCABULARY;
        }
        if (EN_US.equals(language)) {
            return EN_US_VOCABULARY;
        }
        throw new NegotiationContentException(
                "Unsupported negotiation vocabulary language " + language + "; supported languages are zh-CN and"
                        + " en-US, configure A2AT_LANGUAGE accordingly.",
                "language");
    }

    /**
     * Returns the text constant registered under one canonical key.
     *
     * @param canonicalKey canonical vocabulary key such as {@code section.context} or {@code punct.list_colon}
     * @return language-specific text constant
     * @throws NegotiationContentException if the key is not part of the vocabulary
     */
    public String get(String canonicalKey) {
        String value = entries.get(canonicalKey);
        if (value == null) {
            throw new NegotiationContentException(
                    "Unknown negotiation vocabulary key " + canonicalKey + " for language " + language + ".",
                    "canonicalKey");
        }
        return value;
    }

    /**
     * Returns all canonical keys exposed by this vocabulary.
     *
     * @return immutable set of canonical keys, identical for every supported language
     */
    public Set<String> canonicalKeys() {
        return entries.keySet();
    }

    /**
     * Returns the language this vocabulary is bound to.
     *
     * @return locale identifier such as {@code zh-CN}
     */
    public String language() {
        return language;
    }

    private static Map<String, String> buildZhCnEntries() {
        return buildEntries(List.of(
                entry("section.context", "协商上下文"),
                entry("section.info_items", "所需信息项"),
                entry("section.info_static", "信息协商"),
                entry("section.info_conclusion", "信息协商结果"),
                entry("section.info_result_content", "信息协商结果内容"),
                entry("section.target", "目标协商"),
                entry("section.target_intent", "意图理解陈述"),
                entry("section.target_alignment", "理解对齐与疑问澄清"),
                entry("section.target_clarification", "待澄清内容"),
                entry("section.target_conclusion", "目标协商结果"),
                entry("section.target_result_content", "目标协商结果内容"),
                entry("section.feasibility", "可行性协商"),
                entry("section.feasibility_evaluate", "待评估内容说明"),
                entry("section.feasibility_infeasible", "评估不可行时的详情和提案"),
                entry("section.feasibility_conclusion", "可行性协商结果"),
                entry("section.feasibility_confirm", "可行性评估结果确认"),
                entry("slot.feasibility", "可行性协商概述"),
                entry("slot.target", "目标协商概述"),
                entry("slot.feasibility_confirm", "评估结果确认"),
                entry("label.relationship", "缺失项之间的关系："),
                entry("punct.list_colon", "：")));
    }

    private static Map<String, String> buildEnUsEntries() {
        return buildEntries(List.of(
                entry("section.context", "Negotiation Context"),
                entry("section.info_items", "Required Information Items"),
                entry("section.info_static", "Information Negotiation"),
                entry("section.info_conclusion", "Information Negotiation Result"),
                entry("section.info_result_content", "Information Negotiation Result Content"),
                entry("section.target", "Target Negotiation"),
                entry("section.target_intent", "Intent Understanding"),
                entry("section.target_alignment", "Alignment and Clarification"),
                entry("section.target_clarification", "Clarification Required"),
                entry("section.target_conclusion", "Target Negotiation Result"),
                entry("section.target_result_content", "Target Negotiation Result Content"),
                entry("section.feasibility", "Feasibility Negotiation"),
                entry("section.feasibility_evaluate", "Contents to Evaluate"),
                entry("section.feasibility_infeasible", "Infeasibility Details and Proposal"),
                entry("section.feasibility_conclusion", "Feasibility Negotiation Result"),
                entry("section.feasibility_confirm", "Feasibility Result Confirmation"),
                entry("slot.feasibility", "Feasibility Negotiation Summary"),
                entry("slot.target", "Target Negotiation Summary"),
                entry("slot.feasibility_confirm", "Feasibility Result"),
                entry("label.relationship", "Relationship between missing items: "),
                entry("punct.list_colon", ": ")));
    }

    private static Map<String, String> buildEntries(List<String[]> flatEntries) {
        Map<String, String> built = new LinkedHashMap<>();
        for (String[] flatEntry : flatEntries) {
            built.put(flatEntry[0], flatEntry[1]);
        }
        return Collections.unmodifiableMap(built);
    }

    private static String[] entry(String key, String value) {
        return new String[] {key, value};
    }
}
