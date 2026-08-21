package net.openan.a2at.sdk.prompt.taskrendering;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.openan.a2at.sdk.prompt.taskrendering.exception.TaskPromptRenderException;

/**
 * Renders plain prompt bodies from lightweight template placeholders.
 *
 * <p>This renderer carries the <b>collapse</b> blank-slot policy of {@link SectionedTemplateRenderer}: a slot section
 * keeps its scaffolding and its standalone slot line collapses to the bare slot placeholder.
 *
 * @since 2026-06
 */
public final class TaskPromptRenderer implements SectionedTemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{?\\s*([^{}]+?)\\s*\\}\\}?");
    private static final Pattern SECTION_HEADER_PATTERN = Pattern.compile("^##\\s+.+$");
    private static final Pattern STANDALONE_SLOT_LINE_PATTERN = Pattern.compile(
            "^\\s*(\\{\\{?\\s*[^{}]+?\\s*\\}\\}?)(?:\\s*(?:\\(Required\\)|\\(Optional\\)|（必选）|（可选）))?\\s*$");

    /**
     * Renders a template by replacing slot placeholders with normalized slot values.
     *
     * @param templateText template text containing lightweight placeholders
     * @param slots normalized slot values keyed by slot name
     * @return rendered prompt text
     */
    @Override
    public String render(String templateText, Map<String, String> slots) {
        Objects.requireNonNull(templateText, "Template text must not be null.");
        Map<String, String> safeSlots = slots == null ? Map.of() : slots;
        if (!balancedBraces(templateText)) {
            throw new TaskPromptRenderException("Template text has unbalanced braces.");
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(collapseSlotDrivenSections(templateText));
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String slotName = matcher.group(1).trim();
            if (!safeSlots.containsKey(slotName)) {
                throw new TaskPromptRenderException("Unknown slot referenced by template: " + slotName);
            }
            String replacement = safeSlots.get(slotName);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(Optional.ofNullable(replacement).orElse("")));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static String collapseSlotDrivenSections(String templateText) {
        String[] lines = normalizeLineEndings(templateText).split("\n", -1);
        StringBuilder collapsed = new StringBuilder();
        int index = 0;
        while (index < lines.length) {
            if (!isSectionHeader(lines[index])) {
                appendLine(collapsed, lines[index], index < lines.length - 1);
                index++;
                continue;
            }

            int nextSectionIndex = index + 1;
            while (nextSectionIndex < lines.length && !isSectionHeader(lines[nextSectionIndex])) {
                nextSectionIndex++;
            }

            appendCollapsedSection(collapsed, lines, index, nextSectionIndex, nextSectionIndex < lines.length);
            index = nextSectionIndex;
        }
        return collapsed.toString();
    }

    private static String normalizeLineEndings(String templateText) {
        return templateText.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void appendCollapsedSection(
            StringBuilder collapsed,
            String[] lines,
            int sectionStart,
            int nextSectionStart,
            boolean appendTrailingNewline) {
        appendLine(collapsed, lines[sectionStart], true);

        Optional<Matcher> standaloneSlotMatcher = firstStandaloneSlotMatcher(lines, sectionStart + 1, nextSectionStart);
        if (!standaloneSlotMatcher.isPresent()) {
            for (int index = sectionStart + 1; index < nextSectionStart; index++) {
                appendLine(collapsed, lines[index], index < lines.length - 1);
            }
            return;
        }

        int firstEffectiveLineIndex = firstEffectiveLineIndex(lines, sectionStart + 1, nextSectionStart);
        for (int index = sectionStart + 1; index < firstEffectiveLineIndex; index++) {
            appendLine(collapsed, lines[index], true);
        }
        appendLine(collapsed, standaloneSlotMatcher.get().group(1), true);
        if (appendTrailingNewline) {
            collapsed.append('\n');
        }
    }

    private static Optional<Matcher> firstStandaloneSlotMatcher(String[] lines, int startInclusive, int endExclusive) {
        int firstEffectiveLineIndex = firstEffectiveLineIndex(lines, startInclusive, endExclusive);
        if (firstEffectiveLineIndex < 0) {
            return Optional.empty();
        }

        Matcher matcher = STANDALONE_SLOT_LINE_PATTERN.matcher(lines[firstEffectiveLineIndex]);
        return matcher.matches() ? Optional.of(matcher) : Optional.empty();
    }

    private static int firstEffectiveLineIndex(String[] lines, int startInclusive, int endExclusive) {
        for (int index = startInclusive; index < endExclusive; index++) {
            if (!lines[index].trim().isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isSectionHeader(String line) {
        return SECTION_HEADER_PATTERN.matcher(line).matches();
    }

    private static void appendLine(StringBuilder builder, String line, boolean appendTrailingNewline) {
        builder.append(line);
        if (appendTrailingNewline) {
            builder.append('\n');
        }
    }

    private static boolean balancedBraces(String text) {
        long opening = text.chars().filter(ch -> ch == '{').count();
        long closing = text.chars().filter(ch -> ch == '}').count();
        return opening == closing;
    }
}
