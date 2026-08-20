package net.openan.a2at.sdk.prompt.taskrendering.api;

import java.util.Map;

/**
 * Renders a sectioned prompt template by filling slot values under one blank-slot policy.
 *
 * <p>A sectioned template is split into sections on {@code ## } title lines; any content before the first title is
 * preamble and is discarded. A section whose first non-empty body line is a standalone slot placeholder line is a
 * slot section driven by that slot; every other section is static and passes through with placeholder substitution.
 *
 * <p>Implementations differ in how they treat a slot section whose value is null or blank:
 *
 * <ul>
 *   <li><b>collapse policy</b> — {@link TaskPromptRenderer}: the section is kept and its standalone slot line collapses
 *       to the bare slot placeholder, preserving the section scaffolding of the template;
 *   <li><b>drop policy</b> — {@link DropBlankSlotSectionRenderer}: the whole section including its title is removed
 *       from the rendered output.
 * </ul>
 *
 * <p>The two policies are deliberately not merged: both are load-bearing for their extension families, so the grammar
 * of section splitting lives here while each policy keeps its own slot-line and substitution rules.
 *
 * @since 2026-08
 */
public interface SectionedTemplateRenderer {

    /**
     * Renders one sectioned template text with the given slot values.
     *
     * @param templateText full template text whose sections are filled according to the implementation's policy
     * @param slots slot values keyed by slot name; how null or blank values are handled depends on the policy
     * @return rendered prompt text
     */
    String render(String templateText, Map<String, String> slots);
}
