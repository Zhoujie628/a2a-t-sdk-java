package net.openan.a2at.sample.private_line_complaint.negotiation.shared;

/** Terminal result selected by the responder for the sample negotiation. */
public enum NegotiationDecision {
    ACCEPT,
    REJECT;

    public static NegotiationDecision fromEnvironment(String rawValue) {
        return rawValue != null && "reject".equalsIgnoreCase(rawValue.trim()) ? REJECT : ACCEPT;
    }
}
