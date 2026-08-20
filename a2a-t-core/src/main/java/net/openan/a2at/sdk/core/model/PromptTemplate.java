package net.openan.a2at.sdk.core.model;

/**
 * One loadable prompt template of any A2A-T extension.
 *
 * @param uri template URI such as {@code Task-T/v1/energy-saving} or {@code Negotiation-T/v1/information-negotiation/propose}
 * @param description template description taken from the leading HTML comment; empty string when the template has no
 *     comment
 * @param content full template file text
 * @since 2026-08
 */
public record PromptTemplate(String uri, String description, String content) {}
