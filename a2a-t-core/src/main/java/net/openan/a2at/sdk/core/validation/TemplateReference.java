package net.openan.a2at.sdk.core.validation;

/**
 * Identifies a content template against which validation is performed.
 *
 * @since 2026-08
 */
public interface TemplateReference {

    /**
     * Returns the URI of the template.
     *
     * @return template URI
     */
    String uri();

    /**
     * Returns the language of the template.
     *
     * @return template language
     */
    String language();

    /**
     * Returns the extension name identifying the template family — the first segment of the template URI.
     *
     * @return extension name such as {@code Task-T}
     */
    String extensionName();
}