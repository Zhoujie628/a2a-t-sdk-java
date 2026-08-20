package net.openan.a2at.sdk.negotiation.resources;

/**
 * Extracts the description convention shared by every prompt template of the resource tree.
 *
 * <p>A template carries its description as a leading HTML comment on the first line; a template without such a
 * comment reports an empty description. The convention is shared between the negotiation template loader and the
 * template catalog so that both produce equal {@link PromptTemplate} records for the same file.
 *
 * @since 2026-08
 */
final class TemplateDescriptions {

    private static final String COMMENT_OPEN = "<!--";

    private static final String COMMENT_CLOSE = "-->";

    private TemplateDescriptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Extracts the template description from a leading HTML comment.
     *
     * @param content full template text
     * @return stripped comment text of the first line when it is an HTML comment, otherwise an empty string
     */
    static String extract(String content) {
        int firstLineBreak = content.indexOf('\n');
        String firstLine = firstLineBreak < 0 ? content : content.substring(0, firstLineBreak);
        firstLine = firstLine.strip();
        if (firstLine.startsWith(COMMENT_OPEN)
                && firstLine.endsWith(COMMENT_CLOSE)
                && firstLine.length() >= COMMENT_OPEN.length() + COMMENT_CLOSE.length()) {
            return firstLine
                    .substring(COMMENT_OPEN.length(), firstLine.length() - COMMENT_CLOSE.length())
                    .strip();
        }
        return "";
    }
}
