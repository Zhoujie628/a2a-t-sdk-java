package net.openan.a2at.sdk.negotiation.resources;

/**
 * One loadable negotiation template.
 *
 * @param uri template URI such as {@code Negotiation-T/v1/information-negotiation/propose}
 * @param description template description taken from the leading HTML comment; empty string when the template has no
 *     comment
 * @param content full template file text
 * @since 2026-06
 */
public record PromptTemplate(String uri, String description, String content) {}
