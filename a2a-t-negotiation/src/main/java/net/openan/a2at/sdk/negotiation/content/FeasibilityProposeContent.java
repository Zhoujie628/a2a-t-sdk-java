package net.openan.a2at.sdk.negotiation.content;

import java.util.List;

/**
 * Content of a feasibility negotiation propose message.
 *
 * @param feasibilityNegotiationDescription required summary describing the nature of the message
 * @param action action this propose message performs; selects which conditional sections are rendered
 * @param contentsToEvaluate contents the counterpart should evaluate; null or empty omits the section (only for
 *     {@link NegotiationAction#REQUEST_FEASIBILITY_EVALUATION})
 * @param infeasibilityDetailsAndProposal infeasibility details and an alternative proposal; null or empty omits the
 *     section (only for {@link NegotiationAction#PROPOSE_ALTERNATIVE_ON_FAILURE})
 * @since 2026-06
 */
public record FeasibilityProposeContent(
        String feasibilityNegotiationDescription,
        NegotiationAction action,
        List<NegotiationItem> contentsToEvaluate,
        List<NegotiationItem> infeasibilityDetailsAndProposal)
        implements NegotiationProposeContent {}
