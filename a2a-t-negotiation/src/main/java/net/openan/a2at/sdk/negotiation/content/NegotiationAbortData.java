package net.openan.a2at.sdk.negotiation.content;

/**
 * Input bundle for generating an abort negotiation message from typed data.
 *
 * <p>The abort message is type-independent and terminates a negotiation outside the accept/reject outcome model; the
 * {@code Abort} conclusion is fixed template text, so the content carries only the termination reason.
 *
 * @param context negotiation session context
 * @param content typed abort content carrying the termination reason
 * @since 2026-08
 */
public record NegotiationAbortData(NegotiationContext context, NegotiationAbortContent content) {}
