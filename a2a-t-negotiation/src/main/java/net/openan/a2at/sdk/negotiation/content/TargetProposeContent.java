package net.openan.a2at.sdk.negotiation.content;

import java.util.List;

/**
 * Content of a target negotiation propose message.
 *
 * @param targetNegotiationDescription required summary of what the target negotiation is about
 * @param intentUnderstanding restatement of the counterpart's intent; null or empty omits the section (first round
 *     only)
 * @param alignmentAndClarification alignment statements and clarifications; null or empty omits the section (later
 *     rounds only)
 * @param requestForClarification open clarification requests; null or empty omits the section
 * @since 2026-08
 */
public record TargetProposeContent(
        String targetNegotiationDescription,
        List<NegotiationItem> intentUnderstanding,
        List<NegotiationItem> alignmentAndClarification,
        List<NegotiationItem> requestForClarification)
        implements NegotiationProposeContent {}
