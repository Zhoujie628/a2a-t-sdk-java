package net.openan.a2at.sdk.core.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One loadable prompt template of any A2A-T extension.
 *
 * @param templateUri typed template URI such as {@code Task-T/network-layer/ran-energy-saving/v1} or
 *     {@code Negotiation-T/information-negotiation/propose/v1}
 * @param description template description taken from the leading HTML comment; empty string when the template has no
 *     comment
 * @param content full template file text; null when the template content is unavailable
 * @since 2026-08
 */
public record PromptTemplate(
        @NonNull TemplateUri templateUri, @NonNull String description, @Nullable String content) {}
