package net.openan.a2at.sdk.negotiation.content;

/**
 * Content of an abort negotiation message.
 *
 * <p>Abort messages are type-independent: the single common abort template carries the fixed {@code Abort} conclusion
 * section and this content only fills the termination reason.
 *
 * @param terminationReason human-readable reason for terminating the negotiation, such as reaching the round limit,
 *     a timeout or a token budget exhaustion
 * @since 2026-08
 */
public record AbortContent(String terminationReason) implements NegotiationContent {}
