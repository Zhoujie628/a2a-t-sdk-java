package net.openan.a2at.sdk.core.validation;

/**
 * Plain data carrier implementing {@link TemplateReference} for templates that have no richer reference type of
 * their own (Task-T, Notification-T, Authorization-T and language-bound {@link TemplateUri} values).
 *
 * @param uri template URI such as {@code Task-T/v1/energy-saving}
 * @param language locale identifier such as {@code zh-CN} or {@code en-US}
 * @param extensionName extension name identifying the template family, such as {@code Task-T}
 * @since 2026-08
 */
public record SimpleTemplateReference(String uri, String language, String extensionName)
        implements TemplateReference {}
