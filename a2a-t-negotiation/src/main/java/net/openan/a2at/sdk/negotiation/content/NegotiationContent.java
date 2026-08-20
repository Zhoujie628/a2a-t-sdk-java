package net.openan.a2at.sdk.negotiation.content;

/**
 * Marker for the typed content of any negotiation message, regardless of phase.
 *
 * <p>The two concrete families are {@link NegotiationProposeContent} for propose-phase messages and
 * {@link NegotiationEndingContent} for terminal messages. Generators and content extractors accept this common
 * supertype and dispatch on the exact runtime type.
 *
 * @since 2026-06
 */
public sealed interface NegotiationContent permits NegotiationProposeContent, NegotiationEndingContent {}
