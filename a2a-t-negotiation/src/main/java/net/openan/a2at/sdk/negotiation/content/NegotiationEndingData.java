package net.openan.a2at.sdk.negotiation.content;

/**
 * Input bundle for generating a terminal (accept or reject) negotiation message from typed data.
 *
 * <p>Both accept and reject generation methods take this same bundle. The conclusion constraint is carried by the
 * method, not the type: {@code generateAcceptFromData} requires {@code content.conclusion() == ACCEPT} and
 * {@code generateRejectFromData} requires {@code REJECT}; generation methods enforce it and reject a mismatched
 * conclusion (including {@code ABORT}) as a content error.
 *
 * @param context negotiation session context
 * @param content typed terminal content matching the negotiation type addressed by the template URI
 * @since 2026-08
 */
public record NegotiationEndingData(NegotiationContext context, NegotiationEndingContent content) {}
