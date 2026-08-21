package net.openan.a2at.sdk.core.model;

/**
 * One loadable prompt template of any A2A-T extension.
 *
 * @param uri template URI such as {@code Task-T/network-layer/energy-saving/v1} or
 *     {@code Negotiation-T/information-negotiation/propose/v1}
 * @param description template description taken from the leading HTML comment; empty string when the template has no
 *     comment
 * @param content full template file text
 * @since 2026-08
 */
public record PromptTemplate(String uri, String description, String content) {}
